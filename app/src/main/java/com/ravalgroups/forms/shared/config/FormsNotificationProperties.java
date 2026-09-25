package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.notification.email")
public record FormsNotificationProperties(
        String provider, String from, String fromName) {

    public boolean smtpEnabled() {
        return "smtp".equalsIgnoreCase(provider == null ? "log" : provider.trim());
    }
}
