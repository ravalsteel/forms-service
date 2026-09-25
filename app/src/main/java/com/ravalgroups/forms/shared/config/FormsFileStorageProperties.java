package com.ravalgroups.forms.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forms.file-storage")
public record FormsFileStorageProperties(String provider, String root, String keyPrefix, R2 r2) {

    public record R2(
            String accountId,
            String accessKeyId,
            String secretAccessKey,
            String bucket,
            String endpoint,
            String keyPrefix) {}
}
