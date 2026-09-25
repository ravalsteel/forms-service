package com.ravalgroups.forms.file.adapter.out;

import com.ravalgroups.forms.file.application.port.FileStoragePort;
import com.ravalgroups.forms.shared.config.FormsFileStorageProperties;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "forms.file-storage.provider", havingValue = "r2")
public class R2FileStorage implements FileStoragePort {

    private final S3Client s3;
    private final String bucket;
    private final String prefix;

    public R2FileStorage(S3Client r2S3Client, FormsFileStorageProperties properties) {
        FormsFileStorageProperties.R2 r2 = R2ClientConfiguration.requireR2(properties);
        this.s3 = r2S3Client;
        this.bucket = r2.bucket().trim();
        this.prefix = R2ClientConfiguration.objectPrefix(properties);
    }

    @Override
    public StoredFile store(String contentType, InputStream content, long contentLength) throws IOException {
        if (contentLength <= 0) {
            throw new IOException("Content length is required for R2 uploads");
        }
        String storageKey = UuidV7.create().toString();
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey(storageKey))
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build(),
                    RequestBody.fromInputStream(content, contentLength));
        } catch (S3Exception ex) {
            throw new IOException("Failed to store file in R2", ex);
        }
        return new StoredFile(storageKey, contentType, contentLength);
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        try {
            return s3.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(storageKey))
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new IOException("File not found in R2", ex);
        } catch (S3Exception ex) {
            throw new IOException("Failed to open file in R2", ex);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(storageKey))
                    .build());
        } catch (S3Exception ex) {
            throw new IOException("Failed to delete file in R2", ex);
        }
    }

    private String objectKey(String storageKey) throws IOException {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("/") || storageKey.contains("\\")) {
            throw new IOException("Invalid storage key");
        }
        return prefix + storageKey;
    }
}
