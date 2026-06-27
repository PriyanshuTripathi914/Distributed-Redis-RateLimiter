package com.limiter;

public class RateLimiterServer {
    public static void main(String[] args) throws InterruptedException {
        System.out.println(" DISTRIBUTED REDIS RATE LIMITER ");

        boolean isCI = args.length > 0 && args[0].equals("--ci");
        RedisManager.initialize(isCI);

        // Instantiate a rate limiter: Max capacity of 3 tokens, refills at 1 token per second
        TokenBucketLimiter limiter = new TokenBucketLimiter(3, 1);
        String testClient = "client_192.168.1.50";

        System.out.println("\n--- [TEST 1] Blasting 5 Immediate Requests (Burst Traffic) ---");
        for (int i = 1; i <= 5; i++) {
            boolean allowed = limiter.allowRequest(testClient);
            System.out.println("Request :" + i + " -> " + (allowed ? "ALLOWED (200 OK)" : "BLOCKED (429 Too Many Requests)"));
        }

        System.out.println("\n--- [TEST 2] Sleeping for 2 Seconds to Allow Token Refill ---");
        Thread.sleep(2000);

        System.out.println("\n--- [TEST 3] Testing Refilled Token Availability ---");
        for (int i = 1; i <= 3; i++) {
            boolean allowed = limiter.allowRequest(testClient);
            System.out.println("Post-Refill Request :" + i + " -> " + (allowed ? "ALLOWED (200 OK)" : "BLOCKED (429 Too Many Requests)"));
        }

        RedisManager.shutdown();
    }
}