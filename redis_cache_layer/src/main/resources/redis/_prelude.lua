-- Shared helpers, prepended to every script. Every key of one drive carries the hash tag
-- {driveId}, so on Redis Cluster they all live on one shard and each script is atomic.
--
-- KEYS[1] meta      HASH   appliedVersion, slotLengthSec      (exists = drive is loaded)
-- KEYS[2] index     ZSET   member ISO slot start, score epoch  (slots with >= 1 free host)
-- KEYS[3] holds     ZSET   member holdId, score expiresAtMillis
-- KEYS[4] holdinfo  HASH   holdId -> "epoch|iso|hostId|restore"
-- KEYS[5] tombs     ZSET   member "seq|hostId|fromEpoch|toEpoch", score seq
-- KEYS[6] seq       STRING drive version counter
-- KEYS[7] lock      STRING compute lock; value = baseVersion of the computation in progress
--
-- Per slot (name built from the same prefix, so it shares the hash tag):
--   slotcache:drive:{driveId}:slot:<ISO start>   SET of free hostIds
local K_META, K_INDEX, K_HOLDS, K_HOLDINFO = KEYS[1], KEYS[2], KEYS[3], KEYS[4]
local K_TOMBS, K_SEQ, K_LOCK = KEYS[5], KEYS[6], KEYS[7]
local PREFIX = string.sub(K_META, 1, -5) -- strip "meta"

local function slot_key(iso)
  return PREFIX .. 'slot:' .. iso
end

-- "epoch|iso|host|restore" -> epoch, iso, host, restore
local function parse_hold(info)
  local e, iso, h, r = string.match(info, '^(%d+)|([^|]+)|(.+)|([01])$')
  return tonumber(e), iso, h, r == '1'
end

-- Slot sets live exactly as long as the drive's meta, so the whole drive expires together.
local function add_host(epoch, iso, host)
  local key = slot_key(iso)
  if redis.call('SADD', key, host) == 1 then
    redis.call('ZADD', K_INDEX, epoch, iso)
    local ttl = redis.call('PTTL', K_META)
    if ttl > 0 then
      redis.call('PEXPIRE', key, ttl)
    end
  end
end

local function remove_host(iso, host)
  local key = slot_key(iso)
  if redis.call('SREM', key, host) == 1 and redis.call('SCARD', key) == 0 then
    redis.call('ZREM', K_INDEX, iso)
  end
end

-- Expired holds give their host back. Runs lazily inside every script, so correctness does not
-- depend on a background scheduler or on (lossy) keyspace notifications.
local function sweep(nowMillis)
  local expired = redis.call('ZRANGEBYSCORE', K_HOLDS, '-inf', nowMillis)
  for _, id in ipairs(expired) do
    local info = redis.call('HGET', K_HOLDINFO, id)
    redis.call('ZREM', K_HOLDS, id)
    redis.call('HDEL', K_HOLDINFO, id)
    if info and redis.call('EXISTS', K_META) == 1 then
      local e, iso, h, restore = parse_hold(info)
      if restore then
        add_host(e, iso, h)
      end
    end
  end
end
