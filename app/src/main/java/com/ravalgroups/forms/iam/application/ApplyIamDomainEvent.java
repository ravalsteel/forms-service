package com.ravalgroups.forms.iam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamCompanyProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamDepartmentProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamDepartmentProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamMembershipProjectionJpaRepository;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionEntity;
import com.ravalgroups.forms.iam.adapter.out.persistence.IamUserProjectionJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplyIamDomainEvent {

    private static final Logger log = LoggerFactory.getLogger(ApplyIamDomainEvent.class);

    private final ObjectMapper objectMapper;
    private final IamUserProjectionJpaRepository users;
    private final IamMembershipProjectionJpaRepository memberships;
    private final IamCompanyProjectionJpaRepository companies;
    private final IamDepartmentProjectionJpaRepository departments;

    public ApplyIamDomainEvent(
            ObjectMapper objectMapper,
            IamUserProjectionJpaRepository users,
            IamMembershipProjectionJpaRepository memberships,
            IamCompanyProjectionJpaRepository companies,
            IamDepartmentProjectionJpaRepository departments) {
        this.objectMapper = objectMapper;
        this.users = users;
        this.memberships = memberships;
        this.companies = companies;
        this.departments = departments;
    }

    @Transactional
    public void apply(String routingKey, String payloadJson) {
        if (routingKey == null || routingKey.isBlank() || payloadJson == null || payloadJson.isBlank()) {
            log.warn("Ignoring IAM event with empty routing key or payload");
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid IAM event JSON for " + routingKey, ex);
        }

        Instant now = Instant.now();
        if (routingKey.startsWith("iam.user.")) {
            upsertUser(payload, now);
            return;
        }
        if (routingKey.startsWith("iam.membership.")) {
            upsertMembership(payload, now);
            return;
        }
        if (routingKey.startsWith("iam.company.")) {
            upsertCompany(payload, now);
            return;
        }
        if (routingKey.startsWith("iam.department.")) {
            upsertDepartment(payload, now);
            return;
        }
        if (routingKey.startsWith("iam.application_access.")) {
            applyApplicationAccess(routingKey, payload, now);
            return;
        }
        log.info("No projection handler for IAM routing key={}", routingKey);
    }

    private void upsertUser(JsonNode payload, Instant now) {
        UUID id = uuid(payload, "id");
        IamUserProjectionEntity user =
                users.findById(id).orElseGet(() -> IamUserProjectionEntity.createNew(id, now));
        user.apply(
                text(payload, "email"),
                text(payload, "username"),
                text(payload, "firstName"),
                text(payload, "lastName"),
                text(payload, "displayName"),
                text(payload, "designation"),
                text(payload, "phoneNumber"),
                uuidOrNull(payload, "reportingManagerId"),
                text(payload, "status"),
                null,
                now);
        users.save(user);
    }

    private void upsertMembership(JsonNode payload, Instant now) {
        UUID id = uuid(payload, "id");
        UUID companyId = uuid(payload, "companyId");
        String companyCode = text(payload, "companyCode");
        String companyName = text(payload, "companyName");
        if (companyCode != null && companyName != null) {
            IamCompanyProjectionEntity company = companies
                    .findById(companyId)
                    .orElseGet(() -> IamCompanyProjectionEntity.createNew(companyId, now));
            company.apply(companyCode, companyName, null, null, now);
            companies.save(company);
        }

        JsonNode department = payload.get("department");
        UUID departmentId = null;
        String departmentCode = null;
        String departmentName = null;
        if (department != null && !department.isNull()) {
            departmentId = uuidOrNull(department, "id");
            departmentCode = text(department, "code");
            departmentName = text(department, "name");
        }

        IamMembershipProjectionEntity membership = memberships
                .findById(id)
                .orElseGet(() -> IamMembershipProjectionEntity.createNew(id, now));
        membership.apply(
                uuid(payload, "userId"),
                companyId,
                text(payload, "employeeId"),
                text(payload, "status"),
                departmentId,
                departmentCode,
                departmentName,
                companyCode,
                companyName,
                text(payload, "username"),
                text(payload, "displayName"),
                text(payload, "email"),
                instantOrNull(payload, "expiresAt"),
                null,
                now);
        memberships.save(membership);
        seedUserFromMembership(payload, now);
    }

    private void seedUserFromMembership(JsonNode payload, Instant now) {
        UUID userId = uuidOrNull(payload, "userId");
        if (userId == null || users.findById(userId).isPresent()) {
            return;
        }
        IamUserProjectionEntity user = IamUserProjectionEntity.createNew(userId, now);
        user.apply(
                text(payload, "email"),
                text(payload, "username"),
                null,
                null,
                text(payload, "displayName"),
                null,
                null,
                null,
                text(payload, "status"),
                null,
                now);
        users.save(user);
    }

    private void applyApplicationAccess(String routingKey, JsonNode payload, Instant now) {
        if (routingKey.endsWith(".revoked")) {
            return;
        }
        if (!isFormsApplication(payload)) {
            return;
        }
        JsonNode user = payload.get("user");
        if (user != null && !user.isNull()) {
            upsertUser(user, now);
        }
        JsonNode membership = payload.get("membership");
        if (membership != null && !membership.isNull()) {
            upsertMembership(membership, now);
        }
    }

    private static boolean isFormsApplication(JsonNode payload) {
        String code = text(payload, "applicationCode");
        if (code == null) {
            return true;
        }
        return "forms".equalsIgnoreCase(code) || "forms-api".equalsIgnoreCase(code);
    }

    private void upsertCompany(JsonNode payload, Instant now) {
        UUID id = uuid(payload, "id");
        IamCompanyProjectionEntity company =
                companies.findById(id).orElseGet(() -> IamCompanyProjectionEntity.createNew(id, now));
        company.apply(text(payload, "code"), text(payload, "name"), text(payload, "status"), null, now);
        companies.save(company);
    }

    private void upsertDepartment(JsonNode payload, Instant now) {
        UUID id = uuid(payload, "id");
        String code = text(payload, "code");
        String name = text(payload, "name");
        String status = text(payload, "status");
        IamDepartmentProjectionEntity department =
                departments.findById(id).orElseGet(() -> IamDepartmentProjectionEntity.createNew(id, now));
        department.apply(uuid(payload, "companyId"), code, name == null ? "" : name, status, null, now);
        departments.save(department);

        for (IamMembershipProjectionEntity membership : memberships.findByDepartmentId(id)) {
            membership.renameDepartment(code, name, now);
            memberships.save(membership);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        UUID value = uuidOrNull(node, field);
        if (value == null) {
            throw new IllegalArgumentException("Missing required UUID field: " + field);
        }
        return value;
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return UUID.fromString(value.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Instant instantOrNull(JsonNode node, String field) {
        String text = text(node, field);
        return text == null ? null : Instant.parse(text);
    }
}
