package com.ravalgroups.forms.notification.adapter.out;

import com.ravalgroups.forms.notification.adapter.out.persistence.NotificationLogEntity;
import com.ravalgroups.forms.notification.adapter.out.persistence.NotificationLogJpaRepository;
import com.ravalgroups.forms.notification.application.port.NotificationPort;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev/default provider: records notification_log as SKIPPED (not delivered mail).
 * Set {@code forms.notification.email.provider=smtp} for real delivery.
 */
@Component
@ConditionalOnProperty(name = "forms.notification.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    private final NotificationLogJpaRepository logs;

    public LoggingNotificationAdapter(NotificationLogJpaRepository logs) {
        this.logs = logs;
    }

    @Override
    @Transactional
    public void sendEmail(String recipient, String subject, String body, String eventType) {
        if (recipient == null || recipient.isBlank()) {
            logs.save(NotificationLogEntity.create(
                    UUID.randomUUID(),
                    null,
                    "EMAIL",
                    "(missing)",
                    subject,
                    eventType,
                    "SKIPPED",
                    "No recipient",
                    Instant.now()));
            return;
        }
        log.info(
                "notification.email deferred (provider=log) eventType={} recipient={} subject={}",
                eventType,
                recipient,
                subject);
        log.debug("notification.email.body={}", body);
        logs.save(NotificationLogEntity.create(
                UUID.randomUUID(),
                null,
                "EMAIL",
                recipient,
                subject,
                eventType,
                "SKIPPED",
                "provider_deferred:log",
                Instant.now()));
    }
}
