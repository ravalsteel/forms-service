package com.ravalgroups.forms.authorization.adapter.in.web;

import com.ravalgroups.forms.authorization.application.RoleDefinitionApplicationService;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Role definitions")
@SecurityRequirement(name = "bearer-jwt")
public class RoleDefinitionController {

    private final RoleDefinitionApplicationService definitions;

    public RoleDefinitionController(RoleDefinitionApplicationService definitions) {
        this.definitions = definitions;
    }

    @GetMapping("/permissions")
    public List<RoleDefinitionApplicationService.PermissionView> listPermissions() {
        return definitions.listPermissions(CurrentUser.require());
    }

    @GetMapping("/role-definitions")
    public List<RoleDefinitionApplicationService.RoleDefinitionView> list() {
        return definitions.listDefinitions(CurrentUser.require());
    }

    @PostMapping("/role-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDefinitionApplicationService.RoleDefinitionView create(
            @Valid @RequestBody CreateRoleDefinitionRequest request) {
        return definitions.create(
                CurrentUser.require(),
                request.code(),
                request.name(),
                request.description(),
                request.permissionCodes());
    }

    @PutMapping("/role-definitions/{id}")
    public RoleDefinitionApplicationService.RoleDefinitionView update(
            @PathVariable UUID id, @Valid @RequestBody UpdateRoleDefinitionRequest request) {
        return definitions.update(
                CurrentUser.require(),
                id,
                request.name(),
                request.description(),
                request.permissionCodes());
    }

    @DeleteMapping("/role-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        definitions.delete(CurrentUser.require(), id);
    }

    public record CreateRoleDefinitionRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotEmpty List<String> permissionCodes) {}

    public record UpdateRoleDefinitionRequest(
            @NotBlank String name, String description, List<String> permissionCodes) {}
}
