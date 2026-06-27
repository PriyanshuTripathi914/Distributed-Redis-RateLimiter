package com.limiter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterTest {
    public static void main(String[] args) throws Exception {
        System.out.println(" AUTOMATED GATEWAY STRESS TESTER ");
        
        String targetUrl = "http://localhost:8080/api/resource";
        HttpClient client = HttpClient.newHttpClient();
        
        int totalRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        System.out.println("[TEST] Bombarding gateway endpoint concurrently...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else if (response.statusCode() == 429) {
                        blockedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Network call failed: " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("\n--- STRESS TEST SIMULATION RESULTS ---");
        System.out.println("Total Time Elapsed: " + duration + " ms");
        System.out.println(" Allowed Requests (200 OK): " + successCount.get());
        System.out.println(" Blocked Requests (429 Too Many Requests): " + blockedCount.get());

        if (successCount.get() == 5) {
            System.out.println("\n TEST PASSED : No Race Leak!");
        } else {
            System.out.println("\n TEST FAILED : Capacity leak detected !");
        }
    }
}