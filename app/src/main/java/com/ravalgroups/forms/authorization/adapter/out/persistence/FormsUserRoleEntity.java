package com.ravalgroups.forms.authorization.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forms_user_role")
public class FormsUserRoleEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "iam_user_id", nullable = false)
    private UUID iamUserId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FormsUserRoleEntity() {}

    public static FormsUserRoleEntity create(
            UUID id, UUID companyId, UUID iamUserId, UUID roleId, Instant createdAt) {
        FormsUserRoleEntity e = new FormsUserRoleEntity();
        e.id = id;
        e.companyId = companyId;
        e.iamUserId = iamUserId;
        e.roleId = roleId;
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

    public UUID getRoleId() {
        return roleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
