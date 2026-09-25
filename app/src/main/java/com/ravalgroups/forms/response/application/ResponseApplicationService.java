package com.ravalgroups.forms.response.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.application.AnswerValidator.AnswerInput;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResponseApplicationService {

    private final ResponseJpaRepository responses;
    private final FormRunApplicationService runs;
    private final FormVersionJpaRepository versions;
    private final AnswerValidator answerValidator;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;
    private final EntityManager entityManager;

    public ResponseApplicationService(
            ResponseJpaRepository responses,
            FormRunApplicationService runs,
            FormVersionJpaRepository versions,
            AnswerValidator answerValidator,
            FormsAuthorizationService authz,
            DomainEventRecorder events,
            EntityManager entityManager) {
        this.responses = responses;
        this.runs = runs;
        this.versions = versions;
        this.answerValidator = answerValidator;
        this.authz = authz;
        this.events = events;
        this.entityManager = entityManager;
    }

    @Transactional
    public ResponseView start(
            CurrentUser actor, UUID runId, UUID clientResponseId, String idempotencyKey, List<AnswerInput> answers) {
        authz.requireAssigned(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        run.requireOpen();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = responses.findByFormRunIdAndIdempotencyKey(runId, idempotencyKey.trim());
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        UUID responseId = UuidV7.createOrUse(clientResponseId);
        UUID respondentId = resolveRespondentId(actor, run.getRespondentMode());
        Instant now = Instant.now();
        ResponseEntity entity = ResponseEntity.start(
                responseId,
                actor.companyId(),
                run.getId(),
                run.getFormVersionId(),
                respondentId,
                run.getRespondentMode(),
                idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim(),
                now);

        if (answers != null && !answers.isEmpty()) {
            FormVersionEntity version = versions
                    .findById(run.getFormVersionId())
                    .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
            List<ResponseAnswerEntity> mapped =
                    answerValidator.mapAnswers(version.getDefinitionJson(), answers, false, now);
            entity.replaceAnswers(mapped);
        }

        ResponseEntity saved = responses.save(entity);
        events.record(
                actor.companyId(),
                respondentId,
                "forms.response.started",
                "Response",
                saved.getId(),
                "forms.response.started",
                responsePayload(saved));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public ResponseView get(CurrentUser actor, UUID responseId) {
        authz.requireAssigned(actor);
        ResponseEntity entity = requireResponse(actor, responseId);
        authorizeRead(actor, entity);
        return toView(entity);
    }

    @Transactional
    public ResponseView autosave(CurrentUser actor, UUID responseId, List<AnswerInput> answers) {
        authz.requireAssigned(actor);
        ResponseEntity entity = requireResponse(actor, responseId);
        authorizeWrite(actor, entity);
        entity.requireInProgress();
        FormVersionEntity version = versions
                .findById(entity.getFormVersionId())
                .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
        Instant now = Instant.now();
        List<ResponseAnswerEntity> mapped =
                answerValidator.mapAnswers(version.getDefinitionJson(), answers, false, now);
        entity.clearAnswers();
        entityManager.flush();
        entity.replaceAnswers(mapped);
        entity.touch(now);
        return toView(responses.save(entity));
    }

    @Transactional
    public ResponseView submit(CurrentUser actor, UUID responseId, UUID expectedFormVersionId, List<AnswerInput> answers) {
        authz.requireAssigned(actor);
        ResponseEntity entity = requireResponse(actor, responseId);
        authorizeWrite(actor, entity);
        if (entity.getStatus() == ResponseStatus.SUBMITTED) {
            return toView(entity);
        }
        FormRunEntity run = runs.requireRun(actor, entity.getFormRunId());
        run.requireOpen();

        if (expectedFormVersionId != null && !expectedFormVersionId.equals(run.getFormVersionId())) {
            throw new DomainException(
                    "FORM_VERSION_MISMATCH", "Submitted formVersionId does not match the run's published version");
        }
        if (!entity.getFormVersionId().equals(run.getFormVersionId())) {
            throw new DomainException(
                    "FORM_VERSION_MISMATCH", "Response formVersionId does not match the run's published version");
        }

        FormVersionEntity version = versions
                .findById(run.getFormVersionId())
                .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
        Instant now = Instant.now();
        if (answers != null && !answers.isEmpty()) {
            List<ResponseAnswerEntity> mapped =
                    answerValidator.mapAnswers(version.getDefinitionJson(), answers, true, now);
            entity.replaceAnswers(mapped);
        } else {
            // Re-validate existing answers without recreating rows (avoids unique constraint races).
            List<AnswerInput> existing = entity.getAnswers().stream()
                    .map(a -> new AnswerInput(
                            a.getQuestionId(),
                            a.getQuestionKey(),
                            a.getTextValue(),
                            a.getNumberValue(),
                            a.getBooleanValue(),
                            a.getDateValue(),
                            a.getDatetimeValue(),
                            a.getJsonValue(),
                            a.getObjectId()))
                    .toList();
            answerValidator.mapAnswers(version.getDefinitionJson(), existing, true, now);
        }
        entity.submit(now);
        ResponseEntity saved = responses.save(entity);

        UUID actorForAudit = saved.getRespondentMode() == RespondentMode.ANONYMOUS ? null : actor.userId();
        events.record(
                actor.companyId(),
                actorForAudit,
                "forms.response.submitted",
                "Response",
                saved.getId(),
                "forms.response.submitted",
                responsePayload(saved));
        return toView(saved);
    }

    @Transactional
    public ResponseView anonymize(CurrentUser actor, UUID responseId) {
        authz.requireAdmin(actor);
        ResponseEntity entity = requireResponse(actor, responseId);
        if (entity.getAnonymizedAt() != null) {
            return toView(entity);
        }
        Instant now = Instant.now();
        entity.anonymize(now);
        ResponseEntity saved = responses.save(entity);
        events.record(
                actor.companyId(),
                null,
                "forms.response.anonymized",
                "Response",
                saved.getId(),
                "forms.response.anonymized",
                responsePayload(saved));
        return toView(saved);
    }

    private UUID resolveRespondentId(CurrentUser actor, RespondentMode mode) {
        return switch (mode) {
            case IDENTIFIED, PSEUDONYMOUS -> actor.userId();
            case ANONYMOUS -> null;
        };
    }

    private void authorizeRead(CurrentUser actor, ResponseEntity entity) {
        if (entity.getRespondentMode() == RespondentMode.ANONYMOUS) {
            if (authz.hasAnyRole(actor, com.ravalgroups.forms.authorization.FormsRoleCode.ANALYST_OR_ADMIN)
                    || authz.hasAnyRole(actor, com.ravalgroups.forms.authorization.FormsRoleCode.PUBLISHER_OR_ADMIN)) {
                return;
            }
            throw new DomainException("ANONYMOUS_RESPONSE_ACCESS_DENIED", "Cannot read anonymous response details");
        }
        if (actor.userId().equals(entity.getRespondentId())) {
            return;
        }
        if (authz.hasAnyRole(actor, com.ravalgroups.forms.authorization.FormsRoleCode.ANALYST_OR_ADMIN)
                || authz.hasAnyRole(actor, com.ravalgroups.forms.authorization.FormsRoleCode.PUBLISHER_OR_ADMIN)
                || authz.hasAnyRole(actor, com.ravalgroups.forms.authorization.FormsRoleCode.DESIGNER_OR_ADMIN)) {
            return;
        }
        throw new DomainException("FORBIDDEN", "Not permitted to read this response");
    }

    private void authorizeWrite(CurrentUser actor, ResponseEntity entity) {
        if (entity.getRespondentMode() == RespondentMode.ANONYMOUS) {
            return;
        }
        if (!actor.userId().equals(entity.getRespondentId())) {
            throw new DomainException("FORBIDDEN", "Only the respondent may update this response");
        }
    }

    private ResponseEntity requireResponse(CurrentUser actor, UUID responseId) {
        ResponseEntity entity = responses
                .findByIdAndCompanyId(responseId, actor.companyId())
                .orElseThrow(() -> new DomainException("RESPONSE_NOT_FOUND", "Response not found"));
        authz.requireCompany(actor, entity.getCompanyId());
        return entity;
    }

    private Map<String, Object> responsePayload(ResponseEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("responseId", entity.getId().toString());
        payload.put("formRunId", entity.getFormRunId().toString());
        payload.put("formVersionId", entity.getFormVersionId().toString());
        payload.put("status", entity.getStatus().name());
        payload.put("respondentMode", entity.getRespondentMode().name());
        return payload;
    }

    private ResponseView toView(ResponseEntity e) {
        List<AnswerView> answers = e.getAnswers().stream()
                .map(a -> new AnswerView(
                        a.getId(),
                        a.getQuestionId(),
                        a.getQuestionKey(),
                        a.getValueType(),
                        a.getTextValue(),
                        a.getNumberValue(),
                        a.getBooleanValue(),
                        a.getDateValue(),
                        a.getDatetimeValue(),
                        a.getJsonValue(),
                        a.getObjectId()))
                .toList();
        return new ResponseView(
                e.getId(),
                e.getCompanyId(),
                e.getFormRunId(),
                e.getFormVersionId(),
                e.getRespondentId(),
                e.getRespondentMode().name(),
                e.getStatus().name(),
                e.getRevision(),
                e.getIdempotencyKey(),
                e.getStartedAt(),
                e.getSubmittedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                answers);
    }

    public record ResponseView(
            UUID id,
            UUID companyId,
            UUID formRunId,
            UUID formVersionId,
            UUID respondentId,
            String respondentMode,
            String status,
            long revision,
            String idempotencyKey,
            Instant startedAt,
            Instant submittedAt,
            Instant createdAt,
            Instant updatedAt,
            List<AnswerView> answers) {}

    public record AnswerView(
            UUID id,
            UUID questionId,
            String questionKey,
            String valueType,
            String textValue,
            Double numberValue,
            Boolean booleanValue,
            java.time.LocalDate dateValue,
            Instant datetimeValue,
            String jsonValue,
            UUID objectId) {}
}
