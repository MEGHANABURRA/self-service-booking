package com.selfservice.slotcache.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Results returned by {@link SlotCacheService}. */
public final class Models {

    private Models() {
    }

    /**
     * Answer to "may I compute?". If {@code compute} is true the caller holds the compute lock and
     * must publish with {@code baseVersion}; otherwise someone else is computing, retry the read
     * after {@code retryAfter}.
     */
    public record SnapshotTicket(boolean compute, long baseVersion, Duration retryAfter) {
    }

    public record PublishResult(long appliedVersion, int loadedHostSlots) {
    }

    public record OpenSlots(String driveId, Duration slotLength, List<Instant> starts) {
    }

    public record Hold(String holdId, String driveId, String hostId, Instant start, Instant end, Instant expiresAt) {
    }

    public record InvalidationResult(int drivesTouched, long slotsRemoved, long holdsCancelled) {
    }
}
