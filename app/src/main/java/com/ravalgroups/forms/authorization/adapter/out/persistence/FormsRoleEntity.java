package com.ravalgroups.forms.authorization.adapter.out.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "forms_role")
public class FormsRoleEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "forms_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false, length = 128)
    private Set<String> permissionCodes = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FormsRoleEntity() {}

    public static FormsRoleEntity create(
            UUID id,
            UUID companyId,
            String code,
            String name,
            String description,
            boolean systemDefined,
            Set<String> permissionCodes,
            Instant now) {
        FormsRoleEntity e = new FormsRoleEntity();
        e.id = id;
        e.companyId = companyId;
        e.code = code;
        e.name = name;
        e.description = description;
        e.systemDefined = systemDefined;
        e.replacePermissions(permissionCodes);
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void updateMetadata(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        this.updatedAt = now;
    }

    public void replacePermissions(Set<String> codes, Instant now) {
        replacePermissions(codes);
        this.updatedAt = now;
    }

    private void replacePermissions(Set<String> codes) {
        this.permissionCodes.clear();
        if (codes != null) {
            this.permissionCodes.addAll(codes);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemDefined() {
        return systemDefined;
    }

    public Set<String> getPermissionCodes() {
        return Set.copyOf(permissionCodes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
