package com.ravalgroups.forms.share.adapter.in.web;

import com.ravalgroups.forms.response.application.AnswerValidator.AnswerInput;
import com.ravalgroups.forms.response.application.ResponseApplicationService.ResponseView;
import com.ravalgroups.forms.share.application.PublicShareApplicationService;
import com.ravalgroups.forms.share.application.PublicShareApplicationService.PublicShareView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/public/share/{token}")
@Tag(name = "Public share")
public class PublicShareController {

    private final PublicShareApplicationService publicShare;

    public PublicShareController(PublicShareApplicationService publicShare) {
        this.publicShare = publicShare;
    }

    @GetMapping
    public PublicShareView resolve(@PathVariable String token, HttpServletRequest request) {
        return publicShare.resolve(token, clientKey(request));
    }

    @PostMapping("/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseView start(
            @PathVariable String token,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) StartRequest request,
            HttpServletRequest httpRequest) {
        StartRequest body = request == null ? new StartRequest(null) : request;
        return publicShare.start(token, clientKey(httpRequest), idempotencyKey, mapAnswers(body.answers()));
    }

    @PatchMapping("/responses/{responseId}")
    public ResponseView autosave(
            @PathVariable String token,
            @PathVariable UUID responseId,
            @RequestBody AutosaveRequest request,
            HttpServletRequest httpRequest) {
        return publicShare.autosave(
                token, clientKey(httpRequest), responseId, mapAnswers(request == null ? null : request.answers()));
    }

    @PostMapping("/responses/{responseId}/submit")
    public ResponseView submit(
            @PathVariable String token,
            @PathVariable UUID responseId,
            @RequestBody(required = false) SubmitRequest request,
            HttpServletRequest httpRequest) {
        SubmitRequest body = request == null ? new SubmitRequest(null, null) : request;
        return publicShare.submit(
                token,
                clientKey(httpRequest),
                responseId,
                body.formVersionId(),
                mapAnswers(body.answers()));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
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

    public record StartRequest(List<AnswerRequest> answers) {}

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
