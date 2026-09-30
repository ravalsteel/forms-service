package com.ravalgroups.forms.security.api;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionJpaRepository;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Session context")
@SecurityRequirement(name = "bearer-jwt")
public class MeController {

    private final FormsAuthorizationService authz;
    private final IamCompanyProjectionJpaRepository companies;

    public MeController(FormsAuthorizationService authz, IamCompanyProjectionJpaRepository companies) {
        this.authz = authz;
        this.companies = companies;
    }

    @GetMapping
    @Operation(summary = "Return the authenticated Forms service-token context")
    public MeResponse me() {
        CurrentUser user = CurrentUser.require();
        if (user.hasCompanyContext()) {
            authz.ensureSystemRoles(user.companyId());
        }
        String companyCode = null;
        String companyName = null;
        String logoUrl = null;
        if (user.companyId() != null) {
            IamCompanyProjectionEntity company = companies.findById(user.companyId()).orElse(null);
            if (company != null) {
                companyCode = company.getCode();
                companyName = company.getName();
            }
        }
        return new MeResponse(
                user.userId(),
                user.companyId(),
                user.employeeId(),
                user.application(),
                user.sessionId(),
                user.authVersion(),
                user.clientId(),
                authz.isBootstrapOpen(user.companyId()),
                authz.assignedRoleCodes(user),
                authz.assignedPermissions(user),
                companyCode,
                companyName,
                logoUrl);
    }

    public record MeResponse(
            UUID userId,
            UUID companyId,
            String employeeId,
            String application,
            String sessionId,
            Long authVersion,
            String clientId,
            boolean bootstrapOpen,
            List<String> roles,
            List<String> permissions,
            String companyCode,
            String companyName,
            String logoUrl) {}
}
