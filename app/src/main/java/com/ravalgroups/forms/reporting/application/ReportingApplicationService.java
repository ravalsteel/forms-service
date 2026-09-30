package com.ravalgroups.forms.reporting.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionEntity;
import com.ravalgroups.forms.form.adapter.out.persistence.FormVersionJpaRepository;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator;
import com.ravalgroups.forms.form.definition.FormDefinitionValidator.QuestionMeta;
import com.ravalgroups.forms.form.domain.QuestionTypes;
import com.ravalgroups.forms.invitation.adapter.out.persistence.InvitationJpaRepository;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportingApplicationService {

    private final FormRunApplicationService runs;
    private final ResponseJpaRepository responses;
    private final InvitationJpaRepository invitations;
    private final FormVersionJpaRepository versions;
    private final FormDefinitionValidator definitionValidator;
    private final FormsAuthorizationService authz;
    private final ObjectMapper objectMapper;

    public ReportingApplicationService(
            FormRunApplicationService runs,
            ResponseJpaRepository responses,
            InvitationJpaRepository invitations,
            FormVersionJpaRepository versions,
            FormDefinitionValidator definitionValidator,
            FormsAuthorizationService authz,
            ObjectMapper objectMapper) {
        this.runs = runs;
        this.responses = responses;
        this.invitations = invitations;
        this.versions = versions;
        this.definitionValidator = definitionValidator;
        this.authz = authz;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public RunSummaryView summary(CurrentUser actor, UUID runId) {
        authz.requireAnalystOrAdmin(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        long invited = invitations.countByFormRunId(runId);
        long started = responses.countByFormRunId(runId);
        long submitted = responses.countByFormRunIdAndStatus(runId, ResponseStatus.SUBMITTED);
        double completionRate = started == 0 ? 0.0 : (double) submitted / (double) started;
        return new RunSummaryView(run.getId(), invited, started, submitted, completionRate);
    }

    @Transactional(readOnly = true)
    public QuestionResultsView questionResults(CurrentUser actor, UUID runId, UUID questionId) {
        authz.requireAnalystOrAdmin(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        FormVersionEntity version = versions
                .findById(run.getFormVersionId())
                .orElseThrow(() -> new DomainException("FORM_VERSION_NOT_FOUND", "Form version not found"));
        Map<String, QuestionMeta> byId = definitionValidator.indexQuestions(
                definitionValidator.parseAndValidate(version.getDefinitionJson()));
        QuestionMeta meta = byId.get(questionId.toString());
        if (meta == null) {
            throw new DomainException("INVALID_QUESTION_REFERENCE", "Question not in published definition");
        }

        List<ResponseAnswerEntity> answers = responses.findSubmittedAnswers(runId, questionId);
        int count = answers.size();
        if (run.getRespondentMode() != RespondentMode.IDENTIFIED
                && count < run.getMinAggregationThreshold()) {
            return new QuestionResultsView(
                    questionId,
                    meta.key(),
                    meta.type(),
                    count,
                    true,
                    "Aggregation suppressed below min_aggregation_threshold",
                    Map.of(),
                    null,
                    null,
                    null);
        }

        Map<String, Long> distribution = new LinkedHashMap<>();
        Double avg = null;
        Map<String, Map<String, Long>> matrixCells = null;
        Map<String, Double> matrixRowAverages = null;

        if (QuestionTypes.isNumeric(meta.type())) {
            double sum = 0;
            int n = 0;
            Map<String, Long> counts = new TreeMap<>((a, b) -> {
                try {
                    return Double.compare(Double.parseDouble(a), Double.parseDouble(b));
                } catch (NumberFormatException ex) {
                    return a.compareTo(b);
                }
            });
            for (ResponseAnswerEntity a : answers) {
                if (a.getNumberValue() != null) {
                    double value = a.getNumberValue();
                    sum += value;
                    n++;
                    String key = stripTrailingZero(value);
                    counts.merge(key, 1L, Long::sum);
                }
            }
            avg = n == 0 ? null : sum / n;
            distribution.putAll(counts);
        } else if (QuestionTypes.isMatrix(meta.type())) {
            Map<String, Map<String, Long>> cells = new LinkedHashMap<>();
            Map<String, Double> rowSums = new LinkedHashMap<>();
            Map<String, Integer> rowCounts = new LinkedHashMap<>();
            for (ResponseAnswerEntity a : answers) {
                String json = a.getJsonValue();
                if (json == null || json.isBlank()) {
                    continue;
                }
                try {
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode cellNode = root.path("cells");
                    if (!cellNode.isObject()) {
                        continue;
                    }
                    cellNode.fields().forEachRemaining(entry -> {
                        String rowKey = entry.getKey();
                        String colValue = entry.getValue().isNumber()
                                ? stripTrailingZero(entry.getValue().asDouble())
                                : entry.getValue().asText();
                        cells.computeIfAbsent(rowKey, ignored -> new LinkedHashMap<>())
                                .merge(colValue, 1L, Long::sum);
                        try {
                            double numeric = Double.parseDouble(colValue);
                            rowSums.merge(rowKey, numeric, Double::sum);
                            rowCounts.merge(rowKey, 1, Integer::sum);
                        } catch (NumberFormatException ignored) {
                            // non-numeric scale labels
                        }
                    });
                } catch (Exception ignored) {
                    // skip malformed
                }
            }
            matrixCells = cells;
            matrixRowAverages = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : rowSums.entrySet()) {
                int n = rowCounts.getOrDefault(e.getKey(), 0);
                if (n > 0) {
                    matrixRowAverages.put(e.getKey(), e.getValue() / n);
                }
            }
            if (!matrixRowAverages.isEmpty()) {
                avg = matrixRowAverages.values().stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                if (Double.isNaN(avg)) {
                    avg = null;
                }
            }
        } else if (QuestionTypes.SINGLE_CHOICE.equals(meta.type())
                || QuestionTypes.DROPDOWN.equals(meta.type())
                || QuestionTypes.isBooleanLike(meta.type())
                || QuestionTypes.isTextLike(meta.type())) {
            Map<String, Long> counts = new HashMap<>();
            for (ResponseAnswerEntity a : answers) {
                String key;
                if (a.getBooleanValue() != null) {
                    key = Boolean.toString(a.getBooleanValue());
                } else {
                    key = a.getTextValue() == null ? "(empty)" : a.getTextValue();
                }
                counts.merge(key, 1L, Long::sum);
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> distribution.put(e.getKey(), e.getValue()));
        } else if (QuestionTypes.isChoiceMulti(meta.type())) {
            Map<String, Long> counts = new HashMap<>();
            for (ResponseAnswerEntity a : answers) {
                String json = a.getJsonValue();
                if (json == null) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(json);
                    if (node.isArray()) {
                        for (JsonNode item : node) {
                            counts.merge(item.asText(), 1L, Long::sum);
                        }
                    }
                } catch (Exception ignored) {
                    // skip malformed
                }
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> distribution.put(e.getKey(), e.getValue()));
        } else if (QuestionTypes.DATE.equals(meta.type())) {
            Map<String, Long> counts = new HashMap<>();
            for (ResponseAnswerEntity a : answers) {
                String key = a.getDateValue() == null ? "(empty)" : a.getDateValue().toString();
                counts.merge(key, 1L, Long::sum);
            }
            counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> distribution.put(e.getKey(), e.getValue()));
        }

        return new QuestionResultsView(
                questionId,
                meta.key(),
                meta.type(),
                count,
                false,
                null,
                distribution,
                avg,
                matrixCells,
                matrixRowAverages);
    }

    private static String stripTrailingZero(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    public record RunSummaryView(
            UUID formRunId, long invited, long started, long submitted, double completionRate) {}

    public record QuestionResultsView(
            UUID questionId,
            String questionKey,
            String questionType,
            int responseCount,
            boolean masked,
            String maskReason,
            Map<String, Long> distribution,
            Double average,
            Map<String, Map<String, Long>> matrixCells,
            Map<String, Double> matrixRowAverages) {}
}
