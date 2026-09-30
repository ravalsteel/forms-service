package com.ravalgroups.forms.run.adapter.out.persistence;

import com.ravalgroups.forms.run.domain.AudienceRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "form_run_audience_rule")
public class FormRunAudienceRuleEntity {

    @Id
    private UUID id;

    @Column(name = "form_run_id", nullable = false)
    private UUID formRunId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 32)
    private AudienceRuleType ruleType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FormRunAudienceRuleEntity() {}

    public static FormRunAudienceRuleEntity create(
            UUID id,
            UUID formRunId,
            UUID companyId,
            AudienceRuleType ruleType,
            UUID targetId,
            Instant now) {
        FormRunAudienceRuleEntity e = new FormRunAudienceRuleEntity();
        e.id = id;
        e.formRunId = formRunId;
        e.companyId = companyId;
        e.ruleType = ruleType;
        e.targetId = targetId;
        e.createdAt = now;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFormRunId() {
        return formRunId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public AudienceRuleType getRuleType() {
        return ruleType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
