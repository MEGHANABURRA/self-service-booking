package com.selfservice.slotcache.api;

import com.selfservice.slotcache.api.Dtos.InvalidateRequest;
import com.selfservice.slotcache.api.Dtos.InvalidateResponse;
import com.selfservice.slotcache.service.Models.InvalidationResult;
import com.selfservice.slotcache.service.SlotCacheService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static com.selfservice.slotcache.api.Dtos.ID_PATTERN;

@RestController
@Validated
@RequestMapping("/api/v1/hosts/{hostId}")
public class HostController {

    private static final Instant MAX = Instant.ofEpochSecond(253402300799L); // 9999-12-31T23:59:59Z

    private final SlotCacheService service;

    public HostController(SlotCacheService service) {
        this.service = service;
    }

    /**
     * Booking service part 2: the host is no longer free in [from, to) — booking confirmed, calendar
     * event added, or host removed. Applies to every drive the host is in. Safe to retry.
     */
    @PostMapping("/invalidations")
    public InvalidateResponse invalidate(@PathVariable @Pattern(regexp = ID_PATTERN) String hostId,
                                         @RequestBody(required = false) InvalidateRequest request) {
        Instant from = request != null && request.from() != null ? request.from() : Instant.EPOCH;
        Instant to = request != null && request.to() != null ? request.to() : MAX;
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("'to' must be after 'from'");
        }
        InvalidationResult result = service.invalidateHost(hostId, from, to);
        return new InvalidateResponse(hostId, result.drivesTouched(), result.slotsRemoved(), result.holdsCancelled());
    }
}
