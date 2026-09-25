package com.ravalgroups.forms.form.adapter.out.persistence;

import com.ravalgroups.forms.form.domain.FormVersionStatus;
import com.ravalgroups.forms.shared.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "form_version")
public class FormVersionEntity {

    @Id
    private UUID id;

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Version
    @Column(nullable = false)
    private long revision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FormVersionStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected FormVersionEntity() {}

    public static FormVersionEntity createDraft(
            UUID id,
            UUID formId,
            int versionNumber,
            String definitionJson,
            UUID createdBy,
            Instant now) {
        FormVersionEntity e = new FormVersionEntity();
        e.id = id;
        e.formId = formId;
        e.versionNumber = versionNumber;
        e.revision = 0L;
        e.definitionJson = definitionJson;
        e.status = FormVersionStatus.DRAFT;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void updateDraft(String definitionJson, long expectedRevision, Instant now) {
        requireDraftEditable();
        if (this.revision != expectedRevision) {
            throw new DomainException(
                    "FORM_VERSION_CONCURRENT_MODIFICATION",
                    "expectedRevision does not match current revision " + this.revision);
        }
        this.definitionJson = definitionJson;
        this.updatedAt = now;
    }

    public void publish(UUID publishedBy, Instant now) {
        requireDraftEditable();
        this.status = FormVersionStatus.PUBLISHED;
        this.publishedBy = publishedBy;
        this.publishedAt = now;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (this.status == FormVersionStatus.ARCHIVED) {
            return;
        }
        this.status = FormVersionStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public void requireDraftEditable() {
        if (this.status != FormVersionStatus.DRAFT) {
            throw new DomainException("FORM_VERSION_NOT_EDITABLE", "Only DRAFT versions can be modified");
        }
    }

    public UUID getId() { return id; }
    public UUID getFormId() { return formId; }
    public int getVersionNumber() { return versionNumber; }
    public long getRevision() { return revision; }
    public String getDefinitionJson() { return definitionJson; }
    public FormVersionStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getPublishedBy() { return publishedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
