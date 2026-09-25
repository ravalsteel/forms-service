package com.ravalgroups.forms.authorization.adapter.in.web;

import com.ravalgroups.forms.authorization.application.RoleAssignmentApplicationService;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles")
@SecurityRequirement(name = "bearer-jwt")
public class RoleAssignmentController {

    private final RoleAssignmentApplicationService roles;

    public RoleAssignmentController(RoleAssignmentApplicationService roles) {
        this.roles = roles;
    }

    @GetMapping
    public List<RoleAssignmentApplicationService.RoleAssignmentView> list(
            @RequestParam(required = false) String roleCode) {
        return roles.list(CurrentUser.require(), roleCode);
    }

    @GetMapping("/users/{iamUserId}")
    public List<RoleAssignmentApplicationService.RoleAssignmentView> listForUser(@PathVariable UUID iamUserId) {
        return roles.listForUser(CurrentUser.require(), iamUserId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentApplicationService.RoleAssignmentView assign(@Valid @RequestBody AssignRequest request) {
        return roles.assign(CurrentUser.require(), request.iamUserId(), request.roleCode());
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentApplicationService.RoleAssignmentView bootstrap() {
        return roles.claimFirstAdmin(CurrentUser.require());
    }

    @DeleteMapping("/users/{iamUserId}/{roleCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID iamUserId, @PathVariable String roleCode) {
        roles.revoke(CurrentUser.require(), iamUserId, roleCode);
    }

    public record AssignRequest(@NotNull UUID iamUserId, @NotBlank String roleCode) {}
}
