package com.limiter;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.Arrays;

public class SlidingWindowLimiter {
    private final String windowSizeSeconds;
    private final String maxRequests;

    //lua script (Atomicity)
    private final String luaScript = """
    local key = KEYS[1]
    local now = tonumber(ARGV[1])
    local window = tonumber(ARGV[2])
    local max_limit = tonumber(ARGV[3])

    local clear_before = now - window

    -- 1. Prune all old expired request logs outside of our sliding window
    redis.call('ZREMRANGEBYSCORE', key, 0, clear_before)

    -- 2. Fetch total active requests currently remaining in this window
    local current_requests = redis.call('ZCARD', key)

    -- 3. Check if the client has space left under the rate limit threshold
    if current_requests < max_limit then
        -- Log this current unique request timestamp (score and member are the same)
        redis.call('ZADD', key, now, now)
        
        -- Auto-expiry so dead clients don't clutter up Redis memory over time
        redis.call('EXPIRE', key, math.ceil(window / 1000))
        
        return 1 -- ALLOWED (200 OK)
    else
        return 0 -- BLOCKED (429 Too Many Requests)
    end
    """;

    public SlidingWindowLimiter(int windowSizeSeconds, int maxRequests) {
        this.windowSizeSeconds = String.valueOf(windowSizeSeconds);
        this.maxRequests = String.valueOf(maxRequests);
    }

    public boolean allowRequest(String clientKey) {
        String redisKey = "sliding_window:" + clientKey;
        // Accurate down to the millisecond to handle high-frequency traffic logs
        String currentTimeMillis = String.valueOf(System.currentTimeMillis());

        try (Jedis redis = RedisManager.getConnection()) {
            // Run the logs processing step atomically inside the Redis engine
            Object result = redis.eval(
                luaScript,
                Collections.singletonList(redisKey),
                Arrays.asList(currentTimeMillis, String.valueOf(Integer.parseInt(windowSizeSeconds) * 1000), maxRequests)
            );
            return result.toString().equals("1");
        } catch (Exception e) {
            System.err.println("[ERROR] Sliding Window Script Failed: " + e.getMessage());
            return true; // Fail-open fallback
        }
    }
}