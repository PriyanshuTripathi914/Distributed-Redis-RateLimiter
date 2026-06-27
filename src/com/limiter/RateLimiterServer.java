// package com.limiter;

// public class RateLimiterServer {
//     public static void main(String[] args) throws InterruptedException {
//         System.out.println(" DISTRIBUTED REDIS RATE LIMITER ");

//         boolean isCI = args.length > 0 && args[0].equals("--ci");
//         RedisManager.initialize(isCI);

//         // Instantiate a rate limiter: Max capacity of 3 tokens, refills at 1 token per second
//         TokenBucketLimiter limiter = new TokenBucketLimiter(3, 1);
//         String testClient = "client_192.168.1.50";

//         System.out.println("\n--- [TEST 1] Blasting 5 Immediate Requests (Burst Traffic) ---");
//         for (int i = 1; i <= 5; i++) {
//             boolean allowed = limiter.allowRequest(testClient);
//             System.out.println("Request :" + i + " -> " + (allowed ? "ALLOWED (200 OK)" : "BLOCKED (429 Too Many Requests)"));
//         }

//         System.out.println("\n--- [TEST 2] Sleeping for 2 Seconds to Allow Token Refill ---");
//         Thread.sleep(2000);

//         System.out.println("\n--- [TEST 3] Testing Refilled Token Availability ---");
//         for (int i = 1; i <= 3; i++) {
//             boolean allowed = limiter.allowRequest(testClient);
//             System.out.println("Post-Refill Request :" + i + " -> " + (allowed ? "ALLOWED (200 OK)" : "BLOCKED (429 Too Many Requests)"));
//         }

//         RedisManager.shutdown();
//     }
// }

package com.limiter;

public class RateLimiterServer {
    public static void main(String[] args) throws InterruptedException {
        System.out.println(" DISTRIBUTED REDIS RATE LIMITER ");

        boolean isCI = args.length > 0 && args[0].equals("--ci");
        RedisManager.initialize(isCI);

        String testClient = "client_192.168.1.50";

        // ==========================================
        // ALGORITHM 1: TOKEN BUCKET CHECK
        // ==========================================
        System.out.println("\n--- [ALGORITHM 1] Executing Token Bucket Core Check ---");
        TokenBucketLimiter bucketLimiter = new TokenBucketLimiter(2, 1);
        for (int i = 1; i <= 3; i++) {
            System.out.println("Bucket Request #" + i + " -> " + 
                (bucketLimiter.allowRequest(testClient) ? "ALLOWED" : "BLOCKED (429)"));
        }

        // ==========================================
        // ALGORITHM 2: SLIDING WINDOW LOG CHECK
        // ==========================================
        System.out.println("\n--- [ALGORITHM 2] Executing Sliding Window Log Check ---");
        // Limit: Max 2 requests allowed in a rolling 5-second window
        SlidingWindowLimiter windowLimiter = new SlidingWindowLimiter(5, 2);

        System.out.println("Bursting 3 quick requests...");
        for (int i = 1; i <= 3; i++) {
            System.out.println("Window Request #" + i + " -> " + 
                (windowLimiter.allowRequest(testClient) ? "ALLOWED" : "BLOCKED (429)"));
        }

        System.out.println("\nHolding execution threads for 5 seconds to slide past the window...");
        Thread.sleep(5100);

        System.out.println("\nTesting window slide clearing availability...");
        System.out.println("Post-Wait Window Request -> " + 
            (windowLimiter.allowRequest(testClient) ? "ALLOWED" : "BLOCKED (429)"));

        RedisManager.shutdown();
    }
}