package com.limiter;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.embedded.RedisServer;

public class RedisManager {
    private static JedisPool pool;
    private static RedisServer embeddedServer;

    public static void initialize(boolean isCI) {
        // If we are running locally on Windows, spin up the embedded Redis server
        if (!isCI) {
            System.out.println("[SYSTEM] Local environment detected. Starting Embedded Redis Server...");
            try {
                embeddedServer = new RedisServer(6379);
                embeddedServer.start();
                System.out.println("[SYSTEM] Embedded Redis successfully started on port 6379.");
            } catch (Exception e) {
                System.out.println("[WARN] Embedded Redis failed to start. Local instance might already be running.");
            }
        } else {
            System.out.println("[CI/CD Pipeline] Running in GitHub Cloud. Connecting directly to Docker container...");
        }

        // Configure connection pooling for optimal multi-threaded performance
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);  // Max 20 concurrent connections
        config.setMaxIdle(10);   // Keep up to 10 idle connections
        config.setMinIdle(2);    // Always keep at least 2 connections open

        // Connect to localhost on standard Redis port 6379
        pool = new JedisPool(config, "localhost", 6379);
    }

    // Grab a clean, working Redis connection connection from the pool
    public static Jedis getConnection() {
        return pool.getResource();
    }

    // Gracefully teardown links and stop servers on shutdown
    public static void shutdown() {
        if (pool != null) {
            pool.close();
        }
        if (embeddedServer != null) {
            embeddedServer.stop();
        }
        System.out.println("[SYSTEM] Redis connection layers closed down cleanly.");
    }
}