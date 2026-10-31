# Slot Cache Service

A standalone service that stores, in Redis, **for every slot of a hiring drive, which hosts are
still free**. The booking service computes the slots and sends them here. Candidates pick a *slot*
(not a host), and the cache hands out one free host per booking atomically. With 3 hosts free at
10:00, 3 candidates get one each and the 4th is refused.

Base URL `/api/v1`, default port `8081`. Full request/response details for every endpoint are in
[api-reference.md](api-reference.md). Every endpoint is called **by the booking service**; the
cache service never calls anyone.

## Flow

```
Candidate        Booking service                         Slot Cache Service              Redis
   │ open page         │                                        │                          │
   │──────────────────►│ GET /drives/drive-1/slots               │                          │
   │                   │───────────────────────────────────────►│ ─ drive cached? ────────►│
   │                   │◄────────────── 404 DRIVE_NOT_LOADED ────│                          │
   │                   │                                        │                          │
   │                   │ ① POST /drives/drive-1/snapshots         │                          │
   │                   │───────────────────────────────────────►│ ─ lock + version ───────►│
   │                   │◄──────── 201 {compute:true, v:7} ───────│                          │
   │                   │   (everyone else: 200 {compute:false}   │                          │
   │                   │    → wait retryAfterMillis, GET again)  │                          │
   │                   │                                        │                          │
   │                   │ read own DB + host calendars,          │                          │
   │                   │ compute free hosts per slot            │                          │
   │                   │                                        │                          │
   │                   │ ② PUT /drives/drive-1/slots              │                          │
   │                   │───────────────────────────────────────►│ ─ one atomic script ────►│
   │                   │◄──────────────────── 200 ───────────────│                          │
   │◄── 10:00, 10:45 ──│ GET /drives/drive-1/slots ─────────────►│                          │
   │                   │                                        │                          │
   │ picks 10:00 ─────►│ POST /drives/drive-1/holds              │ ─ SPOP slot set ────────►│
   │◄── confirmed ─────│◄────── 201 {hostId:"h2"} / 409 ─────────│                          │
   │                   │ write booking to DB (UNIQUE host+start)│                          │
   │                   │ ③ POST /hosts/h2/invalidations ─────────►│ ─ remove h2 @ 10:00 ────►│
   │                   │   (if DB write failed: DELETE hold)    │                          │
```

* **Part 1** (① + ②) runs only on a cache miss: the first page open, or after the drive's data
  expires (`drive-ttl`, 10 min). It never runs on every page open, because 2,000 candidates would mean
  2,000 recomputes and roughly 100k calendar API calls.
* **Part 2** (③) runs after a booking is written, or when a host's calendar gains an event.

## API

| Method & path | Body | Success | Errors |
|---|---|---|---|
| `GET /drives/{driveId}/slots?from&to&timezone` | – | `200 {"slots":[{"start","end","localStart","localEnd"}]}` | 404 `DRIVE_NOT_LOADED` → do ①. 503 `DRIVE_LOADING` → wait and GET again |
| ① `POST /drives/{driveId}/snapshots` | `{"hostIds":["h1","h2","h3"]}` | `201 {"compute":true,"baseVersion":7}` or `200 {"compute":false,"retryAfterMillis":200}` | 400 |
| ② `PUT /drives/{driveId}/slots` | see below | `200 {"appliedVersion":7,"loadedHostSlots":4}` | 409 `STALE_SNAPSHOT` (just GET) |
| `POST /drives/{driveId}/holds` | `{"holdId":"<uuid>","start":"2026-11-02T04:30:00Z"}` | `201 {"hostId","start","end","expiresAt"}` | 409 `SLOT_UNAVAILABLE`, 404 |
| `DELETE /drives/{driveId}/holds/{holdId}` | – | `204` (idempotent) | – |
| ③ `POST /hosts/{hostId}/invalidations` | `{"from":"…Z","to":"…Z"}`, or empty for all | `200 {"drivesTouched","slotsRemoved","holdsCancelled"}` | 400 |
| `DELETE /drives/{driveId}` | – | `204` | – |

② body: each slot and the hosts free in it.
```json
{
  "baseVersion": 7,
  "slotLengthMinutes": 30,
  "slots": [
    { "start": "2026-11-02T04:30:00Z", "hostIds": ["h1", "h2", "h3"] },
    { "start": "2026-11-02T05:15:00Z", "hostIds": ["h1"] }
  ]
}
```
All times are UTC instants. `GET` adds `localStart`/`localEnd` in the requested IANA timezone. Ids
must match `[A-Za-z0-9._:-]{1,128}`. `holdId` is an idempotency key: generate one per booking attempt
and reuse it on retries.

