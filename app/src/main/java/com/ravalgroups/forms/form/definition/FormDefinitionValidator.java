package com.ravalgroups.forms.form.definition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.form.domain.QuestionTypes;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FormDefinitionValidator {

    private static final Set<String> COMPONENT_TYPES =
            Set.of("SECTION", "QUESTION", "TEXT", "IMAGE", "DIVIDER", "PAGE_BREAK");
    private static final Set<String> DATA_CLASSIFICATIONS =
            Set.of("INTERNAL", "PERSONAL", "SENSITIVE", "PUBLIC");
    private static final Set<String> RETENTIONS = Set.of("RETAIN", "ANONYMIZE", "DELETE");
    private static final Set<String> RULE_ACTIONS = Set.of("SHOW", "HIDE", "REQUIRE", "SKIP_TO");
    /** Matches portal APP_LOCALES: English, Amharic, Oromiffa, Portuguese. */
    private static final Set<String> CONTENT_LOCALES = Set.of("en", "am", "om", "pt");

    private final ObjectMapper objectMapper;

    public FormDefinitionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parseAndValidate(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "definition_json is required");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(definitionJson);
        } catch (Exception ex) {
            throw new DomainException("INVALID_FORM_DEFINITION", "definition_json is not valid JSON");
        }
        validate(root);
        return root;
    }

    public void validate(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "definition must be a JSON object");
        }

        JsonNode pages = root.get("pages");
        JsonNode questions = root.get("questions");
        JsonNode rules = root.path("rules");

        if (pages == null || !pages.isArray() || pages.isEmpty()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "pages must be a non-empty array");
        }
        if (questions == null || !questions.isArray()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "questions must be an array");
        }

        Set<String> pageIds = new HashSet<>();
        Set<String> questionIdsFromPages = new HashSet<>();
        for (JsonNode page : pages) {
            String pageId = text(page, "id");
            if (pageId == null) {
                throw new DomainException("INVALID_FORM_DEFINITION", "page.id is required");
            }
            if (!pageIds.add(pageId)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Duplicate page id: " + pageId);
            }
            JsonNode components = page.path("components");
            if (!components.isArray()) {
                throw new DomainException("INVALID_FORM_DEFINITION", "page.components must be an array");
            }
            for (JsonNode component : components) {
                String type = upper(text(component, "type"));
                if (type == null || !COMPONENT_TYPES.contains(type)) {
                    throw new DomainException(
                            "INVALID_FORM_DEFINITION", "Unsupported component type: " + text(component, "type"));
                }
                if ("QUESTION".equals(type)) {
                    String qid = text(component, "questionId");
                    if (qid == null) {
                        qid = text(component, "id");
                    }
                    if (qid != null) {
                        questionIdsFromPages.add(qid);
                    }
                }
            }
        }

        JsonNode meta = root.get("meta");
        validateMeta(meta);

        Map<String, String> questionIdToKey = new HashMap<>();
        Map<String, String> questionKeyToId = new HashMap<>();
        Set<String> questionIds = new HashSet<>();
        for (JsonNode question : questions) {
            String id = text(question, "id");
            String key = text(question, "key");
            String type = QuestionTypes.normalize(text(question, "type"));
            if (id == null) {
                throw new DomainException("INVALID_FORM_DEFINITION", "question.id is required");
            }
            if (key == null || key.isBlank()) {
                throw new DomainException("INVALID_FORM_DEFINITION", "question.key is required");
            }
            if (!questionIds.add(id)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Duplicate question id: " + id);
            }
            if (questionKeyToId.containsKey(key)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Duplicate question key: " + key);
            }
            if (!QuestionTypes.isSupported(type)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Unsupported question type: " + type);
            }
            String label = text(question, "label");
            if (label == null || label.isBlank()) {
                throw new DomainException("INVALID_FORM_DEFINITION", "question.label is required for " + key);
            }
            validateLabelI18n(question.get("i18n"), "question " + key);
            String classification = upper(text(question, "dataClassification"));
            if (classification != null && !DATA_CLASSIFICATIONS.contains(classification)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Invalid dataClassification for question " + key);
            }
            String retention = upper(text(question, "retention"));
            if (retention != null && !RETENTIONS.contains(retention)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Invalid retention for question " + key);
            }
            if (QuestionTypes.MATRIX.equals(type)) {
                validateMatrixConfiguration(question, key);
            }
            validateChoiceOptions(question, key);
            questionIdToKey.put(id, key);
            questionKeyToId.put(key, id);
        }

        for (String pageQuestionId : questionIdsFromPages) {
            if (!questionIds.contains(pageQuestionId)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "Page references unknown question id: " + pageQuestionId);
            }
        }

        if (rules.isMissingNode()) {
            return;
        }
        if (!rules.isArray()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "rules must be an array");
        }

        Map<String, Set<String>> skipGraph = new HashMap<>();
        Set<String> ruleIds = new HashSet<>();
        for (JsonNode rule : rules) {
            String ruleId = text(rule, "id");
            if (ruleId != null && !ruleIds.add(ruleId)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Duplicate rule id: " + ruleId);
            }
            JsonNode when = rule.get("when");
            if (when == null || !when.isObject()) {
                throw new DomainException("INVALID_FORM_DEFINITION", "rule.when is required");
            }
            String whenKey = text(when, "questionKey");
            if (whenKey == null || !questionKeyToId.containsKey(whenKey)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "rule.when references unknown questionKey: " + whenKey);
            }
            JsonNode actions = rule.get("actions");
            if (actions == null || !actions.isArray() || actions.isEmpty()) {
                throw new DomainException("INVALID_FORM_DEFINITION", "rule.actions must be a non-empty array");
            }
            for (JsonNode action : actions) {
                String actionType = upper(text(action, "type"));
                if (actionType == null || !RULE_ACTIONS.contains(actionType)) {
                    throw new DomainException("INVALID_FORM_DEFINITION", "Unsupported rule action: " + actionType);
                }
                if ("SKIP_TO".equals(actionType)) {
                    String targetPageId = text(action, "pageId");
                    String targetQuestionKey = text(action, "questionKey");
                    if (targetPageId != null) {
                        if (!pageIds.contains(targetPageId)) {
                            throw new DomainException(
                                    "INVALID_FORM_DEFINITION", "SKIP_TO references unknown pageId: " + targetPageId);
                        }
                    } else if (targetQuestionKey != null) {
                        if (!questionKeyToId.containsKey(targetQuestionKey)) {
                            throw new DomainException(
                                    "INVALID_FORM_DEFINITION",
                                    "SKIP_TO references unknown questionKey: " + targetQuestionKey);
                        }
                        skipGraph.computeIfAbsent(whenKey, k -> new HashSet<>()).add(targetQuestionKey);
                    } else {
                        throw new DomainException(
                                "INVALID_FORM_DEFINITION", "SKIP_TO requires pageId or questionKey");
                    }
                } else {
                    String targetKey = text(action, "questionKey");
                    if (targetKey == null || !questionKeyToId.containsKey(targetKey)) {
                        throw new DomainException(
                                "INVALID_FORM_DEFINITION",
                                actionType + " references unknown questionKey: " + targetKey);
                    }
                }
            }
        }

        detectSkipCycles(skipGraph);
    }

    /**
     * Validates meta.defaultLocale / meta.contentLocales / meta.i18n.
     *
     * @return normalized content locale codes (lowercase), defaulting to {@code en}
     */
    private Set<String> validateMeta(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return Set.of("en");
        }
        if (!meta.isObject()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "meta must be a JSON object when present");
        }

        String defaultLocale = text(meta, "defaultLocale");
        if (defaultLocale == null) {
            defaultLocale = "en";
        } else {
            defaultLocale = defaultLocale.toLowerCase(Locale.ROOT);
            if (!CONTENT_LOCALES.contains(defaultLocale)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "meta.defaultLocale must be one of en, am, om, pt");
            }
        }

        Set<String> contentLocales = new HashSet<>();
        JsonNode localesNode = meta.get("contentLocales");
        if (localesNode == null || localesNode.isNull()) {
            contentLocales.add(defaultLocale);
        } else {
            if (!localesNode.isArray() || localesNode.isEmpty()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "meta.contentLocales must be a non-empty array when present");
            }
            for (JsonNode localeNode : localesNode) {
                if (localeNode == null || !localeNode.isTextual()) {
                    throw new DomainException(
                            "INVALID_FORM_DEFINITION", "meta.contentLocales entries must be strings");
                }
                String code = localeNode.asText().trim().toLowerCase(Locale.ROOT);
                if (!CONTENT_LOCALES.contains(code)) {
                    throw new DomainException(
                            "INVALID_FORM_DEFINITION",
                            "Unsupported content locale: " + localeNode.asText() + " (allowed: en, am, om, pt)");
                }
                contentLocales.add(code);
            }
            if (!contentLocales.contains(defaultLocale)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "meta.contentLocales must include meta.defaultLocale");
            }
        }

        validateMetaI18n(meta.get("i18n"));
        return Set.copyOf(contentLocales);
    }

    private void validateMetaI18n(JsonNode i18n) {
        if (i18n == null || i18n.isNull()) {
            return;
        }
        if (!i18n.isObject()) {
            throw new DomainException("INVALID_FORM_DEFINITION", "meta.i18n must be a JSON object");
        }
        var fields = i18n.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String locale = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!CONTENT_LOCALES.contains(locale)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Unsupported meta.i18n locale: " + entry.getKey());
            }
            JsonNode payload = entry.getValue();
            if (payload == null || !payload.isObject()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "meta.i18n." + locale + " must be an object");
            }
        }
    }

    private void validateLabelI18n(JsonNode i18n, String context) {
        if (i18n == null || i18n.isNull()) {
            return;
        }
        if (!i18n.isObject()) {
            throw new DomainException("INVALID_FORM_DEFINITION", context + " i18n must be a JSON object");
        }
        var fields = i18n.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String locale = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!CONTENT_LOCALES.contains(locale)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Unsupported i18n locale on " + context + ": " + entry.getKey());
            }
            // Overlays may remain for locales not currently in contentLocales (creator toggled off).
            JsonNode payload = entry.getValue();
            if (payload == null || !payload.isObject()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", context + " i18n." + locale + " must be an object");
            }
            String label = text(payload, "label");
            if (label == null) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        context + " i18n." + locale + " requires a non-blank label when present");
            }
        }
    }

    private void validateMatrixConfiguration(JsonNode question, String key) {
        JsonNode configuration = question.get("configuration");
        if (configuration == null || !configuration.isObject()) {
            throw new DomainException(
                    "INVALID_FORM_DEFINITION", "MATRIX question " + key + " requires configuration");
        }
        JsonNode rows = configuration.get("rows");
        JsonNode columns = configuration.get("columns");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new DomainException(
                    "INVALID_FORM_DEFINITION", "MATRIX question " + key + " requires non-empty configuration.rows");
        }
        if (columns == null || !columns.isArray() || columns.isEmpty()) {
            throw new DomainException(
                    "INVALID_FORM_DEFINITION",
                    "MATRIX question " + key + " requires non-empty configuration.columns");
        }
        Set<String> rowKeys = new HashSet<>();
        for (JsonNode row : rows) {
            String rowKey = text(row, "key");
            String rowLabel = text(row, "label");
            if (rowKey == null || rowLabel == null) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "MATRIX rows for " + key + " require key and label");
            }
            if (!rowKeys.add(rowKey)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Duplicate MATRIX row key in " + key + ": " + rowKey);
            }
            validateLabelI18n(row.get("i18n"), "MATRIX row " + rowKey + " on " + key);
        }
        Set<String> columnValues = new HashSet<>();
        for (JsonNode column : columns) {
            String columnLabel = text(column, "label");
            JsonNode valueNode = column.get("value");
            if (columnLabel == null || valueNode == null || valueNode.isNull()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "MATRIX columns for " + key + " require label and value");
            }
            String valueKey = valueNode.isNumber() ? valueNode.asText() : valueNode.asText();
            if (valueKey == null || valueKey.isBlank() || !columnValues.add(valueKey)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "MATRIX columns for " + key + " require unique values");
            }
            validateLabelI18n(column.get("i18n"), "MATRIX column " + valueKey + " on " + key);
        }
        validateStringMapI18n(configuration.get("commentLabelI18n"), "MATRIX commentLabelI18n on " + key);
    }

    private void validateChoiceOptions(JsonNode question, String key) {
        JsonNode configuration = question.get("configuration");
        if (configuration == null || !configuration.isObject()) {
            return;
        }
        JsonNode options = configuration.get("options");
        if (options == null || options.isNull()) {
            return;
        }
        if (!options.isArray()) {
            throw new DomainException(
                    "INVALID_FORM_DEFINITION", "configuration.options for " + key + " must be an array");
        }
        for (JsonNode option : options) {
            if (option == null || option.isNull()) {
                continue;
            }
            if (option.isTextual()) {
                continue;
            }
            if (!option.isObject()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "configuration.options for " + key + " must be strings or {value,label} objects");
            }
            String value = text(option, "value");
            String label = text(option, "label");
            if (value == null || label == null) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION",
                        "option objects for " + key + " require value and label");
            }
            validateLabelI18n(option.get("i18n"), "option " + value + " on " + key);
        }
    }

    private void validateStringMapI18n(JsonNode map, String context) {
        if (map == null || map.isNull()) {
            return;
        }
        if (!map.isObject()) {
            throw new DomainException("INVALID_FORM_DEFINITION", context + " must be a JSON object");
        }
        var fields = map.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String locale = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!CONTENT_LOCALES.contains(locale)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Unsupported locale on " + context + ": " + entry.getKey());
            }
            if (entry.getValue() == null || !entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", context + "." + locale + " must be a non-blank string");
            }
        }
    }

    /** Detects cycles among SKIP_TO edges keyed by questionKey. */
    void detectSkipCycles(Map<String, Set<String>> graph) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : new HashSet<>(graph.keySet())) {
            if (hasCycle(node, graph, visiting, visited)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "SKIP_TO rules contain a cycle");
            }
        }
    }

    private boolean hasCycle(
            String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(node)) {
            return false;
        }
        if (!visiting.add(node)) {
            return true;
        }
        for (String next : graph.getOrDefault(node, Set.of())) {
            if (hasCycle(next, graph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    public Map<String, QuestionMeta> indexQuestions(JsonNode root) {
        Map<String, QuestionMeta> byId = new HashMap<>();
        for (JsonNode question : root.path("questions")) {
            String id = text(question, "id");
            String key = text(question, "key");
            String type = QuestionTypes.normalize(text(question, "type"));
            boolean required = question.path("required").asBoolean(false);
            byId.put(id, new QuestionMeta(id, key, type, required, question));
        }
        return byId;
    }

    public Map<String, QuestionMeta> indexQuestionsByKey(JsonNode root) {
        Map<String, QuestionMeta> byKey = new HashMap<>();
        for (QuestionMeta meta : indexQuestions(root).values()) {
            byKey.put(meta.key(), meta);
        }
        return byKey;
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

    public record QuestionMeta(String id, String key, String type, boolean required, JsonNode node) {}
}
