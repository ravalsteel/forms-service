package com.ravalgroups.forms.authorization.adapter.in.web;

import com.ravalgroups.forms.authorization.application.RoleAssignmentApplicationService;
import com.ravalgroups.forms.authorization.application.RoleDefinitionApplicationService;
import com.ravalgroups.forms.shared.config.FormsRoleBridgeProperties;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * IAM write-through shortcut for listing/assigning local roles. Full RBAC management stays on the
 * human JWT endpoints used by the Forms portal.
 */
@RestController
@RequestMapping("/api/v1/iam-bridge")
public class IamRoleBridgeController {

    public static final String SECRET_HEADER = "X-IAM-Bridge-Secret";

    private final FormsRoleBridgeProperties properties;
    private final RoleDefinitionApplicationService definitions;
    private final RoleAssignmentApplicationService assignments;

    public IamRoleBridgeController(
            FormsRoleBridgeProperties properties,
            RoleDefinitionApplicationService definitions,
            RoleAssignmentApplicationService assignments) {
        this.properties = properties;
        this.definitions = definitions;
        this.assignments = assignments;
    }

    @GetMapping("/role-definitions")
    public List<RoleDefinitionApplicationService.RoleDefinitionView> listDefinitions(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestParam UUID companyId) {
        requireSecret(secret);
        return definitions.listDefinitionsForCompany(companyId);
    }

    @GetMapping("/users/{iamUserId}/roles")
    public List<RoleAssignmentApplicationService.RoleAssignmentView> listAssignments(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @PathVariable UUID iamUserId,
            @RequestParam UUID companyId) {
        requireSecret(secret);
        return assignments.listForUserInCompany(companyId, iamUserId);
    }

    @PutMapping("/users/{iamUserId}/roles")
    @ResponseStatus(HttpStatus.OK)
    public List<RoleAssignmentApplicationService.RoleAssignmentView> replaceAssignments(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @PathVariable UUID iamUserId,
            @RequestBody ReplaceRolesRequest request) {
        requireSecret(secret);
        if (request == null || request.companyId() == null) {
            throw new DomainException("VALIDATION_ERROR", "companyId is required");
        }
        return assignments.replaceForCompany(
                request.companyId(), iamUserId, request.roleIds() == null ? List.of() : request.roleIds());
    }

    private void requireSecret(String provided) {
        if (!properties.isConfigured()) {
            throw new DomainException("ROLE_BRIDGE_DISABLED", "IAM role bridge is not configured");
        }
        if (provided == null
                || !MessageDigest.isEqual(
                        properties.secret().getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            throw new DomainException("UNAUTHORIZED", "Invalid IAM bridge secret");
        }
    }

    public record ReplaceRolesRequest(UUID companyId, List<UUID> roleIds) {}
}
