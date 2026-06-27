package com.limiter;

import redis.clients.jedis.Jedis;
import java.util.Map;
import java.util.HashMap;

public class TokenBucketLimiter {
    private final int maxCapacity;
    private final int refillRatePerSecond;

    public TokenBucketLimiter(int maxCapacity, int refillRatePerSecond) {
        this.maxCapacity = maxCapacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    /**
     * Determines whether a specific client request is allowed through or blocked.
     * @param clientKey Unique identifier (e.g., client IP or username)
     * @return true if allowed, false if rate-limited
     */
    public boolean allowRequest(String clientKey) {
        String redisKey = "rate_limit:" + clientKey;
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        try (Jedis redis = RedisManager.getConnection()) {
            // Retrieve current bucket values from Redis (stored as a Hash Map)
            Map<String, String> bucketData = redis.hgetAll(redisKey);

            double currentTokens;
            long lastRefillTime;

            // If the client doesn't exist in Redis yet, initialize a brand new full bucket
            if (bucketData == null || bucketData.isEmpty()) {
                currentTokens = maxCapacity;
                lastRefillTime = currentTimeSeconds;
            } else {
                // Parse existing data out of the Redis hash maps
                currentTokens = Double.parseDouble(bucketData.get("tokens"));
                lastRefillTime = Long.parseLong(bucketData.get("last_refill_time"));

                // Calculate elapsed time and dynamically refill tokens lazily
                long elapsedTime = currentTimeSeconds - lastRefillTime;
                if (elapsedTime > 0) {
                    double tokensToAdd = elapsedTime * refillRatePerSecond;
                    currentTokens = Math.min(maxCapacity, currentTokens + tokensToAdd);
                    lastRefillTime = currentTimeSeconds;
                }
            }

            // Check if the bucket has enough tokens to service this request
            if (currentTokens >= 1.0) {
                currentTokens -= 1.0; // Deduct one token

                // Update the updated values back into Redis
                Map<String, String> updatedData = new HashMap<>();
                updatedData.put("tokens", String.valueOf(currentTokens));
                updatedData.put("last_refill_time", String.valueOf(lastRefillTime));
                redis.hmset(redisKey, updatedData);

                return true; // Request allowed!
            }

            // Not enough tokens left in the bucket
            return false; // Rate limited!

        } catch (Exception e) {
            System.err.println("[ERROR] Token Bucket execution error: " + e.getMessage());
            return true; // Fail-open strategy: allow traffic if the limiter crashes
        }
    }
}