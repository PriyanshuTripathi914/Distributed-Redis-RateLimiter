package com.limiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InternalBenchmark {
    public static void main(String[] args) throws Exception {
        System.out.println("=== INTERNAL ENGINE LATENCY CHECK ===");
        RedisManager.initialize(false);
        
        TokenBucketLimiter limiter = new TokenBucketLimiter(1000, 100);
        int totalTests = 500;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < totalTests; i++) {
            executor.submit(() -> {
                long start = System.nanoTime();
                limiter.allowRequest("benchmark_user");
                long end = System.nanoTime();
                latencies.add((end - start) / 1000); 
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long sum = 0;
        for (long lat : latencies) sum += lat;
        double avgMs = (sum / (double) latencies.size()) / 1000.0;

        System.out.println("Pure Algorithmic Execution Latency: " + String.format("%.3f", avgMs) + " ms");
        RedisManager.shutdown();
    }
}