-- A host is no longer free in [from, to): booking confirmed, calendar event added, host removed.
-- Removes the host from every overlapping slot's set, consumes/cancels its holds there, and records
-- a tombstone so a computation already in flight cannot put the host back.
-- ARGV[1] nowMillis, ARGV[2] hostId, ARGV[3] fromEpoch, ARGV[4] toEpoch, ARGV[5] versionTtlSec
-- Reply: {removedFromSlots, cancelledHolds}
local host, from, to = ARGV[2], tonumber(ARGV[3]), tonumber(ARGV[4])
sweep(tonumber(ARGV[1]))

local seq = redis.call('INCR', K_SEQ)
redis.call('EXPIRE', K_SEQ, ARGV[5])
redis.call('ZADD', K_TOMBS, seq, seq .. '|' .. host .. '|' .. from .. '|' .. to)
redis.call('EXPIRE', K_TOMBS, ARGV[5])

local len = tonumber(redis.call('HGET', K_META, 'slotLengthSec') or '0')
local removed, cancelled = 0, 0

-- Slot [e, e+len) overlaps [from, to) iff from - len < e < to.
local slots = redis.call('ZRANGEBYSCORE', K_INDEX, '(' .. (from - len), '(' .. to)
for _, iso in ipairs(slots) do
  if redis.call('SISMEMBER', slot_key(iso), host) == 1 then
    remove_host(iso, host)
    removed = removed + 1
  end
end

local all = redis.call('HGETALL', K_HOLDINFO)
for k = 1, #all, 2 do
  local e, _, h = parse_hold(all[k + 1])
  -- 'e >= from' keeps this correct when the drive data expired (len unknown, holds outlive it).
  if h == host and e < to and (e >= from or e + len > from) then
    redis.call('ZREM', K_HOLDS, all[k])
    redis.call('HDEL', K_HOLDINFO, all[k])
    cancelled = cancelled + 1
  end
end
return {tostring(removed), tostring(cancelled)}
