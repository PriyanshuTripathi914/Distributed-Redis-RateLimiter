package com.limiter;

import redis.clients.jedis.Jedis;

public class RateLimiterServer {
    public static void main(String[] args) {
        System.out.println(" DISTRIBUTED REDIS RATE LIMITER ");

        // Check if the '--ci' flag was passed by GitHub Actions
        boolean isCI = args.length > 0 && args[0].equals("--ci");

        // Initialize the connection infrastructure
        RedisManager.initialize(isCI);

        try (Jedis redis = RedisManager.getConnection()) {
            // Perform a network heartbeat diagnostic check (Ping)
            String response = redis.ping();
            System.out.println("[NETWORK] Redis Ping Status: " + response);

            // Test basic write/read capability
            redis.set("project_stage", "DAY_1_COMPLETE");
            String value = redis.get("project_stage");
            System.out.println("[DATABASE] Successfully verified cluster data integrity. Value: " + value);

        } catch (Exception e) {
            System.err.println("[CRITICAL ERROR] Failed to connect to Redis matrix: " + e.getMessage());
            System.exit(1);
        } finally {
            RedisManager.shutdown();
        }
    }
}