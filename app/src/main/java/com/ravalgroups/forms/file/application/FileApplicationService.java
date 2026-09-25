package com.ravalgroups.forms.file.application;

import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.authorization.FormsPermissionCode;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileEntity;
import com.ravalgroups.forms.file.adapter.out.persistence.StoredFileJpaRepository;
import com.ravalgroups.forms.file.application.port.FileStoragePort;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileApplicationService {

    private static final long MAX_BYTES = 15_000_000L;

    private final FileStoragePort storage;
    private final StoredFileJpaRepository files;
    private final FormsAuthorizationService authz;

    public FileApplicationService(
            FileStoragePort storage, StoredFileJpaRepository files, FormsAuthorizationService authz) {
        this.storage = storage;
        this.files = files;
        this.authz = authz;
    }

    @Transactional
    public FileMetaView upload(CurrentUser actor, MultipartFile file) {
        authz.requireAssigned(actor);
        if (file == null || file.isEmpty()) {
            throw new DomainException("VALIDATION_ERROR", "File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("VALIDATION_ERROR", "File exceeds 15MB limit");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        try (InputStream in = file.getInputStream()) {
            FileStoragePort.StoredFile stored = storage.store(contentType, in, file.getSize());
            StoredFileEntity entity = files.save(StoredFileEntity.create(
                    UuidV7.create(),
                    actor.companyId(),
                    stored.storageKey(),
                    stored.contentType(),
                    stored.sizeBytes(),
                    file.getOriginalFilename(),
                    actor.userId(),
                    Instant.now()));
            return toView(entity);
        } catch (IOException ex) {
            throw new DomainException("STORAGE_ERROR", "Failed to store file");
        }
    }

    @Transactional(readOnly = true)
    public FileDownload download(CurrentUser actor, UUID fileId) {
        authz.requireAssigned(actor);
        StoredFileEntity entity = files.findByIdAndCompanyId(fileId, actor.companyId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "File not found"));
        if (!entity.getCreatedBy().equals(actor.userId())
                && !authz.hasAnyPermission(
                        actor,
                        FormsPermissionCode.FILES_MANAGE,
                        FormsPermissionCode.REPORTS_READ,
                        FormsPermissionCode.VERSIONS_WRITE)) {
            throw new DomainException("FORBIDDEN", "Not permitted to download this file");
        }
        try {
            return new FileDownload(
                    entity.getOriginalFilename() == null ? entity.getStorageKey() : entity.getOriginalFilename(),
                    entity.getContentType(),
                    storage.open(entity.getStorageKey()));
        } catch (IOException ex) {
            throw new DomainException("STORAGE_ERROR", "Failed to open file");
        }
    }

    @Transactional
    public void delete(CurrentUser actor, UUID fileId) {
        authz.requireAssigned(actor);
        StoredFileEntity entity = files.findByIdAndCompanyId(fileId, actor.companyId())
                .orElseThrow(() -> new DomainException("NOT_FOUND", "File not found"));
        if (!entity.getCreatedBy().equals(actor.userId())
                && !authz.hasAnyPermission(
                        actor, FormsPermissionCode.FILES_MANAGE, FormsPermissionCode.VERSIONS_WRITE)) {
            throw new DomainException("FORBIDDEN", "Not permitted to delete this file");
        }
        try {
            storage.delete(entity.getStorageKey());
        } catch (IOException ignored) {
            // metadata still removed; orphan GC can reclaim later
        }
        files.delete(entity);
    }

    private FileMetaView toView(StoredFileEntity e) {
        return new FileMetaView(
                e.getId(),
                e.getStorageKey(),
                e.getOriginalFilename(),
                e.getContentType(),
                e.getSizeBytes(),
                e.getCreatedAt());
    }

    public record FileMetaView(
            UUID id,
            String storageKey,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Instant createdAt) {}

    public record FileDownload(String filename, String contentType, InputStream content) {}
}
