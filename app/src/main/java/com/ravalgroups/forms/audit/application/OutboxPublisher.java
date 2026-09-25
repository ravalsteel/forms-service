package com.ravalgroups.forms.audit.application;

import com.ravalgroups.forms.audit.adapter.out.persistence.OutboxEventEntity;
import com.ravalgroups.forms.audit.adapter.out.persistence.OutboxEventJpaRepository;
import com.ravalgroups.forms.shared.config.FormsMessagingProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventJpaRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final FormsMessagingProperties messaging;

    public OutboxPublisher(
            OutboxEventJpaRepository outbox,
            RabbitTemplate rabbitTemplate,
            FormsMessagingProperties messaging) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.messaging = messaging;
    }

    @Scheduled(fixedDelayString = "${forms.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishBatch() {
        var unpublished = outbox.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEventEntity event : unpublished) {
            try {
                rabbitTemplate.convertAndSend(
                        messaging.domainEventsExchange(), event.getRoutingKey(), event.getPayload());
                event.markPublished(Instant.now());
                outbox.save(event);
            } catch (Exception ex) {
                log.warn("Outbox publish failed id={}: {}", event.getId(), ex.getMessage());
                event.markFailed(ex.getMessage());
                outbox.save(event);
            }
        }
    }
}
