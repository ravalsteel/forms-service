package com.ravalgroups.forms.iam.adapter.in.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class IamDomainEventListener {

    private final IamProjectionEventProcessor processor;

    public IamDomainEventListener(IamProjectionEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = "${forms.messaging.iam-projection-queue}")
    public void onMessage(
            @Payload String payload,
            @Header(name = "amqp_receivedRoutingKey", required = false) String routingKey,
            @Header(name = "event_id", required = false) String eventId) {
        processor.process(eventId, routingKey, payload);
    }
}
