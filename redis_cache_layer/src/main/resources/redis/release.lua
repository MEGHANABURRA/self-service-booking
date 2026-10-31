-- Candidate abandoned the booking or its DB write failed: give the host back to the slot now.
-- ARGV[1] nowMillis, ARGV[2] holdId
-- Reply: '1' released, '0' unknown / already expired / already consumed (idempotent)
local info = redis.call('HGET', K_HOLDINFO, ARGV[2])
sweep(tonumber(ARGV[1]))
if not info then
  return '0'
end
redis.call('ZREM', K_HOLDS, ARGV[2])
redis.call('HDEL', K_HOLDINFO, ARGV[2])
local e, iso, h, restore = parse_hold(info)
if restore and redis.call('EXISTS', K_META) == 1 then
  add_host(e, iso, h)
end
return '1'
