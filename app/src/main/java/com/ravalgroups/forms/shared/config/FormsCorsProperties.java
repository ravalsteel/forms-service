package com.ravalgroups.forms.shared.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.cors")
public record FormsCorsProperties(
        String allowedOrigins, String allowedMethods, String allowedHeaders, boolean allowCredentials) {

    public List<String> originList() {
        return split(allowedOrigins);
    }

    public List<String> methodList() {
        return split(allowedMethods);
    }

    public List<String> headerList() {
        return split(allowedHeaders);
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
