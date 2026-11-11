package com.ravalgroups.forms.reporting.adapter.in.web;

import com.ravalgroups.forms.reporting.application.ReportingApplicationService;
import com.ravalgroups.forms.reporting.application.ReportingApplicationService.QuestionResultsView;
import com.ravalgroups.forms.reporting.application.ReportingApplicationService.RunRespondentView;
import com.ravalgroups.forms.reporting.application.ReportingApplicationService.RunSummaryView;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Who started a response. Default {@code status=IN_PROGRESS} = opened but not submitted.
     * Pass {@code status=SUBMITTED} for completers.
     */
    @GetMapping("/api/v1/runs/{runId}/respondents")
    public List<RunRespondentView> respondents(
            @PathVariable UUID runId, @RequestParam(required = false, defaultValue = "IN_PROGRESS") String status) {
        return reporting.respondents(CurrentUser.require(), runId, parseStatus(status));
    }

    @GetMapping("/api/v1/runs/{runId}/questions/{questionId}/results")
    public QuestionResultsView questionResults(@PathVariable UUID runId, @PathVariable UUID questionId) {
        return reporting.questionResults(CurrentUser.require(), runId, questionId);
    }

    private static ResponseStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return ResponseStatus.IN_PROGRESS;
        }
        try {
            return ResponseStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DomainException("VALIDATION_ERROR", "status must be IN_PROGRESS or SUBMITTED");
        }
    }
}
