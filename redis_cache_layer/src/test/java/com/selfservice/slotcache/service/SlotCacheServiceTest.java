package com.selfservice.slotcache.service;

import com.selfservice.slotcache.EmbeddedRedis;
import com.selfservice.slotcache.MutableClock;
import com.selfservice.slotcache.config.SlotCacheProperties;
import com.selfservice.slotcache.service.Exceptions.DriveLoadingException;
import com.selfservice.slotcache.service.Exceptions.DriveNotLoadedException;
import com.selfservice.slotcache.service.Exceptions.SlotUnavailableException;
import com.selfservice.slotcache.service.Exceptions.StaleSnapshotException;
import com.selfservice.slotcache.service.Models.Hold;
import com.selfservice.slotcache.service.Models.SnapshotTicket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs every scenario against a real redis-server so the Lua scripts are exercised for real. */
class SlotCacheServiceTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final Duration SLOT = Duration.ofMinutes(30);
    private static final Instant DAY_START = MONDAY.atStartOfDay(INDIA).toInstant();
    private static final Instant DAY_END = MONDAY.plusDays(1).atStartOfDay(INDIA).toInstant();
    private static final SlotCacheProperties PROPS = new SlotCacheProperties(
            Duration.ofMinutes(10), Duration.ofMillis(300), Duration.ofMinutes(5), Duration.ofDays(7), Duration.ofDays(31));

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    private MutableClock clock;
    private SlotCacheService cache;

    @BeforeAll
    static void connect() {
        EmbeddedRedis.start();
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", EmbeddedRedis.PORT));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        clock = new MutableClock(Instant.parse("2026-11-01T00:00:00Z"));
        cache = new SlotCacheService(redis, PROPS, clock);
    }

    // ---- the core requirement --------------------------------------------------------------------

    @Test
    void threeHostsAtTenAmMeansThreeUsersBookAndTheFourthIsDenied() {
        publish("drive-1", slots(at(10, 0), "h1", "h2", "h3"));

        Hold a = cache.reserve("drive-1", "user-a", at(10, 0));
        Hold b = cache.reserve("drive-1", "user-b", at(10, 0));
        Hold c = cache.reserve("drive-1", "user-c", at(10, 0));

        assertThat(List.of(a.hostId(), b.hostId(), c.hostId())).containsExactlyInAnyOrder("h1", "h2", "h3");
        assertThatThrownBy(() -> cache.reserve("drive-1", "user-d", at(10, 0)))
                .isInstanceOf(SlotUnavailableException.class);
        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).isEmpty();
    }

    @Test
    void sameScenarioUnderRealConcurrency() throws Exception {
        publish("drive-1", slots(at(10, 0), "h1", "h2", "h3"));

        List<Object> outcomes = race(40, () -> cache.reserve("drive-1", UUID.randomUUID().toString(), at(10, 0)));

        List<Hold> holds = outcomes.stream().filter(Hold.class::isInstance).map(Hold.class::cast).toList();
        assertThat(holds).extracting(Hold::hostId).containsExactlyInAnyOrder("h1", "h2", "h3");
        assertThat(outcomes).filteredOn(SlotUnavailableException.class::isInstance).hasSize(37);
    }

    @Test
    void redisHoldsOneReadableSetOfFreeHostsPerSlot() {
        Map<Instant, List<String>> data = new LinkedHashMap<>();
        data.put(at(10, 0), List.of("h1", "h2", "h3"));
        data.put(at(10, 45), List.of("h1"));
        publish("drive-1", data);
        cache.reserve("drive-1", "user-a", at(10, 0));

        String tenAm = "slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z";
        String tenFortyFive = "slotcache:drive:{drive-1}:slot:2026-11-02T05:15:00Z";
        assertThat(redis.opsForSet().members(tenAm)).hasSize(2);
        assertThat(redis.opsForSet().members(tenFortyFive)).containsExactly("h1");
        assertThat(redis.opsForZSet().range("slotcache:drive:{drive-1}:index", 0, -1))
                .containsExactly("2026-11-02T04:30:00Z", "2026-11-02T05:15:00Z");
    }

    @Test
    void candidateSeesEachSlotOnceAndOnlyWhileAHostIsFree() {
        Map<Instant, List<String>> data = new LinkedHashMap<>();
        data.put(at(10, 0), List.of("h1"));
        data.put(at(10, 45), List.of("h1", "h2"));
        publish("drive-1", data);

        cache.reserve("drive-1", "user-a", at(10, 0));

        var open = cache.openSlots("drive-1", DAY_START, DAY_END);
        assertThat(open.starts()).containsExactly(at(10, 45));
        assertThat(open.slotLength()).isEqualTo(SLOT);
    }

    // ---- part 1: compute lock and publishing -----------------------------------------------------

    @Test
    void manyPageOpensOnACacheMissProduceExactlyOneComputation() throws Exception {
        List<Object> tickets = race(50, () -> cache.beginSnapshot("drive-1", List.of("h1", "h2", "h3")));

        assertThat(tickets).filteredOn(t -> ((SnapshotTicket) t).compute()).hasSize(1);
        assertThatThrownBy(() -> cache.openSlots("drive-1", DAY_START, DAY_END))
                .isInstanceOf(DriveLoadingException.class);
    }

    @Test
    void publishReleasesTheComputeLock() {
        publish("drive-1", slots(at(10, 0), "h1"));

        assertThat(cache.beginSnapshot("drive-1", List.of("h1")).compute()).isTrue();
    }

    @Test
    void computeLockExpiresIfTheBookingServiceDiesMidCompute() throws Exception {
        assertThat(cache.beginSnapshot("drive-1", List.of("h1")).compute()).isTrue();
        assertThat(cache.beginSnapshot("drive-1", List.of("h1")).compute()).isFalse();

        Thread.sleep(PROPS.computeLockTtl().toMillis() + 100);

        assertThat(cache.beginSnapshot("drive-1", List.of("h1")).compute()).isTrue();
    }

    @Test
    void dataComputedBeforeABookingCommittedCannotBringTheHostBack() {
        // Part 1 gets its version, then reads the DB (h2's booking is not committed yet).
        long version = cache.beginSnapshot("drive-1", List.of("h1", "h2", "h3")).baseVersion();
        Map<Instant, List<String>> computed = slots(at(10, 0), "h1", "h2", "h3");

        // Meanwhile h2's 10:00 booking commits and part 2 invalidates it.
        cache.invalidateHost("h2", at(10, 0), at(10, 30));

        cache.publish("drive-1", version, SLOT, computed);

        assertThat(redis.opsForSet().members("slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z"))
                .containsExactlyInAnyOrder("h1", "h3");
    }

    @Test
    void olderComputationArrivingLateIsRejected() throws Exception {
        long older = cache.beginSnapshot("drive-1", List.of("h1")).baseVersion();
        Thread.sleep(PROPS.computeLockTtl().toMillis() + 100); // first computer is slow; lock lapses
        long newer = cache.beginSnapshot("drive-1", List.of("h1")).baseVersion();
        cache.publish("drive-1", newer, SLOT, slots(at(10, 0), "h1"));

        assertThatThrownBy(() -> cache.publish("drive-1", older, SLOT, slots(at(10, 45), "h1")))
                .isInstanceOf(StaleSnapshotException.class);
        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).containsExactly(at(10, 0));
    }

    @Test
    void recomputeDoesNotHandOutAHeldHostAgain() {
        publish("drive-1", slots(at(10, 0), "h1"));
        cache.reserve("drive-1", "user-a", at(10, 0));

        // Drive refresh still sees h1 free at 10:00 (the booking is not written yet).
        publish("drive-1", slots(at(10, 0), "h1"));

        assertThatThrownBy(() -> cache.reserve("drive-1", "user-b", at(10, 0)))
                .isInstanceOf(SlotUnavailableException.class);
    }

    // ---- holds -----------------------------------------------------------------------------------

    @Test
    void retriedReserveWithSameHoldIdDoesNotTakeASecondHost() {
        publish("drive-1", slots(at(10, 0), "h1", "h2"));

        Hold first = cache.reserve("drive-1", "user-a", at(10, 0));
        Hold retry = cache.reserve("drive-1", "user-a", at(10, 0));

        assertThat(retry).isEqualTo(first);
        assertThat(redis.opsForSet().size("slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z")).isEqualTo(1);
    }

    @Test
    void expiredHoldPutsTheHostBackInTheSlot() {
        publish("drive-1", slots(at(10, 0), "h1"));
        cache.reserve("drive-1", "user-a", at(10, 0));

        clock.advance(PROPS.holdTtl().plusSeconds(1));

        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).containsExactly(at(10, 0));
    }

    @Test
    void releasePutsTheHostBackImmediatelyAndIsIdempotent() {
        publish("drive-1", slots(at(10, 0), "h1"));
        cache.reserve("drive-1", "user-a", at(10, 0));

        assertThat(cache.release("drive-1", "user-a")).isTrue();
        assertThat(cache.release("drive-1", "user-a")).isFalse();

        assertThat(cache.reserve("drive-1", "user-b", at(10, 0)).hostId()).isEqualTo("h1");
    }

    @Test
    void expiredHoldDoesNotRestoreAHostTheLatestDataDropped() {
        publish("drive-1", Map.of(at(10, 0), List.of("h1"), at(10, 45), List.of("h1")));
        cache.reserve("drive-1", "user-a", at(10, 0));

        // h1 changed availability; the recomputed data no longer has h1 at 10:00.
        publish("drive-1", slots(at(10, 45), "h1"));
        clock.advance(PROPS.holdTtl().plusSeconds(1));

        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).containsExactly(at(10, 45));
    }

    // ---- part 2: invalidation --------------------------------------------------------------------

    @Test
    void bookingInvalidationConsumesTheHoldSoTheHostNeverComesBack() {
        publish("drive-1", slots(at(10, 0), "h1"));
        Hold hold = cache.reserve("drive-1", "user-a", at(10, 0));

        var result = cache.invalidateHost(hold.hostId(), hold.start(), hold.end());
        clock.advance(PROPS.holdTtl().plusSeconds(1));

        assertThat(result.holdsCancelled()).isEqualTo(1);
        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).isEmpty();
    }

    @Test
    void invalidationReachesEveryDriveTheHostBelongsTo() {
        publish("drive-1", slots(at(10, 0), "h1", "h2"));
        publish("drive-2", Map.of(at(10, 0), List.of("h1"), at(11, 30), List.of("h1")));

        var result = cache.invalidateHost("h1", at(10, 0), at(10, 30));

        assertThat(result.drivesTouched()).isEqualTo(2);
        assertThat(result.slotsRemoved()).isEqualTo(2);
        assertThat(cache.reserve("drive-1", "u", at(10, 0)).hostId()).isEqualTo("h2");
        assertThat(cache.openSlots("drive-2", DAY_START, DAY_END).starts()).containsExactly(at(11, 30));
    }

    @Test
    void calendarMeetingRemovesOnlyOverlappingSlots() {
        Map<Instant, List<String>> data = new LinkedHashMap<>();
        for (Instant t : List.of(at(10, 0), at(10, 45), at(11, 30), at(12, 15))) {
            data.put(t, List.of("h1"));
        }
        publish("drive-1", data);

        // Meeting 11:15–11:45 overlaps the 11:30 slot only; 10:45–11:15 ends exactly when it starts.
        cache.invalidateHost("h1", at(11, 15), at(11, 45));

        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts())
                .containsExactly(at(10, 0), at(10, 45), at(12, 15));
    }

    // ---- misc ------------------------------------------------------------------------------------

    @Test
    void pastSlotsAreNeitherShownNorReservable() {
        publish("drive-1", Map.of(at(10, 0), List.of("h1"), at(10, 45), List.of("h1")));
        cache = new SlotCacheService(redis, PROPS, new MutableClock(at(10, 30)));

        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).containsExactly(at(10, 45));
        assertThatThrownBy(() -> cache.reserve("drive-1", "u", at(10, 0))).isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    void unknownDriveIsReportedAsNotLoaded() {
        assertThatThrownBy(() -> cache.openSlots("nope", DAY_START, DAY_END)).isInstanceOf(DriveNotLoadedException.class);
        assertThatThrownBy(() -> cache.reserve("nope", "u", at(10, 0))).isInstanceOf(DriveNotLoadedException.class);
    }

    @Test
    void fullyBookedDriveStaysLoaded() {
        publish("drive-1", slots(at(10, 0), "h1"));
        Hold hold = cache.reserve("drive-1", "user-a", at(10, 0));
        cache.invalidateHost("h1", hold.start(), hold.end());

        assertThat(cache.openSlots("drive-1", DAY_START, DAY_END).starts()).isEmpty();
    }

    @Test
    void deleteDriveRemovesItsSlotKeys() {
        publish("drive-1", slots(at(10, 0), "h1"));

        cache.deleteDrive("drive-1");

        assertThat(redis.hasKey("slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z")).isFalse();
        assertThatThrownBy(() -> cache.openSlots("drive-1", DAY_START, DAY_END)).isInstanceOf(DriveNotLoadedException.class);
    }

    // ---------------------------------------------------------------------------------------------

    private void publish(String driveId, Map<Instant, List<String>> hostsBySlot) {
        List<String> hosts = hostsBySlot.values().stream().flatMap(List::stream).distinct().toList();
        SnapshotTicket ticket = cache.beginSnapshot(driveId, hosts);
        assertThat(ticket.compute()).isTrue();
        cache.publish(driveId, ticket.baseVersion(), SLOT, hostsBySlot);
    }

    private static Map<Instant, List<String>> slots(Instant start, String... hosts) {
        return Map.of(start, List.of(hosts));
    }

    /** Starts all callers at the same instant; returns each result or the exception it threw. */
    private static List<Object> race(int callers, Callable<Object> call) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Object> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return call.call();
                    } catch (RuntimeException e) {
                        return e;
                    }
                }));
            }
            go.countDown();
            for (Future<Object> f : futures) {
                outcomes.add(f.get());
            }
        }
        return outcomes;
    }

    private static Instant at(int hour, int minute) {
        return MONDAY.atTime(hour, minute).atZone(INDIA).toInstant();
    }
}
