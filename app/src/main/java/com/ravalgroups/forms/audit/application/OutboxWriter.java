package com.ravalgroups.forms.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.audit.adapter.out.persistence.OutboxEventEntity;
import com.ravalgroups.forms.audit.adapter.out.persistence.OutboxEventJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxWriter {

    private final OutboxEventJpaRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventJpaRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueue(
            String eventType, String aggregateType, UUID aggregateId, String routingKey, Map<String, Object> payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new DomainException("INTERNAL_ERROR", "Failed to serialize outbox payload");
        }
        outbox.save(new OutboxEventEntity(
                UuidV7.create(), eventType, aggregateType, aggregateId, routingKey, json, Instant.now()));
    }
}
