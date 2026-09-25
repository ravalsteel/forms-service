package com.ravalgroups.forms.notification.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
public class NotificationLogEntity {

    @Id
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, length = 320)
    private String recipient;

    @Column(length = 512)
    private String subject;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationLogEntity() {}

    public static NotificationLogEntity create(
            UUID id,
            UUID companyId,
            String channel,
            String recipient,
            String subject,
            String eventType,
            String status,
            String errorMessage,
            Instant createdAt) {
        NotificationLogEntity e = new NotificationLogEntity();
        e.id = id;
        e.companyId = companyId;
        e.channel = channel;
        e.recipient = recipient;
        e.subject = subject;
        e.eventType = eventType;
        e.status = status;
        e.errorMessage = errorMessage;
        e.createdAt = createdAt;
        return e;
    }
}
