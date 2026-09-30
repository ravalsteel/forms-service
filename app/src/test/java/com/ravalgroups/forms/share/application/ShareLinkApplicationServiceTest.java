package com.ravalgroups.forms.share.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.application.FormAccessService;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkEntity;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShareLinkApplicationServiceTest {

    @Mock
    private ShareLinkJpaRepository shareLinks;

    @Mock
    private FormRunApplicationService runs;

    @Mock
    private ResponseJpaRepository responses;

    @Mock
    private FormsAuthorizationService authz;

    @Mock
    private FormAccessService formAccess;

    @Mock
    private DomainEventRecorder events;

    private ShareLinkApplicationService service;

    private final UUID companyId = UUID.fromString("5aa7230d-fe4d-4f5f-a70a-1e4b19af5a65");
    private final UUID userId = UUID.fromString("a2000000-0000-4000-8000-000000000010");
    private final UUID runId = UUID.fromString("c1000000-0000-4000-8000-000000000001");
    private final Instant now = Instant.parse("2026-09-28T08:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ShareLinkApplicationService(shareLinks, runs, responses, authz, formAccess, events);
    }

    @Test
    void createRequiresAnonymousRun() {
        CurrentUser actor = actor();
        when(runs.requireRun(actor, runId)).thenReturn(identifiedRun());

        assertThatThrownBy(() -> service.create(
                        actor, runId, new ShareLinkApplicationService.CreateShareLinkCommand(null, null, null)))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void createReturnsRawTokenOnce() {
        CurrentUser actor = actor();
        when(runs.requireRun(actor, runId)).thenReturn(anonymousRun());
        when(shareLinks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(responses.countByFormRunIdAndStatus(runId, ResponseStatus.SUBMITTED)).thenReturn(0L);

        var created = service.create(
                actor, runId, new ShareLinkApplicationService.CreateShareLinkCommand("Survey", null, 100));

        assertThat(created.token()).hasSize(64);
        assertThat(created.shareLink().label()).isEqualTo("Survey");
        assertThat(created.shareLink().maxResponses()).isEqualTo(100);
        assertThat(created.shareLink().status()).isEqualTo("ACTIVE");

        ArgumentCaptor<ShareLinkEntity> captor = ArgumentCaptor.forClass(ShareLinkEntity.class);
        verify(shareLinks).save(captor.capture());
        assertThat(captor.getValue().getTokenHash())
                .isEqualTo(ShareLinkApplicationService.hashToken(created.token()));
    }

    private FormRunEntity anonymousRun() {
        return FormRunEntity.create(
                runId,
                companyId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Public survey",
                FormRunStatus.OPEN,
                RespondentMode.ANONYMOUS,
                null,
                null,
                5,
                userId,
                now);
    }

    private FormRunEntity identifiedRun() {
        return FormRunEntity.create(
                runId,
                companyId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "HR review",
                FormRunStatus.OPEN,
                RespondentMode.IDENTIFIED,
                null,
                null,
                5,
                userId,
                now);
    }

    private CurrentUser actor() {
        return new CurrentUser(userId, companyId, "E1", "forms", "sess", 1L, null);
    }
}
