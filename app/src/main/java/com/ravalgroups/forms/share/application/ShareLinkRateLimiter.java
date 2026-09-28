package com.ravalgroups.forms.share.application;

import com.ravalgroups.forms.shared.config.FormsRedisProperties;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Soft rate limit for public share-link endpoints. Falls open if Redis is unavailable
 * so collection is not blocked by infrastructure blips.
 */
@Component
public class ShareLinkRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkRateLimiter.class);
    private static final int LIMIT = 40;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectProvider<StringRedisTemplate> redis;
    private final String keyPrefix;

    public ShareLinkRateLimiter(ObjectProvider<StringRedisTemplate> redis, FormsRedisProperties redisProperties) {
        this.redis = redis;
        String prefix = redisProperties.keyPrefix();
        this.keyPrefix = (prefix == null || prefix.isBlank() ? "forms:" : prefix) + "ratelimit:share:";
    }

    public void check(String tokenFingerprint, String clientKey) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        String key = keyPrefix + safe(tokenFingerprint) + ":" + safe(clientKey);
        try {
            Long count = template.opsForValue().increment(key);
            if (count != null && count == 1L) {
                template.expire(key, WINDOW);
            }
            if (count != null && count > LIMIT) {
                throw new DomainException("RATE_LIMITED", "Too many requests; try again shortly");
            }
        } catch (DomainException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Share-link rate limit check skipped: {}", ex.toString());
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9:_-]", "_");
    }
}
