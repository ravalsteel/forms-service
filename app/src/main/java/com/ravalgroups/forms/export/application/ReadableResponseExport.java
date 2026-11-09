package com.ravalgroups.forms.export.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One submitted response per row, question labels as columns. */
public final class ReadableResponseExport {

    public static final List<String> IDENTITY_HEADERS =
            List.of("Employee ID", "Name", "Department", "Sub-department", "Submitted");

    private static final DateTimeFormatter SUBMITTED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private ReadableResponseExport() {}

    public record QuestionColumn(
            String key,
            String header,
            Map<String, String> choiceLabels,
            Map<String, String> matrixRows,
            Map<String, String> matrixColumns) {}

    public record Person(
            String employeeId,
            String name,
            String department,
            String subDepartment,
            String submittedAt,
            Map<String, ResponseAnswerEntity> answersByKey) {}

    public record Grid(List<String> headers, List<List<String>> rows) {}

    public static List<QuestionColumn> questionsFromDefinition(ObjectMapper mapper, String definitionJson, Set<String> seenKeys) {
        List<QuestionColumn> columns = new ArrayList<>();
        if (definitionJson == null || definitionJson.isBlank()) {
            return columns;
        }
        JsonNode root;
        try {
            root = mapper.readTree(definitionJson);
        } catch (Exception ex) {
            return columns;
        }
        JsonNode questions = root.get("questions");
        if (questions == null || !questions.isArray()) {
            return columns;
        }
        for (JsonNode question : questions) {
            String key = text(question, "key");
            if (key == null || !seenKeys.add(key)) {
                continue;
            }
            String label = text(question, "label");
            columns.add(new QuestionColumn(
                    key,
                    label == null || label.isBlank() ? key : label,
                    choiceLabels(question),
                    matrixLabels(question, "rows", "key"),
                    matrixLabels(question, "columns", "value")));
        }
        return columns;
    }

    public static Grid grid(List<QuestionColumn> questions, List<Person> people) {
        List<String> headers = new ArrayList<>(IDENTITY_HEADERS);
        for (QuestionColumn question : questions) {
            headers.add(uniqueHeader(headers, question.header()));
        }
        List<List<String>> rows = new ArrayList<>();
        for (Person person : people) {
            List<String> row = new ArrayList<>();
            row.add(blank(person.employeeId()));
            row.add(blank(person.name()));
            row.add(blank(person.department()));
            row.add(blank(person.subDepartment()));
            row.add(blank(person.submittedAt()));
            for (QuestionColumn question : questions) {
                ResponseAnswerEntity answer = person.answersByKey().get(question.key());
                row.add(answer == null ? "" : display(answer, question));
            }
            rows.add(row);
        }
        return new Grid(headers, rows);
    }

    public static String toCsv(Grid grid) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(line(grid.headers()));
        for (List<String> row : grid.rows()) {
            csv.append(line(row));
        }
        return csv.toString();
    }

    public static String formatSubmitted(java.time.Instant instant) {
        return instant == null ? "" : SUBMITTED.format(instant);
    }

    private static String display(ResponseAnswerEntity answer, QuestionColumn question) {
        if (answer.getBooleanValue() != null) {
            return answer.getBooleanValue() ? "Yes" : "No";
        }
        if (answer.getNumberValue() != null) {
            double value = answer.getNumberValue();
            if (value == Math.rint(value)) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
        if (answer.getDateValue() != null) {
            return answer.getDateValue().toString();
        }
        if (answer.getDatetimeValue() != null) {
            return SUBMITTED.format(answer.getDatetimeValue());
        }
        if (answer.getObjectId() != null && (answer.getTextValue() == null || answer.getTextValue().isBlank())) {
            return "File attached";
        }
        if (answer.getJsonValue() != null && !answer.getJsonValue().isBlank()) {
            return displayJson(answer.getJsonValue(), question);
        }
        if (answer.getTextValue() != null) {
            return labelOf(question.choiceLabels(), answer.getTextValue());
        }
        return "";
    }

    private static String displayJson(String json, QuestionColumn question) {
        try {
            JsonNode node = new ObjectMapper().readTree(json);
            if (node.isArray()) {
                List<String> parts = new ArrayList<>();
                for (JsonNode item : node) {
                    parts.add(labelOf(question.choiceLabels(), item.asText()));
                }
                return String.join("; ", parts);
            }
            if (node.isObject() && node.has("cells") && node.get("cells").isObject()) {
                List<String> parts = new ArrayList<>();
                JsonNode cells = node.get("cells");
                cells.fieldNames().forEachRemaining(rowKey -> {
                    String rowLabel = labelOf(question.matrixRows(), rowKey);
                    String columnLabel = labelOf(question.matrixColumns(), cells.get(rowKey).asText());
                    parts.add(rowLabel + ": " + columnLabel);
                });
                String comment = text(node, "comment");
                if (comment != null && !comment.isBlank()) {
                    parts.add(comment);
                }
                return String.join("; ", parts);
            }
            return node.isTextual() ? labelOf(question.choiceLabels(), node.asText()) : node.toString();
        } catch (Exception ex) {
            return json;
        }
    }

    private static Map<String, String> choiceLabels(JsonNode question) {
        Map<String, String> labels = new LinkedHashMap<>();
        JsonNode options = configuration(question) == null ? null : configuration(question).get("options");
        if (options == null || !options.isArray()) {
            return labels;
        }
        for (JsonNode option : options) {
            if (option.isTextual()) {
                labels.put(option.asText(), option.asText());
            } else if (option.isObject()) {
                String value = text(option, "value");
                String label = text(option, "label");
                if (value != null) {
                    labels.put(value, label == null || label.isBlank() ? value : label);
                }
            }
        }
        return labels;
    }

    private static Map<String, String> matrixLabels(JsonNode question, String arrayName, String keyField) {
        Map<String, String> labels = new LinkedHashMap<>();
        JsonNode configuration = configuration(question);
        if (configuration == null) {
            return labels;
        }
        JsonNode items = configuration.get(arrayName);
        if (items == null || !items.isArray()) {
            return labels;
        }
        for (JsonNode item : items) {
            String key = "value".equals(keyField) && item.has("value")
                    ? item.get("value").asText()
                    : text(item, keyField);
            String label = text(item, "label");
            if (key != null) {
                labels.put(key, label == null || label.isBlank() ? key : label);
            }
        }
        return labels;
    }

    private static JsonNode configuration(JsonNode question) {
        JsonNode configuration = question.get("configuration");
        return configuration != null && configuration.isObject() ? configuration : null;
    }

    private static String labelOf(Map<String, String> labels, String value) {
        if (value == null) {
            return "";
        }
        return labels.getOrDefault(value, value);
    }

    private static String uniqueHeader(List<String> existing, String header) {
        if (!existing.contains(header)) {
            return header;
        }
        int n = 2;
        while (existing.contains(header + " (" + n + ")")) {
            n++;
        }
        return header + " (" + n + ")";
    }

    private static String line(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells.get(i)));
        }
        line.append('\n');
        return line.toString();
    }

    private static String escape(String value) {
        String raw = value == null ? "" : value;
        String escaped = raw.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
