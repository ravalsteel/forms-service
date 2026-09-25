package com.ravalgroups.forms.file.adapter.out;

import com.ravalgroups.forms.shared.config.FormsFileStorageProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "forms.file-storage.provider", havingValue = "r2")
class R2ClientConfiguration {

    @Bean(destroyMethod = "close")
    S3Client r2S3Client(FormsFileStorageProperties properties) {
        FormsFileStorageProperties.R2 r2 = requireR2(properties);
        AwsBasicCredentials credentials = AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey());
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint(r2)))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    static FormsFileStorageProperties.R2 requireR2(FormsFileStorageProperties properties) {
        FormsFileStorageProperties.R2 r2 = properties.r2();
        if (r2 == null
                || isBlank(r2.accessKeyId())
                || isBlank(r2.secretAccessKey())
                || isBlank(r2.bucket())
                || (isBlank(r2.accountId()) && isBlank(r2.endpoint()))) {
            throw new IllegalStateException(
                    "R2 storage requires R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, R2_BUCKET, and R2_ACCOUNT_ID or R2_ENDPOINT");
        }
        return r2;
    }

    static String endpoint(FormsFileStorageProperties.R2 r2) {
        if (!isBlank(r2.endpoint())) {
            return r2.endpoint().trim();
        }
        return "https://" + r2.accountId().trim() + ".r2.cloudflarestorage.com";
    }

    static String objectPrefix(FormsFileStorageProperties properties) {
        FormsFileStorageProperties.R2 r2 = properties.r2();
        String prefix = r2 != null && r2.keyPrefix() != null && !r2.keyPrefix().isBlank()
                ? r2.keyPrefix().trim()
                : (properties.keyPrefix() == null || properties.keyPrefix().isBlank()
                        ? "forms/"
                        : properties.keyPrefix().trim());
        if (prefix.isEmpty()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
