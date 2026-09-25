package com.ravalgroups.forms.export.adapter.in.web;

import com.ravalgroups.forms.export.application.ExportApplicationService;
import com.ravalgroups.forms.export.application.ExportApplicationService.ExportView;
import com.ravalgroups.forms.export.application.ExportApplicationService.FileDownload;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Exports")
@SecurityRequirement(name = "bearer-jwt")
public class ExportController {

    private final ExportApplicationService exports;

    public ExportController(ExportApplicationService exports) {
        this.exports = exports;
    }

    @PostMapping("/api/v1/runs/{runId}/exports")
    @ResponseStatus(HttpStatus.CREATED)
    public ExportView create(@PathVariable UUID runId, @RequestBody(required = false) CreateExportRequest request) {
        String format = request == null ? "CSV" : request.format();
        return exports.create(CurrentUser.require(), runId, format);
    }

    @GetMapping("/api/v1/exports/{exportId}")
    public ExportView get(@PathVariable UUID exportId) {
        return exports.get(CurrentUser.require(), exportId);
    }

    @GetMapping("/api/v1/exports/{exportId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID exportId) {
        FileDownload download = exports.download(CurrentUser.require(), exportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(new InputStreamResource(download.content()));
    }

    public record CreateExportRequest(String format) {}
}
