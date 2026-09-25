package com.ravalgroups.forms.template.adapter.out.persistence;

import com.ravalgroups.forms.template.domain.TemplateScope;
import com.ravalgroups.forms.template.domain.TemplateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "form_template")
public class FormTemplateEntity {

    @Id
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TemplateScope scope;

    @Column(nullable = false, length = 255)
    private String name;

    @Column
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_json", nullable = false, columnDefinition = "jsonb")
    private String definitionJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TemplateStatus status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FormTemplateEntity() {}

    public static FormTemplateEntity create(
            UUID id,
            UUID companyId,
            TemplateScope scope,
            String name,
            String description,
            String definitionJson,
            UUID createdBy,
            Instant now) {
        FormTemplateEntity e = new FormTemplateEntity();
        e.id = id;
        e.companyId = companyId;
        e.scope = scope;
        e.name = name;
        e.description = description;
        e.definitionJson = definitionJson;
        e.status = TemplateStatus.ACTIVE;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void update(String name, String description, String definitionJson, Instant now) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
        if (description != null) {
            this.description = description;
        }
        if (definitionJson != null) {
            this.definitionJson = definitionJson;
        }
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = TemplateStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCompanyId() { return companyId; }
    public TemplateScope getScope() { return scope; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDefinitionJson() { return definitionJson; }
    public TemplateStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
