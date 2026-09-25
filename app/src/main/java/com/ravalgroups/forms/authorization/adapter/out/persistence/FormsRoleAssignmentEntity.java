package com.ravalgroups.forms.authorization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forms_role_assignment")
public class FormsRoleAssignmentEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "iam_user_id", nullable = false)
    private UUID iamUserId;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FormsRoleAssignmentEntity() {}

    public static FormsRoleAssignmentEntity create(
            UUID id, UUID companyId, UUID iamUserId, String roleCode, Instant createdAt) {
        FormsRoleAssignmentEntity e = new FormsRoleAssignmentEntity();
        e.id = id;
        e.companyId = companyId;
        e.iamUserId = iamUserId;
        e.roleCode = roleCode;
        e.createdAt = createdAt;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getIamUserId() {
        return iamUserId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
