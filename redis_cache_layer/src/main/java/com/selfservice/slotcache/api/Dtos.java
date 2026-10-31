package com.selfservice.slotcache.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** Request/response bodies of the slot cache API. */
public final class Dtos {

    /** Ids end up inside Redis keys; keep them free of '|', '{' and '}'. */
    public static final String ID_PATTERN = "[A-Za-z0-9._:-]{1,128}";

    private Dtos() {
    }

    // ---- Part 1, step 1 -------------------------------------------------------------------------

    public record SnapshotRequest(@NotEmpty List<@Pattern(regexp = ID_PATTERN) String> hostIds) {
    }

    /** compute=true: you hold the lock, compute and publish with baseVersion. false: retry the read later. */
    public record SnapshotResponse(String driveId, boolean compute, Long baseVersion, Long retryAfterMillis) {
    }

    // ---- Part 1, step 2 -------------------------------------------------------------------------

    public record PublishSlotsRequest(
            @NotNull @Min(1) Long baseVersion,
            @NotNull @Min(1) @Max(24 * 60) Integer slotLengthMinutes,
            @NotNull List<@Valid SlotHosts> slots) {
    }

    /** One slot and the hosts free in it, e.g. 10:00 → [h1, h2, h3]. */
    public record SlotHosts(@NotNull Instant start, @NotNull List<@NotNull @Pattern(regexp = ID_PATTERN) String> hostIds) {
    }

    public record PublishSlotsResponse(String driveId, long appliedVersion, int loadedHostSlots) {
    }

    // ---- Candidate reads and holds --------------------------------------------------------------

    public record OpenSlotsResponse(String driveId, String timezone, int slotLengthMinutes, List<OpenSlot> slots) {
    }

    public record OpenSlot(Instant start, Instant end, OffsetDateTime localStart, OffsetDateTime localEnd) {
    }

    public record CreateHoldRequest(@NotNull @Pattern(regexp = ID_PATTERN) String holdId, @NotNull Instant start) {
    }

    public record HoldResponse(String holdId, String driveId, String hostId, Instant start, Instant end, Instant expiresAt) {
    }

    // ---- Part 2 ---------------------------------------------------------------------------------

    /** Omit from/to to invalidate everything the host has (e.g. host removed from the drive). */
    public record InvalidateRequest(Instant from, Instant to) {
    }

    public record InvalidateResponse(String hostId, int drivesTouched, long slotsRemoved, long holdsCancelled) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
