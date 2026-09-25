package com.ravalgroups.forms.export.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.export.adapter.out.persistence.ExportJobEntity;
import com.ravalgroups.forms.export.adapter.out.persistence.ExportJobJpaRepository;
import com.ravalgroups.forms.export.domain.ExportJobStatus;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileEntity;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileJpaRepository;
import com.ravalgroups.forms.file.application.port.FileStoragePort;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseAnswerEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseEntity;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExportApplicationService.class);

    private final ExportJobJpaRepository exports;
    private final FormRunApplicationService runs;
    private final ResponseJpaRepository responses;
    private final FileStoragePort storage;
    private final StoredFileJpaRepository files;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;
    private final ObjectMapper objectMapper;

    public ExportApplicationService(
            ExportJobJpaRepository exports,
            FormRunApplicationService runs,
            ResponseJpaRepository responses,
            FileStoragePort storage,
            StoredFileJpaRepository files,
            FormsAuthorizationService authz,
            DomainEventRecorder events,
            ObjectMapper objectMapper) {
        this.exports = exports;
        this.runs = runs;
        this.responses = responses;
        this.storage = storage;
        this.files = files;
        this.authz = authz;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportView create(CurrentUser actor, UUID runId, String formatRaw) {
        authz.requireAnalystOrAdmin(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        if (run.getRespondentMode() == RespondentMode.ANONYMOUS) {
            throw new DomainException(
                    "ANONYMOUS_RESPONSE_ACCESS_DENIED", "Raw anonymous response export is not permitted");
        }
        String format = formatRaw == null || formatRaw.isBlank() ? "CSV" : formatRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX", "JSON").contains(format)) {
            throw new DomainException("VALIDATION_ERROR", "Unsupported export format");
        }
        Instant now = Instant.now();
        ExportJobEntity job = exports.save(ExportJobEntity.create(
                UuidV7.create(), actor.companyId(), runId, format, actor.userId(), now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportId", job.getId().toString());
        payload.put("formRunId", runId.toString());
        payload.put("format", format);
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.export.requested",
                "ExportJob",
                job.getId(),
                "forms.export.requested",
                payload);
        return toView(job);
    }

    @Transactional(readOnly = true)
    public ExportView get(CurrentUser actor, UUID exportId) {
        authz.requireAnalystOrAdmin(actor);
        return toView(requireExport(actor, exportId));
    }

    @Transactional(readOnly = true)
    public FileDownload download(CurrentUser actor, UUID exportId) {
        authz.requireAnalystOrAdmin(actor);
        ExportJobEntity job = requireExport(actor, exportId);
        if (job.getStatus() != ExportJobStatus.COMPLETED || job.getObjectId() == null) {
            throw new DomainException("EXPORT_NOT_READY", "Export is not ready for download");
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(Instant.now())) {
            throw new DomainException("EXPORT_NOT_READY", "Export has expired");
        }
        StoredFileEntity file = files.findByIdAndCompanyId(job.getObjectId(), actor.companyId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "Export file not found"));
        try {
            return new FileDownload(
                    file.getOriginalFilename() == null ? "export.bin" : file.getOriginalFilename(),
                    file.getContentType(),
                    storage.open(file.getStorageKey()));
        } catch (IOException ex) {
            throw new DomainException("STORAGE_ERROR", "Failed to open export file");
        }
    }

    @Scheduled(fixedDelayString = "${forms.export.poll-interval-ms:5000}")
    @Transactional
    public void processPending() {
        for (ExportJobEntity job : exports.findPending(PageRequest.of(0, 5))) {
            try {
                job.markProcessing();
                exports.save(job);
                switch (job.getFormat()) {
                    case "CSV" -> completeCsv(job);
                    case "XLSX" -> completeXlsx(job);
                    case "JSON" -> completeJson(job);
                    default -> throw new DomainException("VALIDATION_ERROR", "Unsupported export format");
                }
            } catch (Exception ex) {
                log.warn("Export failed id={}: {}", job.getId(), ex.getMessage());
                job.fail(ex.getMessage(), Instant.now());
                exports.save(job);
            }
        }
    }

    private void completeCsv(ExportJobEntity job) throws IOException {
        byte[] bytes = buildCsv(loadSubmitted(job.getFormRunId())).getBytes(StandardCharsets.UTF_8);
        storeCompleted(job, bytes, "text/csv", "export-" + job.getId() + ".csv");
    }

    private void completeJson(ExportJobEntity job) throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ResponseEntity response : loadSubmitted(job.getFormRunId())) {
            for (ResponseAnswerEntity answer : response.getAnswers()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("responseId", response.getId().toString());
                row.put("questionKey", answer.getQuestionKey());
                row.put("valueType", answer.getValueType());
                row.put("value", renderValue(answer));
                rows.add(row);
            }
        }
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rows);
        storeCompleted(job, bytes, "application/json", "export-" + job.getId() + ".json");
    }

    private void completeXlsx(ExportJobEntity job) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("responses");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("response_id");
            header.createCell(1).setCellValue("question_key");
            header.createCell(2).setCellValue("value_type");
            header.createCell(3).setCellValue("value");
            int rowIdx = 1;
            for (ResponseEntity response : loadSubmitted(job.getFormRunId())) {
                for (ResponseAnswerEntity answer : response.getAnswers()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(response.getId().toString());
                    row.createCell(1).setCellValue(answer.getQuestionKey());
                    row.createCell(2).setCellValue(answer.getValueType());
                    row.createCell(3).setCellValue(renderValue(answer));
                }
            }
            workbook.write(out);
            workbook.dispose();
            storeCompleted(
                    job,
                    out.toByteArray(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "export-" + job.getId() + ".xlsx");
        }
    }

    private List<ResponseEntity> loadSubmitted(UUID formRunId) {
        return responses.findByFormRunIdAndStatus(formRunId, ResponseStatus.SUBMITTED);
    }

    private String buildCsv(List<ResponseEntity> submitted) {
        StringBuilder csv = new StringBuilder("response_id,question_key,value_type,value\n");
        for (ResponseEntity response : submitted) {
            for (ResponseAnswerEntity answer : response.getAnswers()) {
                csv.append(csvEscape(response.getId().toString()))
                        .append(',')
                        .append(csvEscape(answer.getQuestionKey()))
                        .append(',')
                        .append(csvEscape(answer.getValueType()))
                        .append(',')
                        .append(csvEscape(renderValue(answer)))
                        .append('\n');
            }
        }
        return csv.toString();
    }

    private void storeCompleted(ExportJobEntity job, byte[] bytes, String contentType, String filename)
            throws IOException {
        FileStoragePort.StoredFile stored =
                storage.store(contentType, new ByteArrayInputStream(bytes), bytes.length);
        Instant now = Instant.now();
        StoredFileEntity file = files.save(StoredFileEntity.create(
                UuidV7.create(),
                job.getCompanyId(),
                stored.storageKey(),
                stored.contentType(),
                stored.sizeBytes(),
                filename,
                job.getCreatedBy(),
                now));
        job.complete(file.getId(), now, now.plus(7, ChronoUnit.DAYS));
        exports.save(job);
    }

    private static String renderValue(ResponseAnswerEntity answer) {
        if (answer.getTextValue() != null) {
            return answer.getTextValue();
        }
        if (answer.getNumberValue() != null) {
            return answer.getNumberValue().toString();
        }
        if (answer.getBooleanValue() != null) {
            return answer.getBooleanValue().toString();
        }
        if (answer.getDateValue() != null) {
            return answer.getDateValue().toString();
        }
        if (answer.getDatetimeValue() != null) {
            return answer.getDatetimeValue().toString();
        }
        if (answer.getJsonValue() != null) {
            return answer.getJsonValue();
        }
        if (answer.getObjectId() != null) {
            return answer.getObjectId().toString();
        }
        return "";
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private ExportJobEntity requireExport(CurrentUser actor, UUID exportId) {
        ExportJobEntity job = exports.findByIdAndCompanyId(exportId, actor.companyId())
                .orElseThrow(() -> new DomainException("EXPORT_NOT_FOUND", "Export not found"));
        authz.requireCompany(actor, job.getCompanyId());
        return job;
    }

    private ExportView toView(ExportJobEntity e) {
        return new ExportView(
                e.getId(),
                e.getCompanyId(),
                e.getFormRunId(),
                e.getStatus().name(),
                e.getFormat(),
                e.getObjectId(),
                e.getErrorMessage(),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getCompletedAt(),
                e.getExpiresAt());
    }

    public record ExportView(
            UUID id,
            UUID companyId,
            UUID formRunId,
            String status,
            String format,
            UUID objectId,
            String errorMessage,
            UUID createdBy,
            Instant createdAt,
            Instant completedAt,
            Instant expiresAt) {}

    public record FileDownload(String filename, String contentType, java.io.InputStream content) {}
}
