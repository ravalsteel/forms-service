package com.ravalgroups.forms.run.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.application.FormApplicationService;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunJpaRepository;
import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.config.FormsReportingProperties;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormRunApplicationService {

    private final FormRunJpaRepository runs;
    private final FormApplicationService forms;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;
    private final FormsReportingProperties reportingProperties;

    public FormRunApplicationService(
            FormRunJpaRepository runs,
            FormApplicationService forms,
            FormsAuthorizationService authz,
            DomainEventRecorder events,
            FormsReportingProperties reportingProperties) {
        this.runs = runs;
        this.forms = forms;
        this.authz = authz;
        this.events = events;
        this.reportingProperties = reportingProperties;
    }

    @Transactional(readOnly = true)
    public List<RunView> listForForm(CurrentUser actor, UUID formId) {
        authz.requireAssigned(actor);
        forms.requireForm(actor, formId);
        return runs.findByCompanyIdAndFormIdOrderByCreatedAtDesc(actor.companyId(), formId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public RunView get(CurrentUser actor, UUID runId) {
        authz.requireAssigned(actor);
        return toView(requireRun(actor, runId));
    }

    @Transactional
    public RunView create(CurrentUser actor, UUID formId, CreateRunCommand command) {
        authz.requirePublisherOrAdmin(actor);
        forms.requireForm(actor, formId);
        if (command.formVersionId() == null) {
            throw new DomainException("VALIDATION_ERROR", "formVersionId is required");
        }
        if (command.name() == null || command.name().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "name is required");
        }
        FormVersionEntity version = forms.requirePublishedVersion(formId, command.formVersionId());
        RespondentMode mode = command.respondentMode() == null
                ? RespondentMode.IDENTIFIED
                : command.respondentMode();
        FormRunStatus status = command.status() == null ? FormRunStatus.SCHEDULED : command.status();
        int threshold = command.minAggregationThreshold() == null
                ? reportingProperties.defaultMinAggregationThreshold()
                : command.minAggregationThreshold();
        Instant now = Instant.now();
        FormRunEntity run = runs.save(FormRunEntity.create(
                UuidV7.create(),
                actor.companyId(),
                formId,
                version.getId(),
                command.name().trim(),
                status,
                mode,
                command.opensAt(),
                command.closesAt(),
                threshold,
                actor.userId(),
                now));
        Map<String, Object> payload = runPayload(run);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.run.created",
                "FormRun",
                run.getId(),
                "forms.run.created",
                payload);
        return toView(run);
    }

    @Transactional
    public RunView open(CurrentUser actor, UUID runId) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = requireRun(actor, runId);
        run.open(Instant.now());
        runs.save(run);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.run.opened",
                "FormRun",
                run.getId(),
                "forms.run.opened",
                runPayload(run));
        return toView(run);
    }

    @Transactional
    public RunView close(CurrentUser actor, UUID runId) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = requireRun(actor, runId);
        run.close(Instant.now());
        runs.save(run);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.run.closed",
                "FormRun",
                run.getId(),
                "forms.run.closed",
                runPayload(run));
        return toView(run);
    }

    @Transactional
    public RunView cancel(CurrentUser actor, UUID runId) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = requireRun(actor, runId);
        run.cancel(Instant.now());
        runs.save(run);
        return toView(run);
    }

    public FormRunEntity requireRun(CurrentUser actor, UUID runId) {
        FormRunEntity run = runs.findByIdAndCompanyId(runId, actor.companyId())
                .orElseThrow(() -> new DomainException("FORM_RUN_NOT_FOUND", "Form run not found"));
        authz.requireCompany(actor, run.getCompanyId());
        return run;
    }

    private Map<String, Object> runPayload(FormRunEntity run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formRunId", run.getId().toString());
        payload.put("formId", run.getFormId().toString());
        payload.put("formVersionId", run.getFormVersionId().toString());
        payload.put("status", run.getStatus().name());
        payload.put("respondentMode", run.getRespondentMode().name());
        return payload;
    }

    private RunView toView(FormRunEntity e) {
        return new RunView(
                e.getId(),
                e.getCompanyId(),
                e.getFormId(),
                e.getFormVersionId(),
                e.getName(),
                e.getStatus().name(),
                e.getRespondentMode().name(),
                e.getOpensAt(),
                e.getClosesAt(),
                e.getMinAggregationThreshold(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public record RunView(
            UUID id,
            UUID companyId,
            UUID formId,
            UUID formVersionId,
            String name,
            String status,
            String respondentMode,
            Instant opensAt,
            Instant closesAt,
            int minAggregationThreshold,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateRunCommand(
            UUID formVersionId,
            String name,
            RespondentMode respondentMode,
            FormRunStatus status,
            Instant opensAt,
            Instant closesAt,
            Integer minAggregationThreshold) {}
}
