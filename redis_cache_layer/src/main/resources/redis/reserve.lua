-- Candidate picked a slot: take one free host out of that slot's set and hold it.
-- SPOP removes a random member atomically; when the set is empty it returns nothing, which is
-- how the (N+1)th candidate for a slot with N hosts gets refused.
-- ARGV[1] nowMillis, ARGV[2] epoch, ARGV[3] iso, ARGV[4] holdId (idempotency key),
-- ARGV[5] expiresAtMillis, ARGV[6] holdKeysTtlSec
-- Reply: {'MISS'} | {'TAKEN'} | {'HELD', epoch, iso, hostId, expiresAtMillis, slotLengthSec}
sweep(tonumber(ARGV[1]))
if redis.call('EXISTS', K_META) == 0 then
  return {'MISS'}
end
local len = redis.call('HGET', K_META, 'slotLengthSec')

-- Retried request (e.g. client timeout): return the hold it already got, never a second host.
local existing = redis.call('HGET', K_HOLDINFO, ARGV[4])
if existing then
  local e, iso, h = parse_hold(existing)
  return {'HELD', tostring(e), iso, h, redis.call('ZSCORE', K_HOLDS, ARGV[4]), len}
end

local key = slot_key(ARGV[3])
local host = redis.call('SPOP', key)
if not host then
  return {'TAKEN'}
end
if redis.call('SCARD', key) == 0 then
  redis.call('ZREM', K_INDEX, ARGV[3])
end
redis.call('ZADD', K_HOLDS, ARGV[5], ARGV[4])
redis.call('HSET', K_HOLDINFO, ARGV[4], ARGV[2] .. '|' .. ARGV[3] .. '|' .. host .. '|1')
redis.call('EXPIRE', K_HOLDS, ARGV[6])
redis.call('EXPIRE', K_HOLDINFO, ARGV[6])
return {'HELD', ARGV[2], ARGV[3], host, ARGV[5], len}
