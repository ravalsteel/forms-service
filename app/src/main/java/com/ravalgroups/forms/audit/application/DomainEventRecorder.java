package com.ravalgroups.forms.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.audit.adapter.out.persistence.FormsAuditEventEntity;
import com.ravalgroups.forms.audit.adapter.out.persistence.FormsAuditEventJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a local audit row and enqueues a matching outbox domain event.
 */
@Service
public class DomainEventRecorder {

    private final FormsAuditEventJpaRepository auditEvents;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    public DomainEventRecorder(
            FormsAuditEventJpaRepository auditEvents, OutboxWriter outbox, ObjectMapper objectMapper) {
        this.auditEvents = auditEvents;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            UUID companyId,
            UUID actorUserId,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String routingKey,
            Map<String, Object> payloadFields) {
        Instant occurredAt = Instant.now();
        UUID eventId = UuidV7.create();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("eventType", eventType);
        payload.put("companyId", companyId.toString());
        payload.put("aggregateType", aggregateType);
        payload.put("aggregateId", aggregateId.toString());
        payload.put("occurredAt", occurredAt.toString());
        if (actorUserId != null) {
            payload.put("actorUserId", actorUserId.toString());
        }
        if (payloadFields != null) {
            payloadFields.forEach(payload::putIfAbsent);
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new DomainException("INTERNAL_ERROR", "Failed to serialize audit payload");
        }

        auditEvents.save(new FormsAuditEventEntity(
                eventId, companyId, eventType, aggregateType, aggregateId, actorUserId, json, occurredAt));
        outbox.enqueue(eventType, aggregateType, aggregateId, routingKey, payload);
    }
}