## What is stored in Redis (example after user A took h2 at 10:00)

```
slotcache:drive:{drive-1}:slot:2026-11-02T04:30:00Z   SET     { h1, h3 }        ← 10:00 IST
slotcache:drive:{drive-1}:slot:2026-11-02T05:15:00Z   SET     { h1 }            ← 10:45 IST
slotcache:drive:{drive-1}:index                       ZSET    2026-11-02T04:30:00Z (score 1793593800)
                                                              2026-11-02T05:15:00Z (score 1793596500)
slotcache:drive:{drive-1}:meta                        HASH    appliedVersion=7, slotLengthSec=1800
slotcache:drive:{drive-1}:holds                       ZSET    user-a → 1793593500000 (expires at, ms)
slotcache:drive:{drive-1}:holdinfo                    HASH    user-a → "1793593800|2026-11-02T04:30:00Z|h2|1"
slotcache:drive:{drive-1}:tombs                       ZSET    recent invalidations "seq|host|from|to"
slotcache:drive:{drive-1}:seq                         STRING  7
slotcache:drive:{drive-1}:lock                        STRING  compute lock (only while computing)
slotcache:host:{h2}:drives                            SET     { drive-1 }
```

| Key | Expires |
|---|---|
| slot sets, index, meta | together, `drive-ttl` (10 min) after publish; then the next page open recomputes |
| holds, holdinfo | 2 × `hold-ttl` after the last hold (they outlive the slot data on purpose) |
| seq, tombs, host index | `version-ttl` (7 days) |
| lock | `compute-lock-ttl` (10 s), or deleted by publish |

## Concurrency and distributed-systems guarantees

| Problem | How it is handled |
|---|---|
| N candidates pick a slot with M free hosts | `SPOP` on the slot's set inside one script: exactly `min(N, M)` succeed, each with a different host. The rest get `409`. |
| 2,000 page opens on a cache miss | ① gives the compute lock to exactly one caller; reads return `DRIVE_LOADING` until ② lands. |
| Booking service crashes mid-compute | The compute lock expires after 10 s, and the next page open computes. |
| Booking service crashes after a hold | The hold expires after 5 min and the host goes back into the slot set. Expiry is swept inside every script; no scheduler is needed. |
| Client retries a timed-out hold | The same `holdId` returns the same host; it never takes a second one. Release and invalidate are idempotent. |
| Data computed **before** a booking committed, published **after** | Invalidations take a version from the same counter as ①. ② skips any host invalidated after its `baseVersion`. **Read the DB only after ① returns.** |
| Slow computation published after a newer one | `409 STALE_SNAPSHOT`, and nothing changes. |
| Recompute while a candidate holds a host | Held hosts are left out of the new data. If the new data no longer has that host and slot, the expiring hold won't put it back. |
| Host in several drives | `slotcache:host:{h}:drives` lists them, and ③ updates each drive (atomic per drive). |
| Many service instances / Redis Cluster | The service is stateless. All keys of a drive share the hash tag `{driveId}`, so each script runs on one shard. Slot keys are built inside Lua from that prefix. |

### What the booking service must still guarantee
* **A DB unique constraint on `(host_id, slot_start)`.** Redis replication is asynchronous, so a
  failover can lose the last writes (for example, a hold); the constraint makes sure that never
  becomes a double booking.
* **Max bookings per host per day.** Enforce it in the DB transaction, and once a host reaches the
  limit, invalidate the rest of that host's day with ③.
* **Freed time.** When a calendar event is deleted or a booking is cancelled, wait for expiry or
  `DELETE /drives/{id}` to force a recompute. The cache never adds hosts on its own.

## Where it breaks first at scale
* **A hot drive sits on one Redis shard.** Thousands of candidates are fine; much more would need a 1–2 s
  in-process cache for `GET`.
* **② rewrites the whole drive.** That's fine for 50 hosts × a few weeks of slots. Thousands of hosts
  would need per-host updates.
* **③ fans out once per drive the host belongs to.**

## Running
From the `redis_cache_layer/` folder:
```
cd redis_cache_layer
REDIS_HOST=localhost REDIS_PORT=6379 mvn spring-boot:run
mvn test   # uses an embedded redis-server, no Docker needed
```
