package com.ravalgroups.forms.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ravalgroups.forms.TestcontainersConfiguration;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsRoleCode;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsRoleJpaRepository;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleEntity;
import com.ravalgroups.forms.authorization.adapter.out.persistence.FormsUserRoleJpaRepository;
import com.ravalgroups.forms.form.application.FormApplicationService;
import com.ravalgroups.forms.form.application.FormApplicationService.CreateFormCommand;
import com.ravalgroups.forms.form.application.FormApplicationService.CreateVersionCommand;
import com.ravalgroups.forms.form.application.FormApplicationService.FormVersionView;
import com.ravalgroups.forms.form.application.FormApplicationService.UpdateDraftCommand;
import com.ravalgroups.forms.response.application.AnswerValidator.AnswerInput;
import com.ravalgroups.forms.response.application.ResponseApplicationService;
import com.ravalgroups.forms.response.application.ResponseApplicationService.ResponseView;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.application.FormRunApplicationService.CreateRunCommand;
import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FormLifecycleIntegrationTest {

    @BeforeAll
    static void requireDocker() {
        assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for Testcontainers integration tests");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("forms.iam.jwks-uri", () -> "http://localhost:9/.well-known/jwks.json");
        registry.add("forms.notification.email.provider", () -> "log");
        registry.add("forms.file-storage.root", () -> "./target/forms-test-files");
        registry.add("management.tracing.enabled", () -> "false");
        registry.add("management.otlp.tracing.export.enabled", () -> "false");
    }

    @Autowired
    FormApplicationService forms;

    @Autowired
    FormRunApplicationService runs;

    @Autowired
    ResponseApplicationService responses;

    @Autowired
    FormsAuthorizationService authz;

    @Autowired
    FormsRoleJpaRepository roles;

    @Autowired
    FormsUserRoleJpaRepository userRoles;

    private CurrentUser actor;

    @BeforeEach
    void setUp() {
        UUID companyId = UuidV7.create();
        UUID userId = UuidV7.create();
        actor = new CurrentUser(userId, companyId, "EMP-1", "forms", "sid", 0L, "web");
        authz.ensureSystemRoles(companyId);
        var admin = roles.findByCompanyIdAndCode(companyId, FormsRoleCode.ADMIN).orElseThrow();
        userRoles.save(FormsUserRoleEntity.create(
                UuidV7.create(), companyId, userId, admin.getId(), Instant.now()));
    }

    @Test
    void createPublishRunRespondSubmitAndRejectMismatch() {
        String definition =
                """
                {
                  "pages":[{"id":"p1","title":"P","components":[{"id":"c1","type":"QUESTION","questionId":"019aaaaa-0000-7000-8000-000000000001"}]}],
                  "questions":[{"id":"019aaaaa-0000-7000-8000-000000000001","key":"score","type":"RATING","label":"Score","required":true}],
                  "rules":[]
                }
                """;

        var form = forms.create(actor, new CreateFormCommand("Pulse", "desc", definition));
        var versions = forms.listVersions(actor, form.id());
        assertThat(versions).hasSize(1);
        FormVersionView draft = versions.getFirst();
        assertThat(draft.status()).isEqualTo("DRAFT");

        FormVersionView published = forms.publish(actor, form.id(), draft.id());
        assertThat(published.status()).isEqualTo("PUBLISHED");

        assertThatThrownBy(() -> forms.updateDraft(
                        actor,
                        form.id(),
                        draft.id(),
                        new UpdateDraftCommand(definition, published.revision())))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_NOT_EDITABLE");

        var run = runs.create(
                actor,
                form.id(),
                new CreateRunCommand(
                        published.id(), "Q1 run", RespondentMode.IDENTIFIED, FormRunStatus.OPEN, null, null, 5));
        runs.open(actor, run.id());

        UUID clientResponseId = UuidV7.create();
        ResponseView started = responses.start(
                actor,
                run.id(),
                clientResponseId,
                "idem-1",
                List.of(new AnswerInput(
                        UUID.fromString("019aaaaa-0000-7000-8000-000000000001"),
                        null,
                        null,
                        5.0,
                        null,
                        null,
                        null,
                        null,
                        null)));
        assertThat(started.id()).isEqualTo(clientResponseId);
        assertThat(started.status()).isEqualTo("IN_PROGRESS");

        ResponseView again = responses.start(actor, run.id(), UuidV7.create(), "idem-1", List.of());
        assertThat(again.id()).isEqualTo(clientResponseId);

        assertThatThrownBy(() -> responses.submit(actor, started.id(), UuidV7.create(), null))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("FORM_VERSION_MISMATCH");

        ResponseView submitted = responses.submit(actor, started.id(), published.id(), null);
        assertThat(submitted.status()).isEqualTo("SUBMITTED");

        ResponseView idempotentSubmit = responses.submit(actor, started.id(), published.id(), null);
        assertThat(idempotentSubmit.status()).isEqualTo("SUBMITTED");

        FormVersionView v2 =
                forms.createVersion(actor, form.id(), new CreateVersionCommand(published.id(), null));
        assertThat(v2.versionNumber()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo("DRAFT");
    }
}
