package com.limiter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class RateLimiterBenchmark {
    public static void main(String[] args) throws Exception {
        System.out.println(" HIGH-PRECISION LATENCY BENCHMARK ENGINE ");
        
        String targetUrl = "http://localhost:8080/api/resource";
        
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
                
        int totalRequests = 500;
        int concurrencyLevel = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        
        System.out.println("[BENCHMARK] Warming up and executing " + totalRequests + " requests...");
        long totalExecutionStart = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .GET()
                            .build();

                    long startNano = System.nanoTime();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    long endNano = System.nanoTime();

                    long latencyMicros = (endNano - startNano) / 1000;
                    latencies.add(latencyMicros);
                    
                } catch (Exception e) {
                    // Failures ignored to prevent skewed data
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long totalExecutionTimeMs = System.currentTimeMillis() - totalExecutionStart;

        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        
        for (long lat : latencies) {
            sum += lat;
            if (lat < min) min = lat;
            if (lat > max) max = lat;
        }

        double avgMicros = (double) sum / latencies.size();
        double avgMs = avgMicros / 1000.0;
        double throughputRps = ((double) latencies.size() / totalExecutionTimeMs) * 1000.0;

        System.out.println("\n--- DETAILED LATENCY PROFILES ---");
        System.out.println("Total Requests Processed : " + latencies.size());
        System.out.println("System Throughput        : " + String.format("%.2f", throughputRps) + " requests/sec");
        System.out.println("Minimum Latency          : " + ((double)min / 1000.0) + " ms");
        System.out.println("Average Latency          : " + String.format("%.3f", avgMs) + " ms");
        System.out.println("Maximum Latency          : " + ((double)max / 1000.0) + " ms");
    }
}