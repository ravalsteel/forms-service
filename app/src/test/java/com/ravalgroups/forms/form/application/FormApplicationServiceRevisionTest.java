package com.ravalgroups.forms.form.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormJpaRepository;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FormApplicationServiceRevisionTest {

    @Mock
    FormJpaRepository forms;

    @Mock
    FormVersionJpaRepository versions;

    @Mock
    FormsAuthorizationService authz;

    @Mock
    DomainEventRecorder events;

    FormApplicationService service;
    CurrentUser actor;
    UUID formId;
    UUID versionId;

    @BeforeEach
    void setUp() {
        service = new FormApplicationService(
                forms, versions, new FormDefinitionValidator(new ObjectMapper()), authz, events);
        UUID companyId = UuidV7.create();
        UUID userId = UuidV7.create();
        actor = new CurrentUser(userId, companyId, "E1", "forms", "s", 0L, "web");
        formId = UuidV7.create();
        versionId = UuidV7.create();
    }

    @Test
    void updateDraftThrowsOnWrongExpectedRevision() {
        FormEntity form = FormEntity.create(formId, actor.companyId(), "F", null, actor.userId(), Instant.now());
        FormVersionEntity draft = FormVersionEntity.createDraft(
                versionId,
                formId,
                1,
                "{\"pages\":[{\"id\":\"p1\",\"title\":\"t\",\"components\":[]}],\"questions\":[],\"rules\":[]}",
                actor.userId(),
                Instant.now());
        when(forms.findByIdAndCompanyId(formId, actor.companyId())).thenReturn(Optional.of(form));
        when(versions.findByIdAndFormId(versionId, formId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.updateDraft(
                        actor,
                        formId,
                        versionId,
                        new FormApplicationService.UpdateDraftCommand(draft.getDefinitionJson(), 5L)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_CONCURRENT_MODIFICATION");
    }
}
