package com.ravalgroups.forms.share.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.form.adapter.out.persistence.FormEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormJpaRepository;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionJpaRepository;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.application.AnswerValidator;
import com.ravalgroups.forms.response.application.AnswerValidator.AnswerInput;
import com.ravalgroups.forms.response.application.ResponseApplicationService.AnswerView;
import com.ravalgroups.forms.response.application.ResponseApplicationService.ResponseView;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunJpaRepository;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkEntity;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkJpaRepository;
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
public class PublicShareApplicationService {

    private final ShareLinkJpaRepository shareLinks;
    private final FormRunJpaRepository runs;
    private final FormJpaRepository forms;
    private final FormVersionJpaRepository versions;
    private final ResponseJpaRepository responses;
    private final AnswerValidator answerValidator;
    private final DomainEventRecorder events;
    private final EntityManager entityManager;
    private final ShareLinkRateLimiter rateLimiter;
    private final IamCompanyProjectionJpaRepository companies;

    public PublicShareApplicationService(
            ShareLinkJpaRepository shareLinks,
            FormRunJpaRepository runs,
            FormJpaRepository forms,
            FormVersionJpaRepository versions,
            ResponseJpaRepository responses,
            AnswerValidator answerValidator,
            DomainEventRecorder events,
            EntityManager entityManager,
            ShareLinkRateLimiter rateLimiter,
            IamCompanyProjectionJpaRepository companies) {
        this.shareLinks = shareLinks;
        this.runs = runs;
        this.forms = forms;
        this.versions = versions;
        this.responses = responses;
        this.answerValidator = answerValidator;
        this.events = events;
        this.entityManager = entityManager;
        this.rateLimiter = rateLimiter;
        this.companies = companies;
    }

    @Transactional
    public PublicShareView resolve(String rawToken, String clientKey) {
        ShareLinkEntity link = requireUsableLink(rawToken, clientKey);
        FormRunEntity run = requireRun(link);
        FormEntity form = forms.findById(run.getFormId())
                .orElseThrow(() -> new DomainException("FORM_NOT_FOUND", "Form not found"));
        FormVersionEntity version = versions
                .findById(run.getFormVersionId())
                .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
        long submitted = responses.countByFormRunIdAndStatus(run.getId(), ResponseStatus.SUBMITTED);
        boolean accepting = run.getStatus() == com.ravalgroups.forms.run.domain.FormRunStatus.OPEN
                && (link.getMaxResponses() == null || submitted < link.getMaxResponses());
        IamCompanyProjectionEntity company = companies.findById(run.getCompanyId()).orElse(null);
        return new PublicShareView(
                run.getName(),
                form.getName(),
                run.getStatus().name(),
                run.getId(),
                run.getFormId(),
                run.getFormVersionId(),
                version.getDefinitionJson(),
                run.getClosesAt(),
                link.getExpiresAt(),
                link.getMaxResponses(),
                submitted,
                accepting,
                company != null ? company.getCode() : null,
                company != null ? company.getName() : null,
                null);
    }

    @Transactional
    public ResponseView start(String rawToken, String clientKey, String idempotencyKey, List<AnswerInput> answers) {
        ShareLinkEntity link = requireUsableLink(rawToken, clientKey);
        FormRunEntity run = requireOpenRunForShare(link);
        assertUnderCap(link, run.getId());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = responses.findByFormRunIdAndIdempotencyKey(run.getId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                ResponseEntity found = existing.get();
                assertBelongsToRun(found, run.getId());
                return toView(found);
            }
        }

