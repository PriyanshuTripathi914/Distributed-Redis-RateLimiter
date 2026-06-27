package com.limiter;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class RateLimiterServer {

    public static void main(String[] args) throws Exception {
        System.out.println(" DISTRIBUTED REDIS API GATEWAY ");

        boolean isCI = args.length > 0 && args[0].equals("--ci");
        RedisManager.initialize(isCI);

        // Tocken Bucket (5 token , 1 second refill rate)
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1);

        // HTTP network socket listener on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Intercept incoming requests hitting the target API path
        server.createContext("/api/resource", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {

                // Isolating client IP address
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                
                System.out.println("[GATEWAY] Evaluating request context for client IP: " + clientIp);

                // lua script running
                boolean isAllowed = limiter.allowRequest(clientIp);

                String responseBody;
                int responseCode;

                if (isAllowed) {
                    responseCode = 200; // Success
                    responseBody = "{\"status\": \"SUCCESS\", \"message\": \"Authorized request passed through gateway!\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                } else {
                    responseCode = 429; // Too Many Requests Status
                    responseBody = "{\"status\": \"REJECTED\", \"error\": \"429 Too Many Requests. Token bucket exhausted.\"}";
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.getResponseHeaders().set("Retry-After", "5");
                }

                // Push payload buffers out over the established TCP wire network connection
                byte[] responseBytes = responseBody.getBytes();
                exchange.sendResponseHeaders(responseCode, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });

        // Hooks processor to safely close links if Ctrl+C is triggered
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SHUTDOWN] Terminating server operations...");
            server.stop(0);
            RedisManager.shutdown();
        }));

        server.setExecutor(null); 
        server.start();
        System.out.println("[GATEWAY] API Server actively listening on http://localhost:8080/api/resource");
    }
}