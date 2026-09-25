package com.ravalgroups.forms.form.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormJpaRepository;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.form.domain.FormStatus;
import com.ravalgroups.forms.form.domain.FormVersionStatus;
import com.ravalgroups.forms.security.CurrentUser;
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
public class FormApplicationService {

    private static final String EMPTY_DEFINITION =
            "{\"pages\":[{\"id\":\"00000000-0000-7000-8000-000000000001\",\"title\":\"Page 1\",\"components\":[]}],\"questions\":[],\"rules\":[]}";

    private final FormJpaRepository forms;
    private final FormVersionJpaRepository versions;
    private final FormDefinitionValidator definitionValidator;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;

    public FormApplicationService(
            FormJpaRepository forms,
            FormVersionJpaRepository versions,
            FormDefinitionValidator definitionValidator,
            FormsAuthorizationService authz,
            DomainEventRecorder events) {
        this.forms = forms;
        this.versions = versions;
        this.definitionValidator = definitionValidator;
        this.authz = authz;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<FormView> list(CurrentUser actor, String status) {
        authz.requireAssigned(actor);
        List<FormEntity> rows;
        if (status == null || status.isBlank()) {
            rows = forms.findByCompanyIdOrderByUpdatedAtDesc(actor.companyId());
        } else {
            rows = forms.findByCompanyIdAndStatusOrderByUpdatedAtDesc(
                    actor.companyId(), FormStatus.valueOf(status.trim().toUpperCase()));
        }
        return rows.stream().map(this::toFormView).toList();
    }

    @Transactional(readOnly = true)
    public FormView get(CurrentUser actor, UUID formId) {
        authz.requireAssigned(actor);
        return toFormView(requireForm(actor, formId));
    }

    @Transactional
    public FormView create(CurrentUser actor, CreateFormCommand command) {
        authz.requireDesignerOrAdmin(actor);
        Instant now = Instant.now();
        String name = requireName(command.name());
        FormEntity form = forms.save(FormEntity.create(
                UuidV7.create(), actor.companyId(), name, command.description(), actor.userId(), now));
        String definition = command.definitionJson() == null || command.definitionJson().isBlank()
                ? EMPTY_DEFINITION
                : command.definitionJson();
        definitionValidator.parseAndValidate(definition);
        FormVersionEntity draft = versions.save(FormVersionEntity.createDraft(
                UuidV7.create(), form.getId(), 1, definition, actor.userId(), now));
        Map<String, Object> payload = basePayload(form);
        payload.put("formVersionId", draft.getId().toString());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.form.created",
                "Form",
                form.getId(),
                "forms.form.created",
                payload);
        return toFormView(form);
    }