        Instant now = Instant.now();
        ResponseEntity entity = ResponseEntity.start(
                UuidV7.create(),
                run.getCompanyId(),
                run.getId(),
                run.getFormVersionId(),
                null,
                RespondentMode.ANONYMOUS,
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
                run.getCompanyId(),
                null,
                "forms.response.started",
                "Response",
                saved.getId(),
                "forms.response.started.public",
                responsePayload(saved, link.getId()));
        return toView(saved);
    }

    @Transactional
    public ResponseView autosave(String rawToken, String clientKey, UUID responseId, List<AnswerInput> answers) {
        ShareLinkEntity link = requireUsableLink(rawToken, clientKey);
        FormRunEntity run = requireOpenRunForShare(link);
        ResponseEntity entity = requireGuestResponse(responseId, run.getId());
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
    public ResponseView submit(
            String rawToken, String clientKey, UUID responseId, UUID expectedFormVersionId, List<AnswerInput> answers) {
        ShareLinkEntity link = requireUsableLink(rawToken, clientKey);
        FormRunEntity run = requireOpenRunForShare(link);
        assertUnderCap(link, run.getId());
        ResponseEntity entity = requireGuestResponse(responseId, run.getId());
        if (entity.getStatus() == ResponseStatus.SUBMITTED) {
            return toView(entity);
        }
        entity.requireInProgress();

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
            // Match autosave: delete existing rows before insert to satisfy uq_response_answer_question.
            entity.clearAnswers();
            entityManager.flush();
            entity.replaceAnswers(mapped);
        } else {
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
        events.record(
                run.getCompanyId(),
                null,
                "forms.response.submitted",
                "Response",
                saved.getId(),
                "forms.response.submitted.public",
                responsePayload(saved, link.getId()));
        return toView(saved);
    }

    private ShareLinkEntity requireUsableLink(String rawToken, String clientKey) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "token is required");
        }
        String trimmed = rawToken.trim();
        rateLimiter.check(ShareLinkApplicationService.hashToken(trimmed).substring(0, 16), clientKey);
        ShareLinkEntity link = shareLinks
                .findByTokenHash(ShareLinkApplicationService.hashToken(trimmed))
                .orElseThrow(() -> new DomainException("SHARE_LINK_NOT_FOUND", "Share link not found"));
        link.requireUsable(Instant.now());
        shareLinks.save(link);
        return link;
    }

    private FormRunEntity requireRun(ShareLinkEntity link) {
        return runs.findById(link.getFormRunId())
                .orElseThrow(() -> new DomainException("FORM_RUN_NOT_FOUND", "Form run not found"));
    }

    private FormRunEntity requireOpenRunForShare(ShareLinkEntity link) {
        FormRunEntity run = requireRun(link);
        if (run.getRespondentMode() != RespondentMode.ANONYMOUS) {
            throw new DomainException("SHARE_LINK_UNAVAILABLE", "This share link is not available");
        }
        run.requireOpen();
        return run;
    }

    private void assertUnderCap(ShareLinkEntity link, UUID formRunId) {
        if (link.getMaxResponses() == null) {
            return;
        }
        long submitted = responses.countByFormRunIdAndStatus(formRunId, ResponseStatus.SUBMITTED);
        if (submitted >= link.getMaxResponses()) {
            throw new DomainException("SHARE_LINK_FULL", "This form has reached its response limit");
        }
    }

    private ResponseEntity requireGuestResponse(UUID responseId, UUID formRunId) {
        ResponseEntity entity = responses
                .findById(responseId)
                .orElseThrow(() -> new DomainException("RESPONSE_NOT_FOUND", "Response not found"));
        assertBelongsToRun(entity, formRunId);
        if (entity.getRespondentMode() != RespondentMode.ANONYMOUS) {
            throw new DomainException("FORBIDDEN", "Not a public response");
        }
        return entity;
    }

    private static void assertBelongsToRun(ResponseEntity entity, UUID formRunId) {
        if (!entity.getFormRunId().equals(formRunId)) {
            throw new DomainException("RESPONSE_NOT_FOUND", "Response not found");
        }
    }

    private Map<String, Object> responsePayload(ResponseEntity entity, UUID shareLinkId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("responseId", entity.getId().toString());
        payload.put("formRunId", entity.getFormRunId().toString());
        payload.put("formVersionId", entity.getFormVersionId().toString());
        payload.put("status", entity.getStatus().name());
        payload.put("respondentMode", entity.getRespondentMode().name());
        payload.put("shareLinkId", shareLinkId.toString());
        payload.put("public", true);
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

    public record PublicShareView(
            String runName,
            String formName,
            String runStatus,
            UUID formRunId,
            UUID formId,
            UUID formVersionId,
            String definitionJson,
            Instant closesAt,
            Instant shareExpiresAt,
            Integer maxResponses,
            long submittedCount,
            boolean acceptingResponses,
            String companyCode,
            String companyName,
            String logoUrl) {}
}
