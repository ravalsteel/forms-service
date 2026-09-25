package com.ravalgroups.forms.template.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.application.FormApplicationService;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import com.ravalgroups.forms.template.adapter.out.persistence.FormTemplateEntity;
import com.ravalgroups.forms.template.adapter.out.persistence.FormTemplateJpaRepository;
import com.ravalgroups.forms.template.domain.TemplateScope;
import com.ravalgroups.forms.template.domain.TemplateStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateApplicationService {

    private final FormTemplateJpaRepository templates;
    private final FormDefinitionValidator definitionValidator;
    private final FormApplicationService forms;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;

    public TemplateApplicationService(
            FormTemplateJpaRepository templates,
            FormDefinitionValidator definitionValidator,
            FormApplicationService forms,
            FormsAuthorizationService authz,
            DomainEventRecorder events) {
        this.templates = templates;
        this.definitionValidator = definitionValidator;
        this.forms = forms;
        this.authz = authz;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(CurrentUser actor) {
        authz.requireAssigned(actor);
        return templates.findVisible(actor.companyId()).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(CurrentUser actor, UUID templateId) {
        authz.requireAssigned(actor);
        return toView(requireVisible(actor, templateId));
    }

    @Transactional
    public TemplateView create(CurrentUser actor, CreateTemplateCommand command) {
        authz.requireDesignerOrAdmin(actor);
        if (command.name() == null || command.name().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "name is required");
        }
        definitionValidator.parseAndValidate(command.definitionJson());
        TemplateScope scope = command.scope() == null ? TemplateScope.COMPANY : command.scope();
        if (scope == TemplateScope.GLOBAL) {
            authz.requireAdmin(actor);
        }
        Instant now = Instant.now();
        FormTemplateEntity entity = templates.save(FormTemplateEntity.create(
                UuidV7.create(),
                scope == TemplateScope.GLOBAL ? null : actor.companyId(),
                scope,
                command.name().trim(),
                command.description(),
                command.definitionJson(),
                actor.userId(),
                now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateId", entity.getId().toString());
        payload.put("name", entity.getName());
        payload.put("scope", entity.getScope().name());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.template.created",
                "FormTemplate",
                entity.getId(),
                "forms.template.created",
                payload);
        return toView(entity);
    }

    @Transactional
    public TemplateView update(CurrentUser actor, UUID templateId, UpdateTemplateCommand command) {
        authz.requireDesignerOrAdmin(actor);
        FormTemplateEntity entity = requireOwned(actor, templateId);
        if (command.definitionJson() != null) {
            definitionValidator.parseAndValidate(command.definitionJson());
        }
        entity.update(command.name(), command.description(), command.definitionJson(), Instant.now());
        return toView(templates.save(entity));
    }

    @Transactional
    public void delete(CurrentUser actor, UUID templateId) {
        authz.requireDesignerOrAdmin(actor);
        FormTemplateEntity entity = requireOwned(actor, templateId);
        entity.archive(Instant.now());
        templates.save(entity);
    }

    @Transactional
    public FormApplicationService.FormView instantiate(
            CurrentUser actor, UUID templateId, InstantiateCommand command) {
        authz.requireDesignerOrAdmin(actor);
        FormTemplateEntity template = requireVisible(actor, templateId);
        String name = command.name() == null || command.name().isBlank() ? template.getName() : command.name().trim();
        return forms.create(
                actor,
                new FormApplicationService.CreateFormCommand(
                        name, command.description() != null ? command.description() : template.getDescription(),
                        template.getDefinitionJson()));
    }

    private FormTemplateEntity requireVisible(CurrentUser actor, UUID templateId) {
        FormTemplateEntity entity = templates
                .findById(templateId)
                .orElseThrow(() -> new DomainException("TEMPLATE_NOT_FOUND", "Template not found"));
        if (entity.getStatus() != TemplateStatus.ACTIVE) {
            throw new DomainException("TEMPLATE_NOT_FOUND", "Template not found");
        }
        if (entity.getScope() == TemplateScope.COMPANY) {
            authz.requireCompany(actor, entity.getCompanyId());
        }
        return entity;
    }

    private FormTemplateEntity requireOwned(CurrentUser actor, UUID templateId) {
        FormTemplateEntity entity = requireVisible(actor, templateId);
        if (entity.getScope() == TemplateScope.GLOBAL) {
            authz.requireAdmin(actor);
            return entity;
        }
        return templates
                .findByIdAndCompanyId(templateId, actor.companyId())
                .orElseThrow(() -> new DomainException("TEMPLATE_NOT_FOUND", "Template not found"));
    }

    private TemplateView toView(FormTemplateEntity e) {
        return new TemplateView(
                e.getId(),
                e.getCompanyId(),
                e.getScope().name(),
                e.getName(),
                e.getDescription(),
                e.getDefinitionJson(),
                e.getStatus().name(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    public record TemplateView(
            UUID id,
            UUID companyId,
            String scope,
            String name,
            String description,
            String definitionJson,
            String status,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateTemplateCommand(
            String name, String description, String definitionJson, TemplateScope scope) {}

    public record UpdateTemplateCommand(String name, String description, String definitionJson) {}

    public record InstantiateCommand(String name, String description) {}
}
