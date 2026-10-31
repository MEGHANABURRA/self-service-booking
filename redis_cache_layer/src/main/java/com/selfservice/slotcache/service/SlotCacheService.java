package com.selfservice.slotcache.service;

import com.selfservice.slotcache.config.SlotCacheProperties;
import com.selfservice.slotcache.service.Exceptions.DriveLoadingException;
import com.selfservice.slotcache.service.Exceptions.DriveNotLoadedException;
import com.selfservice.slotcache.service.Exceptions.SlotUnavailableException;
import com.selfservice.slotcache.service.Exceptions.StaleSnapshotException;
import com.selfservice.slotcache.service.Models.Hold;
import com.selfservice.slotcache.service.Models.InvalidationResult;
import com.selfservice.slotcache.service.Models.OpenSlots;
import com.selfservice.slotcache.service.Models.PublishResult;
import com.selfservice.slotcache.service.Models.SnapshotTicket;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Slot cache for one or more hiring drives. For every slot of a drive it stores the set of hosts
 * still free at that time; candidates pick a slot and get any one of those hosts.
 *
 * <p>The service is stateless. All state lives in Redis and every change is one Lua script on the
 * drive's shard, so any number of instances can run behind a load balancer.
 *
 * <h2>Booking-service flow</h2>
 * <ol>
 *   <li>Page opens → {@link #openSlots}. On {@link DriveNotLoadedException}:</li>
 *   <li>Part 1: {@link #beginSnapshot}. If it says compute, read DB + calendars, then {@link #publish}.
 *       If not, someone else is computing; wait {@code retryAfter} and read again.</li>
 *   <li>Candidate picks a slot → {@link #reserve} takes one free host (SPOP) and holds it.</li>
 *   <li>Booking service writes the booking to its DB (unique on host + start).</li>
 *   <li>Part 2: {@link #invalidateHost} for the booked host and time (consumes the hold, removes the
 *       host from that time in every drive). If the DB write failed, call {@link #release} instead.</li>
 * </ol>
 */
@Service
public class SlotCacheService {

    private final StringRedisTemplate redis;
    private final SlotCacheProperties properties;
    private final Clock clock;

    public SlotCacheService(StringRedisTemplate redis, SlotCacheProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Part 1, step 1: "may I compute this drive?". Only one caller at a time is told yes. The caller
     * must read its DB and calendars only AFTER this returns: every invalidation from then on gets a
     * higher version and is honoured at publish time even if the computed data missed it.
     */
    public SnapshotTicket beginSnapshot(String driveId, Collection<String> hostIds) {
        List<String> reply = run(LuaScripts.SNAPSHOT, driveId,
                String.valueOf(properties.computeLockTtl().toMillis()),
                String.valueOf(properties.versionTtl().toSeconds()));
        if ("BUSY".equals(reply.getFirst())) {
            long remaining = Math.max(0, Long.parseLong(reply.get(1)));
            return new SnapshotTicket(false, 0, Duration.ofMillis(Math.min(remaining, 200)));
        }
        // Registered before we answer, so invalidations from now on reach this drive.
        for (String hostId : hostIds) {
            indexHost(hostId, driveId);
        }
        return new SnapshotTicket(true, Long.parseLong(reply.get(1)), Duration.ZERO);
    }

    /** Part 1, step 2: store the computed slots — for each slot start, the hosts free at that time. */
    public PublishResult publish(String driveId, long baseVersion, Duration slotLength,
                                 Map<Instant, ? extends Collection<String>> hostsBySlot) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(clock.millis()));
        args.add(String.valueOf(baseVersion));
        args.add(String.valueOf(slotLength.toSeconds()));
        args.add(String.valueOf(properties.driveTtl().toSeconds()));
        Set<String> allHosts = new LinkedHashSet<>();
        hostsBySlot.forEach((start, hosts) -> {
            Set<String> distinct = new LinkedHashSet<>(hosts);
            args.add(String.valueOf(start.getEpochSecond()));
            args.add(iso(start));
            args.add(String.valueOf(distinct.size()));
            args.addAll(distinct);
            allHosts.addAll(distinct);
        });

        List<String> reply = run(LuaScripts.PUBLISH, driveId, args.toArray());
        if ("STALE".equals(reply.getFirst())) {
            throw new StaleSnapshotException(baseVersion, Long.parseLong(reply.get(1)));
        }
        // Normally done in beginSnapshot already; repeated so the index never misses a published host.
        for (String hostId : allHosts) {
            indexHost(hostId, driveId);
        }
        return new PublishResult(baseVersion, Integer.parseInt(reply.get(1)));
    }

    /** Slot starts in [from, to) that still have at least one free host. Past slots are never returned. */
    public OpenSlots openSlots(String driveId, Instant from, Instant to) {
        Instant now = clock.instant();
        Instant effectiveFrom = from.isAfter(now) ? from : now;
        // Round up so a slot that started a fraction of a second ago is not offered.
        long min = effectiveFrom.getEpochSecond() + (effectiveFrom.getNano() > 0 ? 1 : 0);
        List<String> reply = run(LuaScripts.READ, driveId,
                String.valueOf(clock.millis()), String.valueOf(min), String.valueOf(to.getEpochSecond()));
        switch (reply.getFirst()) {
            case "MISS" -> throw new DriveNotLoadedException(driveId);
            case "LOADING" -> throw new DriveLoadingException(driveId);
            default -> {
                Duration slotLength = Duration.ofSeconds(Long.parseLong(reply.get(1)));
                List<Instant> starts = reply.subList(2, reply.size()).stream().map(Instant::parse).toList();
                return new OpenSlots(driveId, slotLength, starts);
            }
        }
    }

    /**
     * Candidate picked a slot: atomically take one free host from it (Redis SPOP) and hold it for
     * {@code holdTtl}. With 3 hosts free at 10:00, the first 3 callers each get a different host
     * and the 4th gets {@link SlotUnavailableException}. Retrying with the same {@code holdId}
     * returns the original hold instead of taking another host.
     */
    public Hold reserve(String driveId, String holdId, Instant start) {
        if (!start.isAfter(clock.instant())) {
            throw new SlotUnavailableException("slot " + start + " has already started");
        }
        long now = clock.millis();
        long expiresAt = now + properties.holdTtl().toMillis();
        // Hold keys outlive the drive's slot data so a recompute right after expiry still sees them.
        long holdKeysTtl = properties.holdTtl().multipliedBy(2).toSeconds();
        List<String> reply = run(LuaScripts.RESERVE, driveId,
                String.valueOf(now), String.valueOf(start.getEpochSecond()), iso(start), holdId,
                String.valueOf(expiresAt), String.valueOf(holdKeysTtl));

        switch (reply.getFirst()) {
            case "MISS" -> throw new DriveNotLoadedException(driveId);
            case "TAKEN" -> throw new SlotUnavailableException("no host is free at " + start);
            case "HELD" -> {
                Instant heldStart = Instant.ofEpochSecond(Long.parseLong(reply.get(1)));
                Duration length = Duration.ofSeconds(Long.parseLong(reply.get(5)));
                Instant expiry = Instant.ofEpochMilli((long) Double.parseDouble(reply.get(4)));
                return new Hold(holdId, driveId, reply.get(3), heldStart, heldStart.plus(length), expiry);
            }
            default -> throw new IllegalStateException("unexpected reserve reply " + reply);
        }
    }

    /** Gives a held host back to its slot immediately. Idempotent; false if the hold no longer exists. */
    public boolean release(String driveId, String holdId) {
        String reply = redis.execute(LuaScripts.RELEASE, DriveKeys.forDrive(driveId),
                String.valueOf(clock.millis()), holdId);
        return "1".equals(reply);
    }

    /**
     * Part 2: the host is no longer free in [from, to) — booking confirmed, calendar event added, or
     * host left. Applied to every drive the host is in: atomically per drive, sequentially across
     * drives (they may sit on different shards). Each drive also records a tombstone, so a
     * computation already in flight cannot put the host back.
     */
    public InvalidationResult invalidateHost(String hostId, Instant from, Instant to) {
        Set<String> drives = redis.opsForSet().members(DriveKeys.hostDrives(hostId));
        if (drives == null || drives.isEmpty()) {
            return new InvalidationResult(0, 0, 0);
        }
        long removed = 0;
        long cancelled = 0;
        for (String driveId : drives) {
            List<String> reply = run(LuaScripts.INVALIDATE, driveId,
                    String.valueOf(clock.millis()), hostId,
                    String.valueOf(from.getEpochSecond()), String.valueOf(to.getEpochSecond()),
                    String.valueOf(properties.versionTtl().toSeconds()));
            removed += Long.parseLong(reply.get(0));
            cancelled += Long.parseLong(reply.get(1));
        }
        return new InvalidationResult(drives.size(), removed, cancelled);
    }

    /** Drops a drive's slots, holds and compute lock (e.g. the drive closed). */
    public void deleteDrive(String driveId) {
        redis.execute(LuaScripts.DELETE, DriveKeys.forDrive(driveId));
    }

    // ---------------------------------------------------------------------------------------------

    /** Slot keys use the ISO form, e.g. 2026-11-02T04:30:00Z (whole seconds). */
    private static String iso(Instant start) {
        return Instant.ofEpochSecond(start.getEpochSecond()).toString();
    }

    private void indexHost(String hostId, String driveId) {
        String key = DriveKeys.hostDrives(hostId);
        redis.opsForSet().add(key, driveId);
        redis.expire(key, properties.versionTtl());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<String> run(RedisScript<List> script, String driveId, Object... args) {
        List<String> reply = (List<String>) redis.execute(script, DriveKeys.forDrive(driveId), args);
        if (reply == null || reply.isEmpty()) {
            throw new IllegalStateException("empty reply from Redis script for drive " + driveId);
        }
        return reply;
    }
}
