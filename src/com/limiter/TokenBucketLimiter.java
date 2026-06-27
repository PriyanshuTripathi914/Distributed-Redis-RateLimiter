package com.limiter;

import redis.clients.jedis.Jedis;
import java.util.Collections;
import java.util.Arrays;

public class TokenBucketLimiter {
    private final String maxCapacity;
    private final String refillRatePerMillisecond;

    // Tocket Bucket (Atomicity)
    private final String luaScript = """
        local key = KEYS[1]
        local max_capacity = tonumber(ARGV[1])
        local refill_rate_per_ms = tonumber(ARGV[2])
        local current_time_ms = tonumber(ARGV[3])

        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_time')
        local current_tokens = tonumber(bucket[1])
        local last_refill_time = tonumber(bucket[2])

        if not current_tokens then
            current_tokens = max_capacity
            last_refill_time = current_time_ms
        else
            -- Calculate dynamic lazy refill using millisecond differences
            local elapsed_time_ms = current_time_ms - last_refill_time
            if elapsed_time_ms > 0 then
                local tokens_to_add = elapsed_time_ms * refill_rate_per_ms
                current_tokens = math.min(max_capacity, current_tokens + tokens_to_add)
                last_refill_time = current_time_ms
            end
        end

        if current_tokens >= 1 then
            current_tokens = current_tokens - 1
            redis.call('HMSET', key, 'tokens', current_tokens, 'last_refill_time', last_refill_time)
            return 1 -- Allowed
        else
            return 0 -- Rate Limited
        end
        """;

    public TokenBucketLimiter(int maxCapacity, int refillRatePerSecond) {
        this.maxCapacity = String.valueOf(maxCapacity);
        // 1 token/sec = 0.001 tokens/ms
        this.refillRatePerMillisecond = String.valueOf((double) refillRatePerSecond / 1000.0);
    }

    public boolean allowRequest(String clientKey) {
        String redisKey = "rate_limit:" + clientKey;
        
        String currentTimeMillis = String.valueOf(System.currentTimeMillis());

        try (Jedis redis = RedisManager.getConnection()) {
            Object result = redis.eval(
                luaScript, 
                Collections.singletonList(redisKey), 
                Arrays.asList(maxCapacity, refillRatePerMillisecond, currentTimeMillis)
            );
            return result.toString().equals("1");
        } catch (Exception e) {
            System.err.println("[ERROR] Token Bucket execution error: " + e.getMessage());
            return true;
        }
    }
}