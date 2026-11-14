package com.veriweave.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe In-Memory Sliding Window Rate Limiting Service.
 * Protects critical authentication and invitation endpoints from brute-force and DoS attacks.
 *
 * NOTE FOR PRODUCTION DEPLOYMENT:
 * This implementation provides fast, zero-external-dependency rate limiting suitable for single-instance
 * deployments (such as Render single web services). In horizontal multi-instance or auto-scaling clusters,
 * rate limiting state is node-local; for cross-cluster distributed enforcement, swap with a Redis-backed
 * store (e.g., Redis Token Bucket / Redisson).
 */
@Slf4j
@Service
public class RateLimitingService {

    private static class RequestBucket {
        final ConcurrentLinkedQueue<Long> timestamps = new ConcurrentLinkedQueue<>();
    }

    private final Map<String, RequestBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Checks if the request under the specified key is allowed within the rate limit window.
     *
     * @param key Unique identifier (e.g., IP address, user email, or combined action key)
     * @param maxRequests Maximum allowed requests in the window
     * @param windowSeconds Duration of the rate limit window in seconds
     * @throws RateLimitExceededException if request limit is exceeded
     */
    public void checkRateLimit(String key, int maxRequests, long windowSeconds) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - (windowSeconds * 1000L);

        RequestBucket bucket = buckets.computeIfAbsent(key, k -> new RequestBucket());

        // Remove entries older than the sliding window
        Long oldest;
        while ((oldest = bucket.timestamps.peek()) != null && oldest < windowStart) {
            bucket.timestamps.poll();
        }

        if (bucket.timestamps.size() >= maxRequests) {
            long earliest = bucket.timestamps.peek() != null ? bucket.timestamps.peek() : windowStart;
            long retryAfterSeconds = Math.max(1, ((earliest + (windowSeconds * 1000L)) - now) / 1000L);
            log.warn("Rate limit exceeded for key '{}'. Limit: {} req/{}s, Retry after: {}s",
                    key, maxRequests, windowSeconds, retryAfterSeconds);
            throw new RateLimitExceededException(
                    "Too many requests. Please wait " + retryAfterSeconds + " seconds before retrying.",
                    retryAfterSeconds
            );
        }

        bucket.timestamps.add(now);

        // Opportunistic cleanup of stale keys if map exceeds threshold
        if (buckets.size() > 5000) {
            cleanupExpiredBuckets(windowStart);
        }
    }

    /**
     * Resets all rate limit buckets. Used for testing and administrative resets.
     */
    public void reset() {
        buckets.clear();
    }

    private synchronized void cleanupExpiredBuckets(long threshold) {
        Iterator<Map.Entry<String, RequestBucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RequestBucket> entry = iterator.next();
            Long latest = entry.getValue().timestamps.peek();
            if (latest == null || latest < threshold) {
                iterator.remove();
            }
        }
    }
}
