package com.ravalgroups.forms.template.adapter.in.web;

import com.ravalgroups.forms.form.application.FormApplicationService;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.template.application.TemplateApplicationService;
import com.ravalgroups.forms.template.application.TemplateApplicationService.CreateTemplateCommand;
import com.ravalgroups.forms.template.application.TemplateApplicationService.InstantiateCommand;
import com.ravalgroups.forms.template.application.TemplateApplicationService.TemplateView;
import com.ravalgroups.forms.template.application.TemplateApplicationService.UpdateTemplateCommand;
import com.ravalgroups.forms.template.domain.TemplateScope;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Templates")
@SecurityRequirement(name = "bearer-jwt")
public class TemplateController {

    private final TemplateApplicationService templates;

    public TemplateController(TemplateApplicationService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<TemplateView> list() {
        return templates.list(CurrentUser.require());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateView create(@RequestBody CreateTemplateRequest request) {
        TemplateScope scope =
                request.scope() == null ? TemplateScope.COMPANY : TemplateScope.valueOf(request.scope().toUpperCase());
        return templates.create(
                CurrentUser.require(),
                new CreateTemplateCommand(
                        request.name(), request.description(), request.definitionJson(), scope));
    }

    @GetMapping("/{templateId}")
    public TemplateView get(@PathVariable UUID templateId) {
        return templates.get(CurrentUser.require(), templateId);
    }

    @PatchMapping("/{templateId}")
    public TemplateView update(@PathVariable UUID templateId, @RequestBody UpdateTemplateRequest request) {
        return templates.update(
                CurrentUser.require(),
                templateId,
                new UpdateTemplateCommand(request.name(), request.description(), request.definitionJson()));
    }

    @DeleteMapping("/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID templateId) {
        templates.delete(CurrentUser.require(), templateId);
    }

    @PostMapping("/{templateId}/instantiate")
    @ResponseStatus(HttpStatus.CREATED)
    public FormApplicationService.FormView instantiate(
            @PathVariable UUID templateId, @RequestBody(required = false) InstantiateRequest request) {
        InstantiateRequest body = request == null ? new InstantiateRequest(null, null) : request;
        return templates.instantiate(
                CurrentUser.require(), templateId, new InstantiateCommand(body.name(), body.description()));
    }

    public record CreateTemplateRequest(String name, String description, String definitionJson, String scope) {}

    public record UpdateTemplateRequest(String name, String description, String definitionJson) {}

    public record InstantiateRequest(String name, String description) {}
}
