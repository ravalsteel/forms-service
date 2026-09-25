package com.ravalgroups.forms.response.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator.QuestionMeta;
import com.ravalgroups.forms.form.domain.QuestionTypes;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnswerValidator {

    private final FormDefinitionValidator definitionValidator;
    private final ObjectMapper objectMapper;

    public AnswerValidator(FormDefinitionValidator definitionValidator, ObjectMapper objectMapper) {
        this.definitionValidator = definitionValidator;
        this.objectMapper = objectMapper;
    }

    public List<ResponseAnswerEntity> mapAnswers(
            String definitionJson, List<AnswerInput> inputs, boolean requireRequired, Instant now) {
        JsonNode root = definitionValidator.parseAndValidate(definitionJson);
        Map<String, QuestionMeta> byId = definitionValidator.indexQuestions(root);
        Map<String, QuestionMeta> byKey = definitionValidator.indexQuestionsByKey(root);

        List<ResponseAnswerEntity> answers = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Set<String> answeredKeys = new HashSet<>();

        if (inputs != null) {
            for (AnswerInput input : inputs) {
                QuestionMeta meta = resolveMeta(input, byId, byKey);
                UUID questionId = UUID.fromString(meta.id());
                if (!seen.add(questionId)) {
                    throw new DomainException("INVALID_ANSWER", "Duplicate answer for question " + meta.key());
                }
                answers.add(toEntity(meta, input, now));
                answeredKeys.add(meta.key());
            }
        }

        if (requireRequired) {
            for (QuestionMeta meta : byId.values()) {
                if (meta.required() && !answeredKeys.contains(meta.key())) {
                    throw new DomainException("INVALID_ANSWER", "Required question missing: " + meta.key());
                }
            }
        }
        return answers;
    }

    private QuestionMeta resolveMeta(
            AnswerInput input, Map<String, QuestionMeta> byId, Map<String, QuestionMeta> byKey) {
        if (input.questionId() != null) {
            QuestionMeta meta = byId.get(input.questionId().toString());
            if (meta == null) {
                throw new DomainException("INVALID_QUESTION_REFERENCE", "Unknown questionId: " + input.questionId());
            }
            return meta;
        }
        if (input.questionKey() != null && !input.questionKey().isBlank()) {
            QuestionMeta meta = byKey.get(input.questionKey());
            if (meta == null) {
                throw new DomainException(
                        "INVALID_QUESTION_REFERENCE", "Unknown questionKey: " + input.questionKey());
            }
            return meta;
        }
        throw new DomainException("VALIDATION_ERROR", "questionId or questionKey is required");
    }

    private ResponseAnswerEntity toEntity(QuestionMeta meta, AnswerInput input, Instant now) {
        String type = meta.type();
        if (QuestionTypes.isTextLike(type)) {
            if (input.textValue() == null) {
                throw new DomainException("INVALID_ANSWER", "textValue required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "TEXT",
                    input.textValue(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    now);
        }
        if (QuestionTypes.isChoiceMulti(type)) {
            if (input.jsonValue() == null && input.textValue() == null) {
                throw new DomainException("INVALID_ANSWER", "jsonValue/textValue required for " + meta.key());
            }
            String json = input.jsonValue();
            if (json == null) {
                try {
                    json = objectMapper.writeValueAsString(List.of(input.textValue()));
                } catch (Exception ex) {
                    throw new DomainException("INVALID_ANSWER", "Unable to serialize multi choice");
                }
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "JSON",
                    null,
                    null,
                    null,
                    null,
                    null,
                    json,
                    null,
                    now);
        }
        if (QuestionTypes.isNumeric(type)) {
            if (input.numberValue() == null) {
                throw new DomainException("INVALID_ANSWER", "numberValue required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "NUMBER",
                    null,
                    input.numberValue(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    now);
        }
        if (QuestionTypes.isBooleanLike(type)) {
            if (input.booleanValue() == null) {
                throw new DomainException("INVALID_ANSWER", "booleanValue required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "BOOLEAN",
                    null,
                    null,
                    input.booleanValue(),
                    null,
                    null,
                    null,
                    null,
                    now);
        }
        if (QuestionTypes.DATE.equals(type)) {
            if (input.dateValue() == null) {
                throw new DomainException("INVALID_ANSWER", "dateValue required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "DATE",
                    null,
                    null,
                    null,
                    input.dateValue(),
                    null,
                    null,
                    null,
                    now);
        }
        if (QuestionTypes.DATETIME.equals(type)) {
            if (input.datetimeValue() == null) {
                throw new DomainException("INVALID_ANSWER", "datetimeValue required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "DATETIME",
                    null,
                    null,
                    null,
                    null,
                    input.datetimeValue(),
                    null,
                    null,
                    now);
        }
        if (QuestionTypes.FILE_UPLOAD.equals(type)) {
            if (input.objectId() == null) {
                throw new DomainException("INVALID_ANSWER", "objectId required for " + meta.key());
            }
            return ResponseAnswerEntity.create(
                    UuidV7.create(),
                    UUID.fromString(meta.id()),
                    meta.key(),
                    "FILE",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    input.objectId(),
                    now);
        }
        throw new DomainException("INVALID_ANSWER", "Unsupported question type " + type);
    }

    public record AnswerInput(
            UUID questionId,
            String questionKey,
            String textValue,
            Double numberValue,
            Boolean booleanValue,
            LocalDate dateValue,
            Instant datetimeValue,
            String jsonValue,
            UUID objectId) {}
}
