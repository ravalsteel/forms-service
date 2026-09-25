package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.iam")
public record FormsIamProperties(
        String issuer,
        String jwksUri,
        String baseUrl,
        String audience,
        String applicationCode,
        /** Optional machine bearer for outbound IAM admin APIs (reconcile). Prefer short-lived tokens. */
        String serviceBearerToken) {}
