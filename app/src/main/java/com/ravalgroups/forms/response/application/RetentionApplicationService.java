package com.ravalgroups.forms.response.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileEntity;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileJpaRepository;
import com.ravalgroups.forms.file.application.port.FileStoragePort;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator.QuestionMeta;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled retention/anonymization foundations. Applies question-level retention
 * metadata from the published FormVersion definition to submitted responses.
 */
@Service
public class RetentionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RetentionApplicationService.class);

    private final ResponseJpaRepository responses;
    private final FormVersionJpaRepository versions;
    private final FormDefinitionValidator definitionValidator;
    private final StoredFileJpaRepository files;
    private final FileStoragePort storage;
    private final DomainEventRecorder events;
    private final ObjectMapper objectMapper;

    public RetentionApplicationService(
            ResponseJpaRepository responses,
            FormVersionJpaRepository versions,
            FormDefinitionValidator definitionValidator,
            StoredFileJpaRepository files,
            FileStoragePort storage,
            DomainEventRecorder events,
            ObjectMapper objectMapper) {
        this.responses = responses;
        this.versions = versions;
        this.definitionValidator = definitionValidator;
        this.files = files;
        this.storage = storage;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${forms.retention.poll-interval-ms:300000}")
    @Transactional
    public void applyRetentionPolicies() {
        List<ResponseEntity> batch = responses.findSubmittedNotAnonymized(PageRequest.of(0, 50));
        for (ResponseEntity response : batch) {
            try {
                applyForResponse(response);
            } catch (Exception ex) {
                log.warn("Retention failed responseId={}: {}", response.getId(), ex.getMessage());
            }
        }
    }

    private void applyForResponse(ResponseEntity response) throws IOException {
        FormVersionEntity version = versions.findById(response.getFormVersionId()).orElse(null);
        if (version == null) {
            return;
        }
        JsonNode root = definitionValidator.parseAndValidate(version.getDefinitionJson());
        Map<String, QuestionMeta> byId = definitionValidator.indexQuestions(root);
        Instant now = Instant.now();
        boolean changed = false;
        boolean shouldAnonymizeRespondent = false;

        for (ResponseAnswerEntity answer : response.getAnswers()) {
            QuestionMeta meta = byId.get(answer.getQuestionId().toString());
            if (meta == null) {
                continue;
            }
            String retention = upper(text(meta.node(), "retention"));
            if (retention == null || "RETAIN".equals(retention)) {
                continue;
            }
            if ("ANONYMIZE".equals(retention) || "DELETE".equals(retention)) {
                if (answer.getObjectId() != null) {
                    deleteFileQuietly(response.getCompanyId(), answer.getObjectId());
                }
                answer.scrubPii(now);
                shouldAnonymizeRespondent = true;
                changed = true;
            }
        }

        if (shouldAnonymizeRespondent && response.getRespondentId() != null) {
            response.anonymize(now);
            changed = true;
        }

        if (changed) {
            responses.save(response);
            events.record(
                    response.getCompanyId(),
                    null,
                    "forms.response.anonymized",
                    "Response",
                    response.getId(),
                    "forms.response.anonymized",
                    Map.of(
                            "responseId", response.getId().toString(),
                            "formRunId", response.getFormRunId().toString(),
                            "source", "retention-job"));
        }
    }

    private void deleteFileQuietly(UUID companyId, UUID objectId) {
        files.findByIdAndCompanyId(objectId, companyId).ifPresent(file -> {
            try {
                storage.delete(file.getStorageKey());
            } catch (IOException ignored) {
                // continue scrubbing metadata
            }
            files.delete(file);
        });
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }
}
