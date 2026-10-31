package com.selfservice.slotcache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param driveTtl       how long a drive's computed slots live. When they expire the next page open
 *                       is a cache miss and the booking service recomputes (picks up calendar changes).
 * @param computeLockTtl safety expiry of the compute lock, in case the booking service dies mid-compute
 * @param holdTtl        how long a candidate's hold keeps a host while the booking is being written
 * @param versionTtl     lifetime of the version counter, tombstones and host→drive index; must be
 *                       longer than driveTtl so invalidations always reach live drives
 * @param maxQueryRange  widest [from, to) window one read may ask for
 */
@ConfigurationProperties("slot-cache")
public record SlotCacheProperties(
        @DefaultValue("10m") Duration driveTtl,
        @DefaultValue("10s") Duration computeLockTtl,
        @DefaultValue("5m") Duration holdTtl,
        @DefaultValue("7d") Duration versionTtl,
        @DefaultValue("31d") Duration maxQueryRange) {
}
