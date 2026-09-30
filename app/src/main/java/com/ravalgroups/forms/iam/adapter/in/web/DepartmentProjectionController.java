package com.ravalgroups.forms.iam.adapter.in.web;

import com.ravalgroups.forms.iam.application.QueryIamProjection;
import com.ravalgroups.forms.iam.application.QueryIamProjection.DepartmentProjectionView;
import com.ravalgroups.forms.iam.application.QueryIamProjection.SubDepartmentProjectionView;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "IAM projection (read-only)")
@SecurityRequirement(name = "bearer-jwt")
public class DepartmentProjectionController {

    private final QueryIamProjection queryIamProjection;

    public DepartmentProjectionController(QueryIamProjection queryIamProjection) {
        this.queryIamProjection = queryIamProjection;
    }

    @GetMapping("/departments")
    public List<DepartmentProjectionView> listDepartments() {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new DomainException("FORBIDDEN", "Company context required");
        }
        return queryIamProjection.listDepartments(user.companyId());
    }

    @GetMapping("/sub-departments")
    public List<SubDepartmentProjectionView> listSubDepartments(
            @RequestParam(required = false) UUID departmentId) {
        CurrentUser user = CurrentUser.require();
        if (!user.hasCompanyContext()) {
            throw new DomainException("FORBIDDEN", "Company context required");
        }
        return queryIamProjection.listSubDepartments(user.companyId(), departmentId);
    }
}
