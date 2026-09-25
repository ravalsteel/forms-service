package com.ravalgroups.forms.file.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
public class StoredFileEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "storage_key", nullable = false, length = 256, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredFileEntity() {}

    public static StoredFileEntity create(
            UUID id,
            UUID companyId,
            String storageKey,
            String contentType,
            long sizeBytes,
            String originalFilename,
            UUID createdBy,
            Instant createdAt) {
        StoredFileEntity e = new StoredFileEntity();
        e.id = id;
        e.companyId = companyId;
        e.storageKey = storageKey;
        e.contentType = contentType;
        e.sizeBytes = sizeBytes;
        e.originalFilename = originalFilename;
        e.createdBy = createdBy;
        e.createdAt = createdAt;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
