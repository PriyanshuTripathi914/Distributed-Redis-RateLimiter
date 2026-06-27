package com.limiter;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.Arrays;

public class TokenBucketLimiter {
    private final String maxCapacity;
    private final String refillRate;

    // lua script
    private final String luaScript = """
    local key = KEYS[1]
    local max_capacity = tonumber(ARGV[1])
    local refill_rate = tonumber(ARGV[2])
    local current_time = tonumber(ARGV[3])

    -- Fetch existing rate limit metrics for the user
    local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
    local current_tokens = tonumber(bucket[1])
    local last_refill_time = tonumber(bucket[2])

    -- If the user hash is empty, initialize a fresh full bucket
    if not current_tokens then
        current_tokens = max_capacity
        last_refill_time = current_time
    else
        -- Compute lazy refilling based on time elapsed since last check
        local elapsed_time = current_time - last_refill_time
        if elapsed_time > 0 then
            local tokens_to_add = elapsed_time * refill_rate
            current_tokens = math.min(max_capacity, current_tokens + tokens_to_add)
            last_refill_time = current_time
        end
    end

    -- Process request evaluation atomatically
    if current_tokens >= 1 then
        current_tokens = current_tokens - 1
        redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
        return 1  -- ALLOWED (200 OK)
    else
        return 0  -- BLOCKED (429 Too Many Requests)
    end
    """;

    public TokenBucketLimiter(int maxCapacity, int refillRatePerSecond) {
        this.maxCapacity = String.valueOf(maxCapacity);
        this.refillRate = String.valueOf(refillRatePerSecond);
    }

    public boolean allowRequest(String clientKey) {
        String redisKey = "rate_limit:" + clientKey;
        String currentTimeSeconds = String.valueOf(System.currentTimeMillis() / 1000);

        try (Jedis redis = RedisManager.getConnection()) {
            // Execute the script atomically inside the Redis engine
            Object result = redis.eval(
                luaScript, 
                Collections.singletonList(redisKey), 
                Arrays.asList(maxCapacity, refillRate, currentTimeSeconds)
            );

            return result.toString().equals("1");
        } catch (Exception e) {
            System.err.println("[ERROR] Lua Execution Script Failed: " + e.getMessage());
            return true; // Fail-open strategy
        }
    }
}