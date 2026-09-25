package com.ravalgroups.forms.export.adapter.out.persistence;

import com.ravalgroups.forms.export.domain.ExportJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_job")
public class ExportJobEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_run_id", nullable = false)
    private UUID formRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExportJobStatus status;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "object_id")
    private UUID objectId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected ExportJobEntity() {}

    public static ExportJobEntity create(
            UUID id, UUID companyId, UUID formRunId, String format, UUID createdBy, Instant now) {
        ExportJobEntity e = new ExportJobEntity();
        e.id = id;
        e.companyId = companyId;
        e.formRunId = formRunId;
        e.status = ExportJobStatus.PENDING;
        e.format = format;
        e.createdBy = createdBy;
        e.createdAt = now;
        return e;
    }

    public void markProcessing() {
        this.status = ExportJobStatus.PROCESSING;
    }

    public void complete(UUID objectId, Instant now, Instant expiresAt) {
        this.status = ExportJobStatus.COMPLETED;
        this.objectId = objectId;
        this.completedAt = now;
        this.expiresAt = expiresAt;
        this.errorMessage = null;
    }

    public void fail(String message, Instant now) {
        this.status = ExportJobStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getFormRunId() { return formRunId; }
    public ExportJobStatus getStatus() { return status; }
    public String getFormat() { return format; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getObjectId() { return objectId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
