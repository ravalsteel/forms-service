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
            String classification = upper(text(question, "dataClassification"));
            if (classification != null && !DATA_CLASSIFICATIONS.contains(classification)) {
                throw new DomainException(
                        "INVALID_FORM_DEFINITION", "Invalid dataClassification for question " + key);
            }
            String retention = upper(text(question, "retention"));
            if (retention != null && !RETENTIONS.contains(retention)) {
                throw new DomainException("INVALID_FORM_DEFINITION", "Invalid retention for question " + key);
            }
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
