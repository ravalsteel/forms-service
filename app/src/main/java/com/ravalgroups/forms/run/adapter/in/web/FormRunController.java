package com.ravalgroups.forms.run.adapter.in.web;

import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.application.FormRunApplicationService.CreateRunCommand;
import com.ravalgroups.forms.run.application.FormRunApplicationService.RunView;
import com.ravalgroups.forms.run.domain.FormRunStatus;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Runs")
@SecurityRequirement(name = "bearer-jwt")
public class FormRunController {

    private final FormRunApplicationService runs;

    public FormRunController(FormRunApplicationService runs) {
        this.runs = runs;
    }

    @GetMapping("/api/v1/forms/{formId}/runs")
    public List<RunView> listForForm(@PathVariable UUID formId) {
        return runs.listForForm(CurrentUser.require(), formId);
    }

    @PostMapping("/api/v1/forms/{formId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public RunView create(@PathVariable UUID formId, @RequestBody CreateRunRequest request) {
        RespondentMode mode = request.respondentMode() == null
                ? RespondentMode.IDENTIFIED
                : RespondentMode.valueOf(request.respondentMode().toUpperCase());
        FormRunStatus status = request.status() == null
                ? FormRunStatus.SCHEDULED
                : FormRunStatus.valueOf(request.status().toUpperCase());
        return runs.create(
                CurrentUser.require(),
                formId,
                new CreateRunCommand(
                        request.formVersionId(),
                        request.name(),
                        mode,
                        status,
                        request.opensAt(),
                        request.closesAt(),
                        request.minAggregationThreshold()));
    }

    @GetMapping("/api/v1/runs/{runId}")
    public RunView get(@PathVariable UUID runId) {
        return runs.get(CurrentUser.require(), runId);
    }

    @PostMapping("/api/v1/runs/{runId}/open")
    public RunView open(@PathVariable UUID runId) {
        return runs.open(CurrentUser.require(), runId);
    }

    @PostMapping("/api/v1/runs/{runId}/close")
    public RunView close(@PathVariable UUID runId) {
        return runs.close(CurrentUser.require(), runId);
    }

    @PostMapping("/api/v1/runs/{runId}/cancel")
    public RunView cancel(@PathVariable UUID runId) {
        return runs.cancel(CurrentUser.require(), runId);
    }

    public record CreateRunRequest(
            UUID formVersionId,
            String name,
            String respondentMode,
            String status,
            Instant opensAt,
            Instant closesAt,
            Integer minAggregationThreshold) {}
}
