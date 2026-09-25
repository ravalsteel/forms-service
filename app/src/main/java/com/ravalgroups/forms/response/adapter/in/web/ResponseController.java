package com.ravalgroups.forms.response.adapter.in.web;

import com.ravalgroups.forms.response.application.AnswerValidator.AnswerInput;
import com.ravalgroups.forms.response.application.ResponseApplicationService;
import com.ravalgroups.forms.response.application.ResponseApplicationService.ResponseView;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Responses")
@SecurityRequirement(name = "bearer-jwt")
public class ResponseController {

    private final ResponseApplicationService responses;

    public ResponseController(ResponseApplicationService responses) {
        this.responses = responses;
    }

    @PostMapping("/api/v1/runs/{runId}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseView start(
            @PathVariable UUID runId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) StartResponseRequest request) {
        StartResponseRequest body = request == null ? new StartResponseRequest(null, null) : request;
        return responses.start(
                CurrentUser.require(),
                runId,
                body.responseId(),
                idempotencyKey,
                mapAnswers(body.answers()));
    }

    @GetMapping("/api/v1/responses/{responseId}")
    public ResponseView get(@PathVariable UUID responseId) {
        return responses.get(CurrentUser.require(), responseId);
    }

    @PatchMapping("/api/v1/responses/{responseId}")
    public ResponseView autosave(@PathVariable UUID responseId, @RequestBody AutosaveRequest request) {
        return responses.autosave(CurrentUser.require(), responseId, mapAnswers(request.answers()));
    }

    @PostMapping("/api/v1/responses/{responseId}/submit")
    public ResponseView submit(@PathVariable UUID responseId, @RequestBody(required = false) SubmitRequest request) {
        SubmitRequest body = request == null ? new SubmitRequest(null, null) : request;
        return responses.submit(
                CurrentUser.require(), responseId, body.formVersionId(), mapAnswers(body.answers()));
    }

    @PostMapping("/api/v1/responses/{responseId}/anonymize")
    public ResponseView anonymize(@PathVariable UUID responseId) {
        return responses.anonymize(CurrentUser.require(), responseId);
    }

    private static List<AnswerInput> mapAnswers(List<AnswerRequest> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.stream()
                .map(a -> new AnswerInput(
                        a.questionId(),
                        a.questionKey(),
                        a.textValue(),
                        a.numberValue(),
                        a.booleanValue(),
                        a.dateValue(),
                        a.datetimeValue(),
                        a.jsonValue(),
                        a.objectId()))
                .toList();
    }

    public record StartResponseRequest(UUID responseId, List<AnswerRequest> answers) {}

    public record AutosaveRequest(List<AnswerRequest> answers) {}

    public record SubmitRequest(UUID formVersionId, List<AnswerRequest> answers) {}

    public record AnswerRequest(
            UUID questionId,
            String questionKey,
            String textValue,
            Double numberValue,
            Boolean booleanValue,
            LocalDate dateValue,
            Instant datetimeValue,
            String jsonValue,
            UUID objectId) {}
}
