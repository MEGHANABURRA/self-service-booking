package com.selfservice.slotcache.service;

import java.util.List;

/**
 * Redis key layout. Every key of one drive carries the hash tag {driveId}, so they live on the same
 * Redis Cluster shard and one Lua script can update them atomically. See redis/_prelude.lua.
 *
 * <pre>
 * slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z   SET   free hostIds for that slot
 * slotcache:drive:{drive-1}:index                       ZSET  slots that still have a free host
 * slotcache:drive:{drive-1}:meta                        HASH  appliedVersion, slotLengthSec
 * slotcache:drive:{drive-1}:holds / :holdinfo           ZSET / HASH  active holds
 * slotcache:drive:{drive-1}:tombs                       ZSET  recent invalidations
 * slotcache:drive:{drive-1}:seq                         STRING version counter
 * slotcache:drive:{drive-1}:lock                        STRING compute lock
 * slotcache:host:{h1}:drives                            SET   drives host h1 belongs to
 * </pre>
 */
final class DriveKeys {

    private DriveKeys() {
    }

    /** In the exact order the Lua prelude expects. */
    static List<String> forDrive(String driveId) {
        String base = "slotcache:drive:{" + driveId + "}:";
        return List.of(base + "meta", base + "index", base + "holds", base + "holdinfo",
                base + "tombs", base + "seq", base + "lock");
    }

    static String hostDrives(String hostId) {
        return "slotcache:host:{" + hostId + "}:drives";
    }
}
