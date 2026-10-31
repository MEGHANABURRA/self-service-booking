-- Drop a drive's slots, holds and lock (e.g. the hiring drive closed). The version counter and
-- tombstones are kept so a computation still in flight cannot be mistaken for a fresh one.
-- Reply: '1'
for _, iso in ipairs(redis.call('ZRANGE', K_INDEX, 0, -1)) do
  redis.call('DEL', slot_key(iso))
end
redis.call('DEL', K_META, K_INDEX, K_HOLDS, K_HOLDINFO, K_LOCK)
return '1'
