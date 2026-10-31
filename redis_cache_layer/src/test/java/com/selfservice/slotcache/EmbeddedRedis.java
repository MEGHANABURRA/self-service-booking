package com.selfservice.slotcache;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.io.UncheckedIOException;

/** One real redis-server process shared by every test class in the JVM. */
public final class EmbeddedRedis {

    public static final int PORT = 6399;
    private static RedisServer server;

    private EmbeddedRedis() {
    }

    public static synchronized void start() {
        if (server != null) {
            return;
        }
        try {
            server = new RedisServer(PORT);
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (IOException ignored) {
                // JVM is exiting anyway
            }
        }));
    }
}
