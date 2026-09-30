package com.ravalgroups.forms.form.adapter.out.persistence;

import com.ravalgroups.forms.form.domain.FormAccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_access")
public class FormAccessEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "iam_user_id", nullable = false)
    private UUID iamUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 16)
    private FormAccessLevel accessLevel;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FormAccessEntity() {}

    public static FormAccessEntity create(
            UUID id,
            UUID companyId,
            UUID formId,
            UUID iamUserId,
            FormAccessLevel accessLevel,
            UUID grantedBy,
            Instant now) {
        FormAccessEntity e = new FormAccessEntity();
        e.id = id;
        e.companyId = companyId;
        e.formId = formId;
        e.iamUserId = iamUserId;
        e.accessLevel = accessLevel;
        e.grantedBy = grantedBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void updateLevel(FormAccessLevel accessLevel, UUID grantedBy, Instant now) {
        this.accessLevel = accessLevel;
        this.grantedBy = grantedBy;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public UUID getFormId() { return formId; }
    public UUID getIamUserId() { return iamUserId; }
    public FormAccessLevel getAccessLevel() { return accessLevel; }
    public UUID getGrantedBy() { return grantedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
