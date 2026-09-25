package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.redis")
public record FormsRedisProperties(String keyPrefix) {}
