package com.selfservice.slotcache.api;

import com.selfservice.slotcache.api.Dtos.CreateHoldRequest;
import com.selfservice.slotcache.api.Dtos.HoldResponse;
import com.selfservice.slotcache.api.Dtos.OpenSlot;
import com.selfservice.slotcache.api.Dtos.OpenSlotsResponse;
import com.selfservice.slotcache.api.Dtos.PublishSlotsRequest;
import com.selfservice.slotcache.api.Dtos.PublishSlotsResponse;
import com.selfservice.slotcache.api.Dtos.SlotHosts;
import com.selfservice.slotcache.api.Dtos.SnapshotRequest;
import com.selfservice.slotcache.api.Dtos.SnapshotResponse;
import com.selfservice.slotcache.config.SlotCacheProperties;
import com.selfservice.slotcache.service.Models.Hold;
import com.selfservice.slotcache.service.Models.OpenSlots;
import com.selfservice.slotcache.service.Models.PublishResult;
import com.selfservice.slotcache.service.Models.SnapshotTicket;
import com.selfservice.slotcache.service.SlotCacheService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.selfservice.slotcache.api.Dtos.ID_PATTERN;

@RestController
@Validated
@RequestMapping("/api/v1/drives/{driveId}")
public class DriveController {

    private static final Duration DEFAULT_RANGE = Duration.ofDays(14);

    private final SlotCacheService service;
    private final SlotCacheProperties properties;
    private final Clock clock;

    public DriveController(SlotCacheService service, SlotCacheProperties properties, Clock clock) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Part 1, step 1 (booking service, after a cache miss): "may I compute?".
     * 201 + compute=true → you hold the lock; 200 + compute=false → someone else is computing.
     */
    @PostMapping("/snapshots")
    public ResponseEntity<SnapshotResponse> snapshot(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId,
                                                     @Valid @RequestBody SnapshotRequest request) {
        SnapshotTicket ticket = service.beginSnapshot(driveId, request.hostIds());
        if (ticket.compute()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SnapshotResponse(driveId, true, ticket.baseVersion(), null));
        }
        return ResponseEntity.ok(new SnapshotResponse(driveId, false, null, ticket.retryAfter().toMillis()));
    }

    /** Part 1, step 2: store the computed slots — each slot with the hosts free in it. */
    @PutMapping("/slots")
    public PublishSlotsResponse publish(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId,
                                        @Valid @RequestBody PublishSlotsRequest request) {
        Map<Instant, List<String>> hostsBySlot = new LinkedHashMap<>();
        for (SlotHosts slot : request.slots()) {
            hostsBySlot.computeIfAbsent(slot.start(), s -> new ArrayList<>()).addAll(slot.hostIds());
        }
        PublishResult result = service.publish(driveId, request.baseVersion(),
                Duration.ofMinutes(request.slotLengthMinutes()), hostsBySlot);
        return new PublishSlotsResponse(driveId, result.appliedVersion(), result.loadedHostSlots());
    }

    /** Open slots (hosts are not exposed), rendered in the viewer's timezone. */
    @GetMapping("/slots")
    public OpenSlotsResponse openSlots(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(defaultValue = "UTC") String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        Instant start = from != null ? from : clock.instant();
        Instant end = to != null ? to : start.plus(DEFAULT_RANGE);
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(properties.maxQueryRange()) > 0) {
            throw new IllegalArgumentException("'to' must be after 'from' and within " + properties.maxQueryRange());
        }

        OpenSlots open = service.openSlots(driveId, start, end);
        List<OpenSlot> slots = open.starts().stream()
                .map(s -> new OpenSlot(s, s.plus(open.slotLength()),
                        s.atZone(zone).toOffsetDateTime(), s.plus(open.slotLength()).atZone(zone).toOffsetDateTime()))
                .toList();
        return new OpenSlotsResponse(driveId, zone.getId(), (int) open.slotLength().toMinutes(), slots);
    }

    /** Candidate picked a slot: take any one free host in it and hold it until the booking is written. */
    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId,
                             @Valid @RequestBody CreateHoldRequest request) {
        Hold hold = service.reserve(driveId, request.holdId(), request.start());
        return new HoldResponse(hold.holdId(), hold.driveId(), hold.hostId(), hold.start(), hold.end(), hold.expiresAt());
    }

    /** Booking abandoned or its DB write failed: give the host back now instead of waiting for expiry. */
    @DeleteMapping("/holds/{holdId}")
    public ResponseEntity<Void> release(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId,
                                        @PathVariable @Pattern(regexp = ID_PATTERN) String holdId) {
        service.release(driveId, holdId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteDrive(@PathVariable @Pattern(regexp = ID_PATTERN) String driveId) {
        service.deleteDrive(driveId);
        return ResponseEntity.noContent().build();
    }
}
