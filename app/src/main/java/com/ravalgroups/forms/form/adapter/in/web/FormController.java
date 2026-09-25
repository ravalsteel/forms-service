package com.ravalgroups.forms.form.adapter.in.web;

import com.ravalgroups.forms.form.application.FormApplicationService;
import com.ravalgroups.forms.form.application.FormApplicationService.CreateFormCommand;
import com.ravalgroups.forms.form.application.FormApplicationService.CreateVersionCommand;
import com.ravalgroups.forms.form.application.FormApplicationService.FormView;
import com.ravalgroups.forms.form.application.FormApplicationService.FormVersionView;
import com.ravalgroups.forms.form.application.FormApplicationService.UpdateDraftCommand;
import com.ravalgroups.forms.form.application.FormApplicationService.UpdateFormCommand;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forms")
@Tag(name = "Forms")
@SecurityRequirement(name = "bearer-jwt")
public class FormController {

    private final FormApplicationService forms;

    public FormController(FormApplicationService forms) {
        this.forms = forms;
    }

    @GetMapping
    public List<FormView> list(@RequestParam(required = false) String status) {
        return forms.list(CurrentUser.require(), status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormView create(@RequestBody CreateFormRequest request) {
        return forms.create(
                CurrentUser.require(),
                new CreateFormCommand(request.name(), request.description(), request.definitionJson()));
    }

    @GetMapping("/{formId}")
    public FormView get(@PathVariable UUID formId) {
        return forms.get(CurrentUser.require(), formId);
    }

    @PatchMapping("/{formId}")
    public FormView update(@PathVariable UUID formId, @RequestBody UpdateFormRequest request) {
        return forms.update(
                CurrentUser.require(), formId, new UpdateFormCommand(request.name(), request.description()));
    }

    @DeleteMapping("/{formId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID formId) {
        forms.delete(CurrentUser.require(), formId);
    }

    @GetMapping("/{formId}/versions")
    public List<FormVersionView> listVersions(@PathVariable UUID formId) {
        return forms.listVersions(CurrentUser.require(), formId);
    }

    @PostMapping("/{formId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public FormVersionView createVersion(
            @PathVariable UUID formId, @RequestBody(required = false) CreateVersionRequest request) {
        CreateVersionRequest body = request == null ? new CreateVersionRequest(null, null) : request;
        return forms.createVersion(
                CurrentUser.require(),
                formId,
                new CreateVersionCommand(body.fromVersionId(), body.definitionJson()));
    }

    @GetMapping("/{formId}/versions/{versionId}")
    public FormVersionView getVersion(@PathVariable UUID formId, @PathVariable UUID versionId) {
        return forms.getVersion(CurrentUser.require(), formId, versionId);
    }

    @PatchMapping("/{formId}/versions/{versionId}")
    public FormVersionView updateDraft(
            @PathVariable UUID formId,
            @PathVariable UUID versionId,
            @RequestBody UpdateDraftRequest request) {
        return forms.updateDraft(
                CurrentUser.require(),
                formId,
                versionId,
                new UpdateDraftCommand(request.definitionJson(), request.expectedRevision()));
    }

    @PostMapping("/{formId}/versions/{versionId}/publish")
    public FormVersionView publish(@PathVariable UUID formId, @PathVariable UUID versionId) {
        return forms.publish(CurrentUser.require(), formId, versionId);
    }

    @PostMapping("/{formId}/versions/{versionId}/archive")
    public FormVersionView archive(@PathVariable UUID formId, @PathVariable UUID versionId) {
        return forms.archiveVersion(CurrentUser.require(), formId, versionId);
    }

    public record CreateFormRequest(String name, String description, String definitionJson) {}

    public record UpdateFormRequest(String name, String description) {}

    public record CreateVersionRequest(UUID fromVersionId, String definitionJson) {}

    public record UpdateDraftRequest(@NotBlank String definitionJson, @NotNull Long expectedRevision) {}
}
