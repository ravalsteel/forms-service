package com.ravalgroups.forms.run;

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
import com.ravalgroups.forms.form.application.FormApplicationService.FormVersionView;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.response.application.ResponseApplicationService;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.application.FormRunApplicationService.CreateRunCommand;
import com.ravalgroups.forms.run.application.RunAudienceService;
import com.ravalgroups.forms.run.application.RunAudienceService.AudienceRuleInput;
import com.ravalgroups.forms.run.domain.AudienceRuleType;
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
class RunAudienceIntegrationTest {

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
    RunAudienceService audience;

    @Autowired
    ResponseApplicationService responses;

    @Autowired
    FormsAuthorizationService authz;

    @Autowired
    FormsRoleJpaRepository roles;

    @Autowired
    FormsUserRoleJpaRepository userRoles;

    @Autowired
    IamMembershipProjectionJpaRepository memberships;

    private UUID companyId;
    private UUID adminId;
    private UUID respondentId;
    private UUID outsiderId;
    private UUID departmentId;
    private UUID subDepartmentId;
    private CurrentUser admin;
    private CurrentUser respondent;
    private CurrentUser outsider;
    private FormVersionView published;
    private UUID formId;

    @BeforeEach
    void setUp() {
        companyId = UuidV7.create();
        adminId = UuidV7.create();
        respondentId = UuidV7.create();
        outsiderId = UuidV7.create();
        departmentId = UuidV7.create();
        subDepartmentId = UuidV7.create();

        admin = new CurrentUser(adminId, companyId, "EMP-ADMIN", "forms", "sid", 0L, "web");
        respondent = new CurrentUser(respondentId, companyId, "EMP-R1", "forms", "sid", 0L, "web");
        outsider = new CurrentUser(outsiderId, companyId, "EMP-OUT", "forms", "sid", 0L, "web");

        authz.ensureSystemRoles(companyId);
        var adminRole = roles.findByCompanyIdAndCode(companyId, FormsRoleCode.ADMIN).orElseThrow();
        var respondentRole =
                roles.findByCompanyIdAndCode(companyId, FormsRoleCode.RESPONDENT).orElseThrow();
        Instant now = Instant.now();
        userRoles.save(FormsUserRoleEntity.create(UuidV7.create(), companyId, adminId, adminRole.getId(), now));
        userRoles.save(
                FormsUserRoleEntity.create(UuidV7.create(), companyId, respondentId, respondentRole.getId(), now));
        userRoles.save(
                FormsUserRoleEntity.create(UuidV7.create(), companyId, outsiderId, respondentRole.getId(), now));

        seedMembership(respondentId, "EMP-R1", departmentId, subDepartmentId);
        seedMembership(outsiderId, "EMP-OUT", UuidV7.create(), null);
        seedMembership(adminId, "EMP-ADMIN", departmentId, subDepartmentId);

        String definition =
                """
                {
                  "pages":[{"id":"p1","title":"P","components":[{"id":"c1","type":"QUESTION","questionId":"019aaaaa-0000-7000-8000-000000000099"}]}],
                  "questions":[{"id":"019aaaaa-0000-7000-8000-000000000099","key":"q1","type":"SHORT_TEXT","label":"Q","required":false}],
                  "rules":[]
                }
                """;
        var form = forms.create(admin, new CreateFormCommand("Audience Survey", null, definition));
        formId = form.id();
        FormVersionView draft = forms.listVersions(admin, formId).getFirst();
        published = forms.publish(admin, formId, draft.id());
    }

