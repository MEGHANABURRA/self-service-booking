# Slot Cache Service — API Reference

Developer reference for every endpoint of the Redis slot cache service: request, response and
errors. For the design and the concurrency guarantees, see [slot-cache-service.md](slot-cache-service.md).

| | |
|---|---|
| Base URL (local) | `http://localhost:8081/api/v1` |
| Caller | The **booking service**. Candidates never call this service directly. |
| Format | JSON, `Content-Type: application/json` |
| Auth | None yet (internal service) |

---

## Contents
1. [Concepts](#1-concepts)
2. [Conventions](#2-conventions)
3. [Endpoints](#3-endpoints)
   - [3.1 Get open slots](#31-get-open-slots) — `GET /drives/{driveId}/slots`
   - [3.2 Request a snapshot (compute lock)](#32-request-a-snapshot-compute-lock) — `POST /drives/{driveId}/snapshots`
   - [3.3 Publish computed slots](#33-publish-computed-slots) — `PUT /drives/{driveId}/slots`
   - [3.4 Hold a slot](#34-hold-a-slot) — `POST /drives/{driveId}/holds`
   - [3.5 Release a hold](#35-release-a-hold) — `DELETE /drives/{driveId}/holds/{holdId}`
   - [3.6 Invalidate a host](#36-invalidate-a-host) — `POST /hosts/{hostId}/invalidations`
   - [3.7 Delete a drive](#37-delete-a-drive) — `DELETE /drives/{driveId}`
4. [Error codes](#4-error-codes)
5. [Which API to call when](#5-which-api-to-call-when)
6. [End-to-end example](#6-end-to-end-example)

---

## 1. Concepts

| Term | Meaning | Example |
|---|---|---|
| **Drive** (`driveId`) | One group of hosts that candidates book from, with one slot length. Until master data exists, use the **interview type + duration**. | `technical-30min`, `hr-45min` |
| **Host** (`hostId`) | An interviewer. | `h1` |
| **Slot** | A start time. Its length comes from the drive. Candidates pick a slot, not a host. | `2027-01-11T04:30:00Z` (10:00 IST) |
| **Hold** (`holdId`) | A host reserved for one candidate for 5 minutes while the booking service saves the booking. | `user-a-attempt-1` |
| **Snapshot / version** | Permission to compute a drive's slots, plus a version number that stops stale data from undoing bookings. | `baseVersion: 7` |

What Redis stores per slot (for reference):
```
slotcache:drive:{technical-30min}:slot:2027-01-11T04:30:00Z   SET { h1, h2, h3 }
```

---

## 2. Conventions

**IDs.** `driveId`, `hostId` and `holdId` must match `[A-Za-z0-9._:-]{1,128}`, so no spaces, `|`, `{` or `}`.

**Times**
- Requests and responses use **ISO-8601 UTC instants**: `2027-01-11T04:30:00Z`.
- Slot times are whole seconds; fractions are dropped.
- `GET /slots` also returns `localStart` / `localEnd` in the viewer's timezone, e.g. `2027-01-11T10:00:00+05:30`.

**Errors.** Every error has the same body:
```json
{ "code": "SLOT_UNAVAILABLE", "message": "no host is free at 2027-01-11T04:30:00Z" }
```
Branch on `code` (stable). `message` is for logs and humans and may change.

**Retries.** Every endpoint is safe to retry:
- holds are idempotent per `holdId`;
- release, invalidate and delete are no-ops when repeated;
- a repeated publish with the same version is accepted.

**Expiry (defaults, in `application.yml`)**

| Data | Lives for | Property |
|---|---|---|
| A drive's slot data | 10 min after publish | `slot-cache.drive-ttl` |
| Compute lock | 10 s, or until publish | `slot-cache.compute-lock-ttl` |
| Hold | 5 min | `slot-cache.hold-ttl` |
| Version counter, invalidation records, host→drive index | 7 days | `slot-cache.version-ttl` |

---

## 3. Endpoints

### 3.1 Get open slots

Slots in a time range that still have at least one free host. Hosts are not exposed.

```
GET /api/v1/drives/{driveId}/slots?from={instant}&to={instant}&timezone={zone}
```

| Query param | Required | Default | Description |
|---|---|---|---|
| `from` | no | now | Range start (inclusive). Past slots are never returned. |
| `to` | no | `from` + 14 days | Range end (exclusive). Max range 31 days. |
| `timezone` | no | `UTC` | IANA zone for `localStart`/`localEnd`, e.g. `Asia/Kolkata`, `Europe/London` |

**Response `200 OK`**
```json
{
  "driveId": "technical-30min",
  "timezone": "Asia/Kolkata",
  "slotLengthMinutes": 30,
  "slots": [
    {
      "start": "2027-01-11T04:30:00Z",
      "end": "2027-01-11T05:00:00Z",
      "localStart": "2027-01-11T10:00:00+05:30",
      "localEnd": "2027-01-11T10:30:00+05:30"
    },
    {
      "start": "2027-01-11T05:15:00Z",
      "end": "2027-01-11T05:45:00Z",
      "localStart": "2027-01-11T10:45:00+05:30",
      "localEnd": "2027-01-11T11:15:00+05:30"
    }
  ]
}
```
`slots` is empty when the drive is loaded but fully booked. That's not an error.

**Errors**

| Status | `code` | Meaning | What to do |
|---|---|---|---|
| 404 | `DRIVE_NOT_LOADED` | No slot data stored (first visit, or expired) | Call [3.2](#32-request-a-snapshot-compute-lock) |
| 503 | `DRIVE_LOADING` | Another request is computing right now. Also sends header `Retry-After: 1` | Wait ~200 ms, call this again |
| 400 | `BAD_REQUEST` | Bad timezone or date format, `to` ≤ `from`, or range > 31 days | Fix the request |

```
curl -s "http://localhost:8081/api/v1/drives/technical-30min/slots?from=2027-01-11T00:00:00Z&to=2027-01-12T00:00:00Z&timezone=Asia/Kolkata"
```

---

### 3.2 Request a snapshot (compute lock)

Part 1, step 1. Ask "may I compute this drive's slots?". Only one caller at a time gets yes.

```
POST /api/v1/drives/{driveId}/snapshots
```

**Request body**
```json
{ "hostIds": ["h1", "h2", "h3"] }
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `hostIds` | string[] | yes | At least 1. Every host in the drive. |

**Response `201 Created`**: you hold the lock.
```json
{ "driveId": "technical-30min", "compute": true, "baseVersion": 7, "retryAfterMillis": null }
```
Then:
1. Read your DB and calendars. **This must happen after this response arrives.**
2. Call [3.3](#33-publish-computed-slots) with `baseVersion` within 10 seconds.

**Response `200 OK`**: someone else is computing.
```json
{ "driveId": "technical-30min", "compute": false, "baseVersion": null, "retryAfterMillis": 200 }
```
Wait `retryAfterMillis`, then call [3.1](#31-get-open-slots) again. Don't compute.

**Errors**

| Status | `code` | Meaning |
|---|---|---|
| 400 | `BAD_REQUEST` | Empty or missing `hostIds`, invalid ID, malformed JSON |

**Notes**
- If you don't publish within 10 s (crash or slow compute), the lock expires and another caller can compute.
- You can also call this when data already exists, to force a refresh (e.g. a host freed up time).

```
curl -s -X POST http://localhost:8081/api/v1/drives/technical-30min/snapshots \
  -H "Content-Type: application/json" -d '{"hostIds":["h1","h2","h3"]}'
```

---

### 3.3 Publish computed slots

Part 1, step 2. Store what the booking service computed: each slot with the hosts free in it.
This **replaces** the drive's previous slot data.

```
PUT /api/v1/drives/{driveId}/slots
```

**Request body**
```json
{
  "baseVersion": 7,
  "slotLengthMinutes": 30,
  "slots": [
    { "start": "2027-01-11T04:30:00Z", "hostIds": ["h1", "h2", "h3"] },
    { "start": "2027-01-11T05:15:00Z", "hostIds": ["h1"] }
  ]
}
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `baseVersion` | integer | yes | ≥ 1. The value from [3.2](#32-request-a-snapshot-compute-lock). |
| `slotLengthMinutes` | integer | yes | 1–1440. Same for every slot in the drive. |
| `slots` | object[] | yes | May be empty (no availability). |
| `slots[].start` | instant | yes | Slot start in UTC. Send future slots only; past ones are never shown. |
| `slots[].hostIds` | string[] | yes | Hosts free at that time. Duplicates are ignored. Repeated `start` values are merged. |

**Response `200 OK`**
```json
{ "driveId": "technical-30min", "appliedVersion": 7, "loadedHostSlots": 4 }
```
`loadedHostSlots` counts the host–slot pairs actually stored. It can be lower than what you sent, because the cache automatically skips:
- hosts **currently on hold** for that slot (a candidate is mid-booking);
- hosts **invalidated after** `baseVersion` (booked while you were computing).

**Errors**

| Status | `code` | Meaning | What to do |
|---|---|---|---|
| 409 | `STALE_SNAPSHOT` | Newer data (higher version) is already stored | Nothing. Call [3.1](#31-get-open-slots). |
| 400 | `BAD_REQUEST` | Validation failed, invalid ID, malformed JSON | Fix the request |

```
curl -s -X PUT http://localhost:8081/api/v1/drives/technical-30min/slots \
  -H "Content-Type: application/json" -d '{
    "baseVersion": 7, "slotLengthMinutes": 30,
    "slots": [
      {"start": "2027-01-11T04:30:00Z", "hostIds": ["h1","h2","h3"]},
      {"start": "2027-01-11T05:15:00Z", "hostIds": ["h1"]}
    ]}'
```

---

### 3.4 Hold a slot

Candidate picked a slot. This atomically takes **one random free host** from that slot and holds it for
5 minutes. With 3 hosts free, the first 3 calls succeed with different hosts and the 4th gets `409`.

```
POST /api/v1/drives/{driveId}/holds
```

**Request body**
```json
{ "holdId": "user-a-attempt-1", "start": "2027-01-11T04:30:00Z" }
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `holdId` | string | yes | **Idempotency key.** Generate one per booking attempt (e.g. a UUID) and reuse it on retries. |
| `start` | instant | yes | Slot start, as returned by [3.1](#31-get-open-slots). Must be in the future. |

**Response `201 Created`**
```json
{
  "holdId": "user-a-attempt-1",
  "driveId": "technical-30min",
  "hostId": "h2",
  "start": "2027-01-11T04:30:00Z",
  "end": "2027-01-11T05:00:00Z",
  "expiresAt": "2026-11-01T09:05:00.123Z"
}
```
Then:
1. Save the booking with `hostId` in your DB.
2. If it saved, call [3.6](#36-invalidate-a-host). If not, call [3.5](#35-release-a-hold).

**Retry behaviour.** Calling again with the same `holdId` returns the **same** hold (`201`, same `hostId`)
and never takes a second host. This is true even if `start` differs.

**Errors**

| Status | `code` | Meaning | What to do |
|---|---|---|---|
| 409 | `SLOT_UNAVAILABLE` | No host is free at that time, or the slot has already started | Show "This time was just taken" and call [3.1](#31-get-open-slots) for the remaining slots |
| 404 | `DRIVE_NOT_LOADED` | Drive data expired | Reload via [3.1](#31-get-open-slots) / [3.2](#32-request-a-snapshot-compute-lock) / [3.3](#33-publish-computed-slots), then retry |
| 400 | `BAD_REQUEST` | Invalid `holdId`, malformed JSON | Fix the request |

```
curl -s -X POST http://localhost:8081/api/v1/drives/technical-30min/holds \
  -H "Content-Type: application/json" -d '{"holdId":"user-a-attempt-1","start":"2027-01-11T04:30:00Z"}'
```

---

### 3.5 Release a hold

The booking failed to save, or the candidate backed out. The host goes back into the slot immediately.
If you never call this, the hold expires on its own after 5 minutes.

```
DELETE /api/v1/drives/{driveId}/holds/{holdId}
```

**Response `204 No Content`**: always, even if the hold doesn't exist or already expired (idempotent).

```
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE \
  http://localhost:8081/api/v1/drives/technical-30min/holds/user-a-attempt-1
```

---

### 3.6 Invalidate a host

Part 2. Tells the cache a host is **no longer free** in a time range. Applies to **every drive** the host
belongs to. Use it when:
- **a booking was saved**: pass the booked slot's `start` and `end`. This also finalizes the hold.
- **the host added a calendar event**: pass the meeting's start and end.
- **the host was removed**: send no body. This removes the host from all their slots.

```
POST /api/v1/hosts/{hostId}/invalidations
```

**Request body** (optional)
```json
{ "from": "2027-01-11T04:30:00Z", "to": "2027-01-11T05:00:00Z" }
```

| Field | Type | Required | Rules |
|---|---|---|---|
| `from` | instant | no | Default: beginning of time |
| `to` | instant | no | Default: end of time. Must be after `from`. |

Any slot that **overlaps** `[from, to)` is affected. A meeting 10:15–10:45 removes both the 10:00 and the
10:30 slots of a 30-minute drive. A slot that ends exactly at `from` is not affected.

**Response `200 OK`**
```json
{ "hostId": "h2", "drivesTouched": 1, "slotsRemoved": 0, "holdsCancelled": 1 }
```

| Field | Meaning |
|---|---|
| `drivesTouched` | Drives this host belongs to (all were updated) |
| `slotsRemoved` | Free slots the host was removed from |
| `holdsCancelled` | Holds on this host in the range that were finalized/cancelled |

Typical result after a booking: `slotsRemoved: 0, holdsCancelled: 1`. The host was already out of the
free set because of the hold.

**Errors**

| Status | `code` | Meaning |
|---|---|---|
| 400 | `BAD_REQUEST` | `to` ≤ `from`, invalid `hostId`, malformed JSON |

```
curl -s -X POST http://localhost:8081/api/v1/hosts/h2/invalidations \
  -H "Content-Type: application/json" -d '{"from":"2027-01-11T04:30:00Z","to":"2027-01-11T05:00:00Z"}'
```

The cache never **adds** hosts on its own. When a host gains free time (meeting deleted, booking
cancelled), call [3.2](#32-request-a-snapshot-compute-lock) + [3.3](#33-publish-computed-slots) to refresh, or wait
for the 10-minute expiry.

---

### 3.7 Delete a drive

Removes a drive's slot data, holds and compute lock, e.g. when interviews for that type are closed, or
to force the next page open to recompute.

```
DELETE /api/v1/drives/{driveId}
```

**Response `204 No Content`**: always (idempotent).

```
curl -s -o /dev/null -w "%{http_code}\n" -X DELETE http://localhost:8081/api/v1/drives/technical-30min
```

---

## 4. Error codes

| HTTP | `code` | Returned by | Meaning | Booking service action |
|---|---|---|---|---|
| 400 | `BAD_REQUEST` | any | Invalid input | Fix the request; don't retry as-is |
| 404 | `DRIVE_NOT_LOADED` | 3.1, 3.4 | No slot data stored for the drive | Call 3.2, then 3.3 |
| 409 | `SLOT_UNAVAILABLE` | 3.4 | No free host at that time | Show "just taken"; refresh slots via 3.1 |
| 409 | `STALE_SNAPSHOT` | 3.3 | A newer version is already stored | Ignore; read via 3.1 |
| 503 | `DRIVE_LOADING` | 3.1 | Someone else is computing | Wait ~200 ms; retry 3.1 |
| 500 | (Spring default body) | any | Unexpected failure, e.g. Redis is down | Retry with backoff; alert |

---

## 5. Which API to call when

| # | Event in the booking service | API |
|---|---|---|
| 1 | Candidate opens booking page | 3.1 `GET /slots` |
| 2 | 3.1 returned `404` | 3.2 `POST /snapshots` |
| 3 | 3.2 returned `compute: true` | read DB + calendars, then 3.3 `PUT /slots` |
| 4 | Candidate clicks a slot | 3.4 `POST /holds` |
| 5 | Booking saved in DB | 3.6 `POST /hosts/{hostId}/invalidations` with the slot's `start`/`end` |
| 6 | Booking save failed / candidate backed out | 3.5 `DELETE /holds/{holdId}` |
| 7 | Host added a calendar event | 3.6 with the event's time |
| 8 | Host gained free time | 3.2 + 3.3 (refresh), or wait for expiry |
| 9 | Host removed | 3.6 with no body |
| 10 | Interview type closed | 3.7 `DELETE /drives/{driveId}` |

**Candidate cancels a booking:** cancel in your DB, then do #8.
**Candidate reschedules:** #4 for the new time → save → #5 for the new time → #8 to bring back the old time.

Booking-service pseudocode for the page-open path:
```java
List<Slot> slotsForPage(String driveId) {
    for (int attempt = 0; attempt < 15; attempt++) {                // ~3 s budget
        var r = cache.getSlots(driveId);                             // 3.1
        if (r.status() == 200) return r.slots();
        if (r.status() == 503) { sleep(200); continue; }             // DRIVE_LOADING
        if (r.status() == 404) {                                     // DRIVE_NOT_LOADED
            var ticket = cache.snapshot(driveId, hostIdsFor(driveId)); // 3.2
            if (ticket.compute()) {
                var computed = computeFromDbAndCalendars(driveId);   // AFTER 3.2 returns
                cache.publish(driveId, ticket.baseVersion(), slotMinutes(driveId), computed); // 3.3 (409 = fine)
            } else {
                sleep(ticket.retryAfterMillis());
            }
        }
    }
    throw new ServiceUnavailableException("slots not ready, try again");
}
```

---

## 6. End-to-end example

Interview type `technical-30min` with hosts h1, h2, h3, on Monday 11 Jan 2027 (India).

| Step | Call | Response |
|---|---|---|
| Page opens | `GET /drives/technical-30min/slots` | `404 DRIVE_NOT_LOADED` |
| Ask to compute | `POST /drives/technical-30min/snapshots` `{"hostIds":["h1","h2","h3"]}` | `201 {"compute":true,"baseVersion":1}` |
| Publish | `PUT /drives/technical-30min/slots` 10:00→[h1,h2,h3], 10:45→[h1] | `200 {"loadedHostSlots":4}` |
| Show page | `GET /drives/technical-30min/slots?timezone=Asia/Kolkata` | `200` slots 10:00, 10:45 |
| User A clicks 10:00 | `POST /holds` `{"holdId":"a1","start":"…04:30:00Z"}` | `201 {"hostId":"h2"}` |
| User B clicks 10:00 | `POST /holds` `{"holdId":"b1", …}` | `201 {"hostId":"h1"}` |
| User C clicks 10:00 | `POST /holds` `{"holdId":"c1", …}` | `201 {"hostId":"h3"}` |
| User D clicks 10:00 | `POST /holds` `{"holdId":"d1", …}` | `409 SLOT_UNAVAILABLE` |
| A's booking saved | `POST /hosts/h2/invalidations` `{"from":"…04:30:00Z","to":"…05:00:00Z"}` | `200 {"holdsCancelled":1}` |
| B's booking failed | `DELETE /drives/technical-30min/holds/b1` | `204`, h1 back at 10:00 |
| Page refresh | `GET /drives/technical-30min/slots` | `200` slots 10:00 (h1 free again), 10:45 |

For a hands-on version of this table with copy-paste commands, see the "Running" section in
[slot-cache-service.md](slot-cache-service.md).
