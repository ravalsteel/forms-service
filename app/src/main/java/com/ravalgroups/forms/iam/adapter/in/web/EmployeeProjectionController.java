package com.ravalgroups.forms.iam.adapter.in.web;

import com.ravalgroups.forms.iam.application.QueryIamProjection;
import com.ravalgroups.forms.iam.application.ReconcileIamProjection;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "IAM projection (read-only)")
@SecurityRequirement(name = "bearer-jwt")
public class EmployeeProjectionController {

    private final QueryIamProjection queryIamProjection;
    private final ReconcileIamProjection reconcileIamProjection;

    public EmployeeProjectionController(QueryIamProjection queryIamProjection, ReconcileIamProjection reconcileIamProjection) {
        this.queryIamProjection = queryIamProjection;
        this.reconcileIamProjection = reconcileIamProjection;
    }

    @GetMapping
    public PageResponse<QueryIamProjection.EmployeeProjectionView> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new com.ravalgroups.forms.shared.exception.DomainException(
                    "FORBIDDEN", "Company context required");
        }
        return PageResponse.from(queryIamProjection.listCompanyEmployees(user.companyId(), q, page, size, sort));
    }

    @GetMapping("/by-employee-id/{employeeId}")
    public QueryIamProjection.EmployeeProjectionView byEmployeeId(@PathVariable String employeeId) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new com.ravalgroups.forms.shared.exception.DomainException(
                    "FORBIDDEN", "Company context required");
        }
        return queryIamProjection.getByEmployeeId(user.companyId(), employeeId);
    }

    @GetMapping("/{iamUserId}")
    public QueryIamProjection.EmployeeProjectionView byUserId(@PathVariable UUID iamUserId) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new com.ravalgroups.forms.shared.exception.DomainException(
                    "FORBIDDEN", "Company context required");
        }
        return queryIamProjection.getByUserId(user.companyId(), iamUserId);
    }

    @PostMapping("/{iamUserId}/reconcile")
    public QueryIamProjection.EmployeeProjectionView reconcile(@PathVariable UUID iamUserId) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new com.ravalgroups.forms.shared.exception.DomainException(
                    "FORBIDDEN", "Company context required");
        }
        reconcileIamProjection.reconcileUserAndMembership(user.companyId(), iamUserId);
        return queryIamProjection.getByUserId(user.companyId(), iamUserId);
    }
}
