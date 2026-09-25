package com.ravalgroups.forms.iam.adapter.out.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.ravalgroups.forms.iam.application.port.IamClientPort;
import com.ravalgroups.forms.shared.config.FormsIamProperties;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Outbound IAM HTTP adapter using discovered IAM routes:
 * {@code GET /api/v1/users/{id}}, {@code GET /api/v1/users/{userId}/memberships}.
 * Requires a machine/service bearer with IAM memberships/users read permission.
 */
@Component
public class IamHttpClient implements IamClientPort {

    private static final Logger log = LoggerFactory.getLogger(IamHttpClient.class);

    private final RestClient restClient;

    public IamHttpClient(FormsIamProperties properties, RestClient.Builder builder) {
        RestClient.Builder configured = builder.baseUrl(properties.baseUrl());
        if (properties.serviceBearerToken() != null && !properties.serviceBearerToken().isBlank()) {
            configured = configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceBearerToken());
        }
        this.restClient = configured.build();
    }

    @Override
    public Optional<IamUserView> getUser(UUID userId) {
        try {
            JsonNode body = restClient
                    .get()
                    .uri("/api/v1/users/{id}", userId)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(mapUser(body));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("IAM getUser failed status={}", ex.getStatusCode().value());
            throw ex;
        }
    }

    @Override
    public Optional<IamMembershipView> getMembership(UUID companyId, UUID userId) {
        try {
            JsonNode body = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/{userId}/memberships")
                            .queryParam("page", 0)
                            .queryParam("size", 100)
                            .build(userId))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || body.get("content") == null || !body.get("content").isArray()) {
                return Optional.empty();
            }
            for (JsonNode item : body.get("content")) {
                UUID itemCompanyId = uuidOrNull(item, "companyId");
                if (companyId.equals(itemCompanyId)) {
                    return Optional.of(mapMembership(item));
                }
            }
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("IAM getMembership failed status={}", ex.getStatusCode().value());
            throw ex;
        }
    }

    private static IamUserView mapUser(JsonNode body) {
        return new IamUserView(
                UUID.fromString(text(body, "id")),
                text(body, "email"),
                text(body, "username"),
                text(body, "firstName"),
                text(body, "lastName"),
                text(body, "displayName"),
                text(body, "designation"),
                text(body, "phoneNumber"),
                uuidOrNull(body, "reportingManagerId"),
                text(body, "status"));
    }

    private static IamMembershipView mapMembership(JsonNode body) {
        JsonNode department = body.get("department");
        return new IamMembershipView(
                UUID.fromString(text(body, "id")),
                UUID.fromString(text(body, "userId")),
                UUID.fromString(text(body, "companyId")),
                text(body, "companyCode"),
                text(body, "companyName"),
                text(body, "employeeId"),
                text(body, "status"),
                department == null || department.isNull() ? null : uuidOrNull(department, "id"),
                department == null || department.isNull() ? null : text(department, "code"),
                department == null || department.isNull() ? null : text(department, "name"),
                text(body, "username"),
                text(body, "displayName"),
                text(body, "email"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String value = text(node, field);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
