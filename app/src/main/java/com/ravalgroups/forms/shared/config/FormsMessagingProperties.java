package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.messaging")
public record FormsMessagingProperties(String domainEventsExchange, String iamProjectionQueue) {}
