package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.role-bridge")
public record FormsRoleBridgeProperties(String secret) {

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }
}
