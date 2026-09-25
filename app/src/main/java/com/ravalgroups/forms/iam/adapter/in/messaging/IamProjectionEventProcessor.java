package com.ravalgroups.forms.iam.adapter.in.messaging;

import com.ravalgroups.forms.iam.application.ApplyIamDomainEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent IAM event intake. Applies projection upserts after recording processed_event.
 */
@Component
public class IamProjectionEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IamProjectionEventProcessor.class);

    private final JdbcTemplate jdbcTemplate;
    private final ApplyIamDomainEvent applyIamDomainEvent;

    public IamProjectionEventProcessor(JdbcTemplate jdbcTemplate, ApplyIamDomainEvent applyIamDomainEvent) {
        this.jdbcTemplate = jdbcTemplate;
        this.applyIamDomainEvent = applyIamDomainEvent;
    }

    @Transactional
    public void process(String eventId, String routingKey, String payload) {
        String resolvedEventId = eventId == null || eventId.isBlank() ? digest(routingKey, payload) : eventId;
        String eventType = routingKey == null ? "unknown" : routingKey;

        int inserted = jdbcTemplate.update(
                """
                INSERT INTO processed_event (event_id, event_type, source)
                VALUES (?, ?, 'IAM')
                ON CONFLICT (event_id) DO NOTHING
                """,
                resolvedEventId,
                eventType);

        if (inserted == 0) {
            log.debug("Skipping duplicate IAM event id={} type={}", resolvedEventId, eventType);
            return;
        }

        applyIamDomainEvent.apply(routingKey, payload);
        log.info("Applied IAM domain event id={} type={}", resolvedEventId, eventType);
    }

    private static String digest(String routingKey, String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((routingKey == null ? "" : routingKey).getBytes(StandardCharsets.UTF_8));
            digest.update((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash event identity", ex);
        }
    }
}
