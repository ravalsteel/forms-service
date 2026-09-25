package com.ravalgroups.forms.reporting.adapter.in.web;

import com.ravalgroups.forms.reporting.application.ReportingApplicationService;
import com.ravalgroups.forms.reporting.application.ReportingApplicationService.QuestionResultsView;
import com.ravalgroups.forms.reporting.application.ReportingApplicationService.RunSummaryView;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Reporting")
@SecurityRequirement(name = "bearer-jwt")
public class ReportingController {

    private final ReportingApplicationService reporting;

    public ReportingController(ReportingApplicationService reporting) {
        this.reporting = reporting;
    }

    @GetMapping("/api/v1/runs/{runId}/summary")
    public RunSummaryView summary(@PathVariable UUID runId) {
        return reporting.summary(CurrentUser.require(), runId);
    }

    @GetMapping("/api/v1/runs/{runId}/questions/{questionId}/results")
    public QuestionResultsView questionResults(@PathVariable UUID runId, @PathVariable UUID questionId) {
        return reporting.questionResults(CurrentUser.require(), runId, questionId);
    }
}
