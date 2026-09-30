package com.ravalgroups.forms.run.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormJpaRepository;
import com.ravalgroups.forms.form.application.FormAccessService;
import com.ravalgroups.forms.form.domain.FormAccessLevel;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunAudienceRuleEntity;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunAudienceRuleJpaRepository;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunJpaRepository;
import com.ravalgroups.forms.run.domain.AudienceRuleType;
import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunAudienceService {

    private final FormRunAudienceRuleJpaRepository rules;
    private final FormRunJpaRepository runs;
    private final FormJpaRepository forms;
    private final FormAccessService formAccess;
    private final FormsAuthorizationService authz;
    private final IamMembershipProjectionJpaRepository memberships;
    private final ResponseJpaRepository responses;

    public RunAudienceService(
            FormRunAudienceRuleJpaRepository rules,
            FormRunJpaRepository runs,
            FormJpaRepository forms,
            FormAccessService formAccess,
            FormsAuthorizationService authz,
            IamMembershipProjectionJpaRepository memberships,
            ResponseJpaRepository responses) {
        this.rules = rules;
        this.runs = runs;
        this.forms = forms;
        this.formAccess = formAccess;
        this.authz = authz;
        this.memberships = memberships;
        this.responses = responses;
    }

    @Transactional(readOnly = true)
    public List<AudienceRuleView> listRules(UUID formRunId) {
        return rules.findByFormRunIdOrderByCreatedAtAsc(formRunId).stream()
                .map(this::toRuleView)
                .toList();
    }

    @Transactional
    public AudienceView replaceAudience(CurrentUser actor, UUID runId, List<AudienceRuleInput> inputs) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = runs.findByIdAndCompanyId(runId, actor.companyId())
                .orElseThrow(() -> new DomainException("FORM_RUN_NOT_FOUND", "Form run not found"));
        formAccess.requireFormAccess(actor, run.getFormId(), FormAccessLevel.MANAGE);
        if (run.getRespondentMode() == RespondentMode.ANONYMOUS) {
            throw new DomainException(
                    "VALIDATION_ERROR", "Audience rules do not apply to anonymous (public) collections");
        }

        List<AudienceRuleInput> normalized = normalizeInputs(inputs);
        Instant now = Instant.now();
        rules.deleteByFormRunId(runId);
        for (AudienceRuleInput input : normalized) {
            rules.save(FormRunAudienceRuleEntity.create(
                    UuidV7.create(),
                    run.getId(),
                    run.getCompanyId(),
                    input.type(),
                    input.targetId(),
                    now));
        }
        return audienceView(run);
    }

    @Transactional(readOnly = true)
    public AudienceView audienceView(FormRunEntity run) {
        return new AudienceView(run.isAudienceRequired(), listRules(run.getId()));
    }

    public void requireConfiguredForOpen(FormRunEntity run) {
        if (!run.isAudienceRequired() || run.getRespondentMode() == RespondentMode.ANONYMOUS) {
            return;
        }
        if (rules.countByFormRunId(run.getId()) == 0) {
            throw new DomainException(
                    "AUDIENCE_REQUIRED", "Configure an audience before opening this collection");
        }
    }

    public void requireEligible(CurrentUser actor, FormRunEntity run) {
        if (!run.isAudienceRequired() || run.getRespondentMode() == RespondentMode.ANONYMOUS) {
            return;
        }
        if (!isEligible(actor, run)) {
            throw new DomainException("AUDIENCE_FORBIDDEN", "You are not in the audience for this collection");
        }
    }

    public boolean isEligible(CurrentUser actor, FormRunEntity run) {
        if (!run.isAudienceRequired() || run.getRespondentMode() == RespondentMode.ANONYMOUS) {
            return true;
        }
        List<FormRunAudienceRuleEntity> runRules = rules.findByFormRunIdOrderByCreatedAtAsc(run.getId());
        if (runRules.isEmpty()) {
            return false;
        }
        Optional<IamMembershipProjectionEntity> membership =
                memberships.findByCompanyIdAndIamUserId(run.getCompanyId(), actor.userId());
        if (membership.isEmpty() || !isActiveMembership(membership.get())) {
            return false;
        }
        return matchesAny(membership.get(), runRules);
    }

    @Transactional(readOnly = true)
    public List<MyCollectionView> listMyCollections(CurrentUser actor) {
        authz.requireAssigned(actor);
        List<FormRunEntity> openRuns =
                runs.findByCompanyIdAndStatusOrderByCreatedAtDesc(actor.companyId(), FormRunStatus.OPEN);
        if (openRuns.isEmpty()) {
            return List.of();
        }

        List<FormRunEntity> eligible = openRuns.stream()
                .filter(run -> run.getRespondentMode() != RespondentMode.ANONYMOUS)
                .filter(run -> isEligible(actor, run))
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }

        Map<UUID, FormEntity> formById = forms
                .findAllById(eligible.stream().map(FormRunEntity::getFormId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(FormEntity::getId, f -> f));

        List<UUID> runIds = eligible.stream().map(FormRunEntity::getId).toList();
        Map<UUID, ResponseEntity> responseByRun = responses
                .findByFormRunIdInAndRespondentId(runIds, actor.userId())
                .stream()
                .collect(Collectors.toMap(
                        ResponseEntity::getFormRunId,
                        r -> r,
                        (a, b) -> a.getStartedAt().isAfter(b.getStartedAt()) ? a : b));

        List<MyCollectionView> out = new ArrayList<>();
        for (FormRunEntity run : eligible) {
            FormEntity form = formById.get(run.getFormId());
            ResponseEntity response = responseByRun.get(run.getId());
            out.add(new MyCollectionView(
                    run.getId(),
                    run.getFormId(),
                    run.getFormVersionId(),
                    form == null ? run.getName() : form.getName(),
                    run.getName(),
                    run.getStatus().name(),
                    run.getRespondentMode().name(),
                    run.getOpensAt(),
                    run.getClosesAt(),
                    response == null ? null : response.getId(),
                    response == null ? null : response.getStatus().name()));
        }
        return out;
    }

    private List<AudienceRuleInput> normalizeInputs(List<AudienceRuleInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Map<String, AudienceRuleInput> dedup = new LinkedHashMap<>();
        for (AudienceRuleInput raw : inputs) {
            if (raw == null || raw.type() == null) {
                throw new DomainException("VALIDATION_ERROR", "Audience rule type is required");
            }
            AudienceRuleType type = raw.type();
            UUID targetId = raw.targetId();
            if (type == AudienceRuleType.ALL_COMPANY) {
                if (targetId != null) {
                    throw new DomainException("VALIDATION_ERROR", "ALL_COMPANY rules must not include targetId");
                }
            } else if (targetId == null) {
                throw new DomainException("VALIDATION_ERROR", type + " rules require targetId");
            }
            String key = type.name() + ":" + (targetId == null ? "-" : targetId);
            dedup.put(key, new AudienceRuleInput(type, targetId));
        }
        return new ArrayList<>(dedup.values());
    }

    private boolean matchesAny(IamMembershipProjectionEntity membership, List<FormRunAudienceRuleEntity> runRules) {
        for (FormRunAudienceRuleEntity rule : runRules) {
            switch (rule.getRuleType()) {
                case ALL_COMPANY -> {
                    return true;
                }
                case DEPARTMENT -> {
                    if (Objects.equals(membership.getDepartmentId(), rule.getTargetId())) {
                        return true;
                    }
                }
                case SUB_DEPARTMENT -> {
                    if (Objects.equals(membership.getSubDepartmentId(), rule.getTargetId())) {
                        return true;
                    }
                }
                case USER -> {
                    if (Objects.equals(membership.getIamUserId(), rule.getTargetId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isActiveMembership(IamMembershipProjectionEntity membership) {
        String status = membership.getStatus();
        if (status == null || status.isBlank()) {
            return true;
        }
        return "ACTIVE".equalsIgnoreCase(status.trim());
    }

    private AudienceRuleView toRuleView(FormRunAudienceRuleEntity e) {
        return new AudienceRuleView(e.getId(), e.getRuleType().name(), e.getTargetId());
    }

    public record AudienceRuleInput(AudienceRuleType type, UUID targetId) {}

    public record AudienceRuleView(UUID id, String type, UUID targetId) {}

    public record AudienceView(boolean required, List<AudienceRuleView> rules) {}

    public record MyCollectionView(
            UUID runId,
            UUID formId,
            UUID formVersionId,
            String formName,
            String runName,
            String status,
            String respondentMode,
            Instant opensAt,
            Instant closesAt,
            UUID responseId,
            String responseStatus) {}
}
