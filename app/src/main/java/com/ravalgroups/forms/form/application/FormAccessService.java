package com.ravalgroups.forms.form.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.form.adapter.out.persistence.FormAccessEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormAccessJpaRepository;
import com.ravalgroups.forms.form.adapter.out.persistence.FormEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormJpaRepository;
import com.ravalgroups.forms.form.domain.FormAccessLevel;
import com.ravalgroups.forms.form.domain.FormStatus;
import com.ravalgroups.forms.iam.application.QueryIamProjection;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormAccessService {

    private final FormAccessJpaRepository access;
    private final FormJpaRepository forms;
    private final FormsAuthorizationService authz;
    private final QueryIamProjection iamProjection;

    public FormAccessService(
            FormAccessJpaRepository access,
            FormJpaRepository forms,
            FormsAuthorizationService authz,
            QueryIamProjection iamProjection) {
        this.access = access;
        this.forms = forms;
        this.authz = authz;
        this.iamProjection = iamProjection;
    }

    public boolean isCompanyAdmin(CurrentUser actor) {
        return authz.hasPermission(actor, FormsPermissionCode.ROLES_MANAGE);
    }

    /**
     * Effective access for this user on the form: Admin/creator → MANAGE; else explicit grant; else empty.
     */
    public Optional<FormAccessLevel> effectiveLevel(CurrentUser actor, FormEntity form) {
        if (form == null || actor == null || !actor.hasCompanyContext()) {
            return Optional.empty();
        }
        if (!form.getCompanyId().equals(actor.companyId())) {
            return Optional.empty();
        }
        if (isCompanyAdmin(actor) || form.getCreatedBy().equals(actor.userId())) {
            return Optional.of(FormAccessLevel.MANAGE);
        }
        return access.findByFormIdAndIamUserId(form.getId(), actor.userId())
                .map(FormAccessEntity::getAccessLevel);
    }

    public void requireAtLeast(CurrentUser actor, FormEntity form, FormAccessLevel required) {
        authz.requireCompany(actor, form.getCompanyId());
        Optional<FormAccessLevel> level = effectiveLevel(actor, form);
        if (level.isEmpty() || !level.get().atLeast(required)) {
            throw new DomainException(
                    "FORM_ACCESS_DENIED",
                    "You do not have " + required.name() + " access to this questionnaire");
        }
    }

    public void requireFormAccess(CurrentUser actor, UUID formId, FormAccessLevel required) {
        FormEntity form = forms.findByIdAndCompanyId(formId, actor.companyId())
                .orElseThrow(() -> new DomainException("FORM_NOT_FOUND", "Form not found"));
        requireAtLeast(actor, form, required);
    }

    @Transactional(readOnly = true)
    public List<FormEntity> listAccessibleForms(CurrentUser actor, String status) {
        authz.requireAssigned(actor);
        if (isCompanyAdmin(actor)) {
            if (status == null || status.isBlank()) {
                return forms.findByCompanyIdOrderByUpdatedAtDesc(actor.companyId());
            }
            return forms.findByCompanyIdAndStatusOrderByUpdatedAtDesc(
                    actor.companyId(), FormStatus.valueOf(status.trim().toUpperCase()));
        }
        if (status == null || status.isBlank()) {
            return access.findAccessibleByCompanyAndUser(actor.companyId(), actor.userId());
        }
        return access.findAccessibleByCompanyUserAndStatus(
                actor.companyId(),
                actor.userId(),
                FormStatus.valueOf(status.trim().toUpperCase()));
    }

    @Transactional(readOnly = true)
    public List<AccessGrantView> listGrants(CurrentUser actor, UUID formId) {
        FormEntity form = requireManageableForm(actor, formId);
        List<AccessGrantView> rows = new ArrayList<>();
        // Synthetic creator row first
        rows.add(toCreatorView(form));
        for (FormAccessEntity grant : access.findByFormIdOrderByCreatedAtAsc(formId)) {
            rows.add(toGrantView(grant));
        }
        return rows;
    }

    @Transactional
    public AccessGrantView upsertGrant(CurrentUser actor, UUID formId, UUID iamUserId, FormAccessLevel level) {
        FormEntity form = requireManageableForm(actor, formId);
        if (iamUserId == null) {
            throw new DomainException("VALIDATION_ERROR", "iamUserId is required");
        }
        if (level == null) {
            throw new DomainException("VALIDATION_ERROR", "accessLevel is required");
        }
        if (iamUserId.equals(form.getCreatedBy())) {
            throw new DomainException(
                    "VALIDATION_ERROR", "The creator already has full access; no grant is needed");
        }
        // Ensure the user belongs to this company
        iamProjection.getByUserId(actor.companyId(), iamUserId);

        Instant now = Instant.now();
        Optional<FormAccessEntity> existing = access.findByFormIdAndIamUserId(formId, iamUserId);
        if (existing.isPresent()) {
            FormAccessEntity entity = existing.get();
            entity.updateLevel(level, actor.userId(), now);
            return toGrantView(access.save(entity));
        }
        FormAccessEntity created = FormAccessEntity.create(
                UuidV7.create(),
                actor.companyId(),
                formId,
                iamUserId,
                level,
                actor.userId(),
                now);
        return toGrantView(access.save(created));
    }

    @Transactional
    public void revokeGrant(CurrentUser actor, UUID formId, UUID iamUserId) {
        FormEntity form = requireManageableForm(actor, formId);
        if (iamUserId != null && iamUserId.equals(form.getCreatedBy())) {
            throw new DomainException("VALIDATION_ERROR", "Cannot revoke the creator's access");
        }
        access.deleteByFormIdAndIamUserId(formId, iamUserId);
    }

    private FormEntity requireManageableForm(CurrentUser actor, UUID formId) {
        authz.requireAssigned(actor);
        FormEntity form = forms.findByIdAndCompanyId(formId, actor.companyId())
                .orElseThrow(() -> new DomainException("FORM_NOT_FOUND", "Form not found"));
        requireAtLeast(actor, form, FormAccessLevel.MANAGE);
        return form;
    }

    private AccessGrantView toCreatorView(FormEntity form) {
        String displayName = null;
        String email = null;
        try {
            var emp = iamProjection.getByUserId(form.getCompanyId(), form.getCreatedBy());
            displayName = emp.displayName();
            email = emp.email();
        } catch (RuntimeException ignored) {
            // projection may be missing; still show the creator id
        }
        return new AccessGrantView(
                null,
                form.getId(),
                form.getCreatedBy(),
                FormAccessLevel.MANAGE.name(),
                true,
                form.getCreatedBy(),
                displayName,
                email,
                form.getCreatedAt(),
                form.getUpdatedAt());
    }

    private AccessGrantView toGrantView(FormAccessEntity grant) {
        String displayName = null;
        String email = null;
        try {
            var emp = iamProjection.getByUserId(grant.getCompanyId(), grant.getIamUserId());
            displayName = emp.displayName();
            email = emp.email();
        } catch (RuntimeException ignored) {
            // optional enrichment
        }
        return new AccessGrantView(
                grant.getId(),
                grant.getFormId(),
                grant.getIamUserId(),
                grant.getAccessLevel().name(),
                false,
                grant.getGrantedBy(),
                displayName,
                email,
                grant.getCreatedAt(),
                grant.getUpdatedAt());
    }

    public record AccessGrantView(
            UUID id,
            UUID formId,
            UUID iamUserId,
            String accessLevel,
            boolean creator,
            UUID grantedBy,
            String displayName,
            String email,
            Instant createdAt,
            Instant updatedAt) {}
}