    @Test
    void openRequiresAudienceAndAllCompanyAllowsEligibleMembers() {
        var run = runs.create(
                admin,
                formId,
                new CreateRunCommand(
                        published.id(), "Company run", RespondentMode.IDENTIFIED, FormRunStatus.SCHEDULED, null, null, 5));

        assertThatThrownBy(() -> runs.open(admin, run.id()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("AUDIENCE_REQUIRED");

        audience.replaceAudience(admin, run.id(), List.of(new AudienceRuleInput(AudienceRuleType.ALL_COMPANY, null)));
        runs.open(admin, run.id());

        var mine = audience.listMyCollections(respondent);
        assertThat(mine).extracting(m -> m.runId()).contains(run.id());

        var started = responses.start(respondent, run.id(), UuidV7.create(), "aud-1", List.of());
        assertThat(started.status()).isEqualTo("IN_PROGRESS");

        CurrentUser noMembership =
                new CurrentUser(UuidV7.create(), companyId, "EMP-NONE", "forms", "sid", 0L, "web");
        var noneRole = roles.findByCompanyIdAndCode(companyId, FormsRoleCode.RESPONDENT).orElseThrow();
        userRoles.save(FormsUserRoleEntity.create(
                UuidV7.create(), companyId, noMembership.userId(), noneRole.getId(), Instant.now()));

        assertThatThrownBy(() -> responses.start(noMembership, run.id(), UuidV7.create(), "aud-none", List.of()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("AUDIENCE_FORBIDDEN");
    }

    @Test
    void departmentAndUserRulesMatch() {
        var run = runs.create(
                admin,
                formId,
                new CreateRunCommand(
                        published.id(), "Dept run", RespondentMode.IDENTIFIED, FormRunStatus.SCHEDULED, null, null, 5));

        audience.replaceAudience(
                admin,
                run.id(),
                List.of(new AudienceRuleInput(AudienceRuleType.DEPARTMENT, departmentId)));
        runs.open(admin, run.id());

        assertThat(audience.listMyCollections(respondent)).extracting(m -> m.runId()).contains(run.id());
        assertThat(audience.listMyCollections(outsider)).extracting(m -> m.runId()).doesNotContain(run.id());

        var userRun = runs.create(
                admin,
                formId,
                new CreateRunCommand(
                        published.id(), "User run", RespondentMode.IDENTIFIED, FormRunStatus.SCHEDULED, null, null, 5));
        audience.replaceAudience(
                admin,
                userRun.id(),
                List.of(new AudienceRuleInput(AudienceRuleType.USER, respondentId)));
        runs.open(admin, userRun.id());

        responses.start(respondent, userRun.id(), UuidV7.create(), null, List.of());
        assertThatThrownBy(() -> responses.start(outsider, userRun.id(), UuidV7.create(), null, List.of()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("AUDIENCE_FORBIDDEN");
    }

    @Test
    void subDepartmentRuleAndRespondPathReads() {
        var run = runs.create(
                admin,
                formId,
                new CreateRunCommand(
                        published.id(),
                        "Subdept run",
                        RespondentMode.IDENTIFIED,
                        FormRunStatus.SCHEDULED,
                        null,
                        null,
                        5));
        audience.replaceAudience(
                admin,
                run.id(),
                List.of(new AudienceRuleInput(AudienceRuleType.SUB_DEPARTMENT, subDepartmentId)));
        runs.open(admin, run.id());

        var view = runs.getForRespond(respondent, run.id());
        assertThat(view.id()).isEqualTo(run.id());
        assertThat(view.audience().rules()).hasSize(1);

        var version = runs.getVersionForRespond(respondent, run.id());
        assertThat(version.id()).isEqualTo(published.id());

        assertThatThrownBy(() -> runs.getForRespond(outsider, run.id()))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).code())
                .isEqualTo("AUDIENCE_FORBIDDEN");
    }

    @Test
    void anonymousRunsDoNotRequireAudience() {
        var run = runs.create(
                admin,
                formId,
                new CreateRunCommand(
                        published.id(), "Public", RespondentMode.ANONYMOUS, FormRunStatus.SCHEDULED, null, null, 5));
        assertThat(run.audienceRequired()).isFalse();
        runs.open(admin, run.id());
        assertThat(audience.listMyCollections(respondent)).extracting(m -> m.runId()).doesNotContain(run.id());
    }

    private void seedMembership(UUID userId, String employeeId, UUID deptId, UUID subDeptId) {
        Instant now = Instant.now();
        IamMembershipProjectionEntity membership = IamMembershipProjectionEntity.createNew(UuidV7.create(), now);
        membership.apply(
                userId,
                companyId,
                employeeId,
                "ACTIVE",
                deptId,
                "D",
                "Dept",
                subDeptId,
                subDeptId == null ? null : "SD",
                subDeptId == null ? null : "Sub",
                "AISCO",
                "AISCO",
                employeeId.toLowerCase(),
                employeeId,
                null,
                null,
                1L,
                now);
        memberships.save(membership);
    }
}
