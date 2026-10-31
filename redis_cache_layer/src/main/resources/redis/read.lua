-- Slots with at least one free host in [min, max).
-- ARGV[1] nowMillis, ARGV[2] minEpoch (inclusive), ARGV[3] maxEpoch (exclusive)
-- Reply: {'MISS'} | {'LOADING'} | {'HIT', slotLengthSec, iso...}
sweep(tonumber(ARGV[1]))
if redis.call('EXISTS', K_META) == 0 then
  if redis.call('EXISTS', K_LOCK) == 1 then
    return {'LOADING'}
  end
  return {'MISS'}
end
local reply = {'HIT', redis.call('HGET', K_META, 'slotLengthSec')}
for _, iso in ipairs(redis.call('ZRANGEBYSCORE', K_INDEX, ARGV[2], '(' .. ARGV[3])) do
  table.insert(reply, iso)
end
return reply
