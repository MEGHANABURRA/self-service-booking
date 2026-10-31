-- Booking service asks: "may I compute this drive's slots?"
-- Only one caller at a time gets the compute lock; everyone else is told to wait and re-read.
-- ARGV[1] lockTtlMillis, ARGV[2] versionTtlSec
-- Reply: {'COMPUTE', baseVersion} | {'BUSY', lockRemainingMillis}
if redis.call('EXISTS', K_LOCK) == 1 then
  return {'BUSY', tostring(redis.call('PTTL', K_LOCK))}
end
local version = redis.call('INCR', K_SEQ)
redis.call('EXPIRE', K_SEQ, ARGV[2])
redis.call('SET', K_LOCK, version, 'PX', ARGV[1])
return {'COMPUTE', tostring(version)}
