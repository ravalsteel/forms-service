package com.ravalgroups.forms.shared.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionRuntimeGuard {

    public ProductionRuntimeGuard(
            Environment environment,
            FormsCorsProperties cors,
            FormsNotificationProperties notification) {
        if (!environment.matchesProfiles("production")) {
            return;
        }
        if (cors.originList().isEmpty()) {
            throw new IllegalStateException("Production requires CORS_ALLOWED_ORIGINS to be configured");
        }
        if (notification.smtpEnabled()
                && (notification.from() == null || notification.from().isBlank())) {
            throw new IllegalStateException(
                    "Production SMTP email requires forms.notification.email.from (NOTIFICATION_EMAIL_FROM)");
        }
    }
}