    @Transactional
    public FormView update(CurrentUser actor, UUID formId, UpdateFormCommand command) {
        authz.requireDesignerOrAdmin(actor);
        FormEntity form = requireForm(actor, formId);
        if (form.getStatus() == FormStatus.ARCHIVED) {
            throw new DomainException("INVALID_STATE", "Archived forms cannot be updated");
        }
        form.update(command.name(), command.description(), Instant.now());
        forms.save(form);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.form.updated",
                "Form",
                form.getId(),
                "forms.form.updated",
                basePayload(form));
        return toFormView(form);
    }

    @Transactional
    public void delete(CurrentUser actor, UUID formId) {
        authz.requireDesignerOrAdmin(actor);
        FormEntity form = requireForm(actor, formId);
        form.archive(Instant.now());
        forms.save(form);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.form.updated",
                "Form",
                form.getId(),
                "forms.form.updated",
                basePayload(form));
    }

    @Transactional(readOnly = true)
    public List<FormVersionView> listVersions(CurrentUser actor, UUID formId) {
        authz.requireAssigned(actor);
        requireForm(actor, formId);
        return versions.findByFormIdOrderByVersionNumberDesc(formId).stream()
                .map(this::toVersionView)
                .toList();
    }

    @Transactional(readOnly = true)
    public FormVersionView getVersion(CurrentUser actor, UUID formId, UUID versionId) {
        authz.requireAssigned(actor);
        requireForm(actor, formId);
        return toVersionView(requireVersion(formId, versionId));
    }

    @Transactional
    public FormVersionView createVersion(CurrentUser actor, UUID formId, CreateVersionCommand command) {
        authz.requireDesignerOrAdmin(actor);
        FormEntity form = requireForm(actor, formId);
        if (form.getStatus() == FormStatus.ARCHIVED) {
            throw new DomainException("INVALID_STATE", "Cannot add versions to an archived form");
        }
        Instant now = Instant.now();
        String definition;
        if (command.fromVersionId() != null) {
            FormVersionEntity source = requireVersion(formId, command.fromVersionId());
            definition = source.getDefinitionJson();
        } else if (command.definitionJson() != null && !command.definitionJson().isBlank()) {
            definition = command.definitionJson();
        } else {
            definition = EMPTY_DEFINITION;
        }
        definitionValidator.parseAndValidate(definition);
        int next = versions.findMaxVersionNumber(formId) + 1;
        FormVersionEntity draft = versions.save(FormVersionEntity.createDraft(
                UuidV7.create(), formId, next, definition, actor.userId(), now));
        return toVersionView(draft);
    }

    @Transactional
    public FormVersionView updateDraft(
            CurrentUser actor, UUID formId, UUID versionId, UpdateDraftCommand command) {
        authz.requireDesignerOrAdmin(actor);
        requireForm(actor, formId);
        if (command.expectedRevision() == null) {
            throw new DomainException("VALIDATION_ERROR", "expectedRevision is required");
        }
        FormVersionEntity version = requireVersion(formId, versionId);
        String definition = command.definitionJson();
        definitionValidator.parseAndValidate(definition);
        version.updateDraft(definition, command.expectedRevision(), Instant.now());
        return toVersionView(versions.save(version));
    }

    @Transactional
    public FormVersionView publish(CurrentUser actor, UUID formId, UUID versionId) {
        authz.requirePublisherOrAdmin(actor);
        requireForm(actor, formId);
        FormVersionEntity version = requireVersion(formId, versionId);
        definitionValidator.parseAndValidate(version.getDefinitionJson());
        Instant now = Instant.now();
        version.publish(actor.userId(), now);
        versions.save(version);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formId", formId.toString());
        payload.put("formVersionId", version.getId().toString());
        payload.put("versionNumber", version.getVersionNumber());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.version.published",
                "FormVersion",
                version.getId(),
                "forms.version.published",
                payload);
        return toVersionView(version);
    }

    @Transactional
    public FormVersionView archiveVersion(CurrentUser actor, UUID formId, UUID versionId) {
        authz.requirePublisherOrAdmin(actor);
        requireForm(actor, formId);
        FormVersionEntity version = requireVersion(formId, versionId);
        version.archive(Instant.now());
        versions.save(version);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formId", formId.toString());
        payload.put("formVersionId", version.getId().toString());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.version.archived",
                "FormVersion",
                version.getId(),
                "forms.version.archived",
                payload);
        return toVersionView(version);
    }

    public FormEntity requireForm(CurrentUser actor, UUID formId) {
        FormEntity form = forms.findByIdAndCompanyId(formId, actor.companyId())
                .orElseThrow(() -> new DomainException("FORM_NOT_FOUND", "Form not found"));
        authz.requireCompany(actor, form.getCompanyId());
        return form;
    }

    public FormVersionEntity requireVersion(UUID formId, UUID versionId) {
        return versions.findByIdAndFormId(versionId, formId)
                .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
    }

    public FormVersionEntity requirePublishedVersion(UUID formId, UUID versionId) {
        FormVersionEntity version = requireVersion(formId, versionId);
        if (version.getStatus() != FormVersionStatus.PUBLISHED) {
            throw new DomainException("FORM_VERSION_NOT_PUBLISHED", "Form version is not published");
        }
        return version;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "name is required");
        }
        return name.trim();
    }

    private Map<String, Object> basePayload(FormEntity form) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("formId", form.getId().toString());
        payload.put("name", form.getName());
        payload.put("status", form.getStatus().name());
        return payload;
    }

    private FormView toFormView(FormEntity e) {
        return new FormView(
                e.getId(),
                e.getCompanyId(),
                e.getName(),
                e.getDescription(),
                e.getStatus().name(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private FormVersionView toVersionView(FormVersionEntity e) {
        return new FormVersionView(
                e.getId(),
                e.getFormId(),
                e.getVersionNumber(),
                e.getRevision(),
                e.getDefinitionJson(),
                e.getStatus().name(),
                e.getCreatedBy(),
                e.getPublishedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getPublishedAt());
    }

    public record FormView(
            UUID id,
            UUID companyId,
            String name,
            String description,
            String status,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {}

    public record FormVersionView(
            UUID id,
            UUID formId,
            int versionNumber,
            long revision,
            String definitionJson,
            String status,
            UUID createdBy,
            UUID publishedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {}

    public record CreateFormCommand(String name, String description, String definitionJson) {}

    public record UpdateFormCommand(String name, String description) {}

    public record CreateVersionCommand(UUID fromVersionId, String definitionJson) {}

    public record UpdateDraftCommand(String definitionJson, Long expectedRevision) {}
}
