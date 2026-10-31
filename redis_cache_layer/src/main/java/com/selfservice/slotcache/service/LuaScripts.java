package com.selfservice.slotcache.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Each script = shared prelude + body, loaded once. Spring runs them via EVALSHA with EVAL fallback. */
@SuppressWarnings("rawtypes")
final class LuaScripts {

    static final RedisScript<List> SNAPSHOT = list("snapshot");
    static final RedisScript<List> PUBLISH = list("publish");
    static final RedisScript<List> READ = list("read");
    static final RedisScript<List> RESERVE = list("reserve");
    static final RedisScript<String> RELEASE = RedisScript.of(source("release"), String.class);
    static final RedisScript<List> INVALIDATE = list("invalidate");
    static final RedisScript<String> DELETE = RedisScript.of(source("delete"), String.class);

    private LuaScripts() {
    }

    private static RedisScript<List> list(String name) {
        return RedisScript.of(source(name), List.class);
    }

    private static String source(String name) {
        return read("_prelude") + "\n" + read(name);
    }

    private static String read(String name) {
        try {
            return new ClassPathResource("redis/" + name + ".lua").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing Lua script " + name, e);
        }
    }
}
