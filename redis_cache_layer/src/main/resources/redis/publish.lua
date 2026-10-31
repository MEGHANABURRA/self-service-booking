-- Store the slots the booking service computed: for each slot, the set of free hosts.
-- ARGV[1] nowMillis, ARGV[2] baseVersion, ARGV[3] slotLengthSec, ARGV[4] driveTtlSec,
-- ARGV[5..] groups of: epoch, iso, hostCount, host_1 .. host_n
-- Reply: {'OK', loadedPairs} | {'STALE', appliedVersion}
--
-- Concurrency rules (all applied inside this one atomic script):
--  * baseVersion older than the applied one -> rejected (a slow computation arrived late).
--  * Host currently on hold for that slot -> skipped (a candidate is mid-booking on it). If the new
--    data no longer has that host-slot at all, the hold is marked so expiry will not restore it.
--  * Host invalidated AFTER this computation started (tombstone seq > baseVersion) -> skipped, so
--    data read before a booking committed cannot bring the booked host back.
local now, base, len, ttl = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3]), tonumber(ARGV[4])
sweep(now)

local function release_lock()
  if redis.call('GET', K_LOCK) == tostring(base) then
    redis.call('DEL', K_LOCK)
  end
end

local applied = tonumber(redis.call('HGET', K_META, 'appliedVersion') or '-1')
if base < applied then
  release_lock()
  return {'STALE', tostring(applied)}
end

-- Parse the incoming slots.
local incoming = {}   -- list of {epoch, iso, {hosts}}
local inData = {}     -- "iso|host" -> true
local i = 5
while i <= #ARGV do
  local epoch, iso, n = tonumber(ARGV[i]), ARGV[i + 1], tonumber(ARGV[i + 2])
  local hosts = {}
  for j = 1, n do
    local h = ARGV[i + 2 + j]
    table.insert(hosts, h)
    inData[iso .. '|' .. h] = true
  end
  table.insert(incoming, {epoch, iso, hosts})
  i = i + 3 + n
end

-- Hosts on hold.
local held = {}
local holds = redis.call('HGETALL', K_HOLDINFO)
for k = 1, #holds, 2 do
  local e, iso, h = parse_hold(holds[k + 1])
  local pair = iso .. '|' .. h
  held[pair] = true
  if not inData[pair] then
    redis.call('HSET', K_HOLDINFO, holds[k], e .. '|' .. iso .. '|' .. h .. '|0')
  end
end

-- Invalidations newer than this computation.
local tombs = {}
for _, t in ipairs(redis.call('ZRANGEBYSCORE', K_TOMBS, '(' .. base, '+inf')) do
  local _, h, f, to = string.match(t, '^(%d+)|(.+)|(%d+)|(%d+)$')
  table.insert(tombs, {h, tonumber(f), tonumber(to)})
end

-- Replace the old data.
for _, iso in ipairs(redis.call('ZRANGE', K_INDEX, 0, -1)) do
  redis.call('DEL', slot_key(iso))
end
redis.call('DEL', K_INDEX)
redis.call('HSET', K_META, 'appliedVersion', base, 'slotLengthSec', len)
redis.call('EXPIRE', K_META, ttl)

local loaded = 0
for _, slot in ipairs(incoming) do
  local epoch, iso, hosts = slot[1], slot[2], slot[3]
  for _, h in ipairs(hosts) do
    local skip = held[iso .. '|' .. h]
    if not skip then
      for _, t in ipairs(tombs) do
        if t[1] == h and epoch < t[3] and epoch + len > t[2] then
          skip = true
          break
        end
      end
    end
    if not skip then
      add_host(epoch, iso, h)
      loaded = loaded + 1
    end
  end
end
redis.call('EXPIRE', K_INDEX, ttl)

-- Invalidations up to this version are now reflected in the stored data.
redis.call('ZREMRANGEBYSCORE', K_TOMBS, '-inf', base)
release_lock()
return {'OK', tostring(loaded)}
