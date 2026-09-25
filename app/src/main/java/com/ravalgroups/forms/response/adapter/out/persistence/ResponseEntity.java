package com.ravalgroups.forms.response.adapter.out.persistence;

import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.shared.exception.DomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "response")
public class ResponseEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_run_id", nullable = false)
    private UUID formRunId;

    @Column(name = "form_version_id", nullable = false)
    private UUID formVersionId;

    @Column(name = "respondent_id")
    private UUID respondentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "respondent_mode", nullable = false, length = 32)
    private RespondentMode respondentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResponseStatus status;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ResponseAnswerEntity> answers = new ArrayList<>();

    protected ResponseEntity() {}

    public static ResponseEntity start(
            UUID id,
            UUID companyId,
            UUID formRunId,
            UUID formVersionId,
            UUID respondentId,
            RespondentMode respondentMode,
            String idempotencyKey,
            Instant now) {
        ResponseEntity e = new ResponseEntity();
        e.id = id;
        e.companyId = companyId;
        e.formRunId = formRunId;
        e.formVersionId = formVersionId;
        e.respondentId = respondentId;
        e.respondentMode = respondentMode;
        e.status = ResponseStatus.IN_PROGRESS;
        e.revision = 0L;
        e.idempotencyKey = idempotencyKey;
        e.startedAt = now;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void requireInProgress() {
        if (status != ResponseStatus.IN_PROGRESS) {
            throw new DomainException("RESPONSE_ALREADY_SUBMITTED", "Response is already submitted");
        }
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void submit(Instant now) {
        requireInProgress();
        this.status = ResponseStatus.SUBMITTED;
        this.submittedAt = now;
        this.updatedAt = now;
    }

    public void anonymize(Instant now) {
        this.respondentId = null;
        this.anonymizedAt = now;
        this.updatedAt = now;
        for (ResponseAnswerEntity answer : answers) {
            answer.scrubPii(now);
        }
    }

    public void replaceAnswers(List<ResponseAnswerEntity> next) {
        this.answers.clear();
        for (ResponseAnswerEntity answer : next) {
            answer.setResponse(this);
            this.answers.add(answer);
        }
    }

    public void clearAnswers() {
        this.answers.clear();
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getFormRunId() { return formRunId; }
    public UUID getFormVersionId() { return formVersionId; }
    public UUID getRespondentId() { return respondentId; }
    public RespondentMode getRespondentMode() { return respondentMode; }
    public ResponseStatus getStatus() { return status; }
    public long getRevision() { return revision; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAnonymizedAt() { return anonymizedAt; }
    public List<ResponseAnswerEntity> getAnswers() { return answers; }
}
