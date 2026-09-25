package com.ravalgroups.forms.run.adapter.out.persistence;

import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_run")
public class FormRunEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "form_version_id", nullable = false)
    private UUID formVersionId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FormRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "respondent_mode", nullable = false, length = 32)
    private RespondentMode respondentMode;

    @Column(name = "opens_at")
    private Instant opensAt;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "min_aggregation_threshold", nullable = false)
    private int minAggregationThreshold;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FormRunEntity() {}

    public static FormRunEntity create(
            UUID id,
            UUID companyId,
            UUID formId,
            UUID formVersionId,
            String name,
            FormRunStatus status,
            RespondentMode respondentMode,
            Instant opensAt,
            Instant closesAt,
            int minAggregationThreshold,
            UUID createdBy,
            Instant now) {
        FormRunEntity e = new FormRunEntity();
        e.id = id;
        e.companyId = companyId;
        e.formId = formId;
        e.formVersionId = formVersionId;
        e.name = name;
        e.status = status;
        e.respondentMode = respondentMode;
        e.opensAt = opensAt;
        e.closesAt = closesAt;
        e.minAggregationThreshold = minAggregationThreshold;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void open(Instant now) {
        if (status == FormRunStatus.CANCELLED || status == FormRunStatus.CLOSED) {
            throw new DomainException("INVALID_STATE", "Cannot open a " + status + " run");
        }
        this.status = FormRunStatus.OPEN;
        this.updatedAt = now;
    }

    public void close(Instant now) {
        if (status != FormRunStatus.OPEN && status != FormRunStatus.SCHEDULED) {
            throw new DomainException("INVALID_STATE", "Cannot close a " + status + " run");
        }
        this.status = FormRunStatus.CLOSED;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        if (status == FormRunStatus.CLOSED) {
            throw new DomainException("INVALID_STATE", "Cannot cancel a CLOSED run");
        }
        this.status = FormRunStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void requireOpen() {
        if (status != FormRunStatus.OPEN) {
            throw new DomainException("FORM_RUN_NOT_OPEN", "Form run is not open");
        }
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getFormId() { return formId; }
    public UUID getFormVersionId() { return formVersionId; }
    public String getName() { return name; }
    public FormRunStatus getStatus() { return status; }
    public RespondentMode getRespondentMode() { return respondentMode; }
    public Instant getOpensAt() { return opensAt; }
    public Instant getClosesAt() { return closesAt; }
    public int getMinAggregationThreshold() { return minAggregationThreshold; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
