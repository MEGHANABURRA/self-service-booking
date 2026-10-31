package com.selfservice.slotcache.service;

/** Error types surfaced by the cache, mapped to HTTP statuses in the API layer. */
public final class Exceptions {

    private Exceptions() {
    }

    /** No slots stored for this drive (never computed, or expired): the booking service should compute. */
    public static class DriveNotLoadedException extends RuntimeException {
        public DriveNotLoadedException(String driveId) {
            super("drive " + driveId + " has no slots stored; request a snapshot and publish them");
        }
    }

    /** Another booking-service request is computing this drive right now: wait briefly and read again. */
    public static class DriveLoadingException extends RuntimeException {
        public DriveLoadingException(String driveId) {
            super("drive " + driveId + " is being computed; retry shortly");
        }
    }

    /** A newer computation was already stored; this one was computed earlier and is discarded. */
    public static class StaleSnapshotException extends RuntimeException {
        public StaleSnapshotException(long baseVersion, long appliedVersion) {
            super("snapshot version " + baseVersion + " is older than stored version " + appliedVersion);
        }
    }

    /** No host is free in that slot any more ("This time was just taken"). */
    public static class SlotUnavailableException extends RuntimeException {
        public SlotUnavailableException(String message) {
            super(message);
        }
    }
}
