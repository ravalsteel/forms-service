package com.ravalgroups.forms.share.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.response.adapter.out.persistence.ResponseJpaRepository;
import com.ravalgroups.forms.response.domain.ResponseStatus;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.run.domain.RespondentMode;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkEntity;
import com.ravalgroups.forms.share.adapter.out.persistence.ShareLinkJpaRepository;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShareLinkApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkJpaRepository shareLinks;
    private final FormRunApplicationService runs;
    private final ResponseJpaRepository responses;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;

    public ShareLinkApplicationService(
            ShareLinkJpaRepository shareLinks,
            FormRunApplicationService runs,
            ResponseJpaRepository responses,
            FormsAuthorizationService authz,
            DomainEventRecorder events) {
        this.shareLinks = shareLinks;
        this.runs = runs;
        this.responses = responses;
        this.authz = authz;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ShareLinkView> list(CurrentUser actor, UUID runId) {
        authz.requirePublisherOrAdmin(actor);
        runs.requireRun(actor, runId);
        Instant now = Instant.now();
        return shareLinks.findByCompanyIdAndFormRunIdOrderByCreatedAtDesc(actor.companyId(), runId).stream()
                .map(link -> {
                    link.expireIfNeeded(now);
                    return toView(link, submittedCount(link.getFormRunId()));
                })
                .toList();
    }

    @Transactional
    public CreatedShareLinkView create(CurrentUser actor, UUID runId, CreateShareLinkCommand command) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        if (run.getRespondentMode() != RespondentMode.ANONYMOUS) {
            throw new DomainException(
                    "VALIDATION_ERROR",
                    "Public share links require an ANONYMOUS run (identified runs stay in-app only)");
        }
        Instant now = Instant.now();
        Instant expiresAt = command.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new DomainException("VALIDATION_ERROR", "expiresAt must be in the future");
        }
        Integer maxResponses = command.maxResponses();
        if (maxResponses != null && maxResponses <= 0) {
            throw new DomainException("VALIDATION_ERROR", "maxResponses must be greater than zero");
        }
        String rawToken = generateToken();
        String label = command.label() == null || command.label().isBlank() ? null : command.label().trim();
        ShareLinkEntity saved = shareLinks.save(ShareLinkEntity.create(
                UuidV7.create(),
                actor.companyId(),
                run.getId(),
                hashToken(rawToken),
                label,
                expiresAt,
                maxResponses,
                actor.userId(),
                now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shareLinkId", saved.getId().toString());
        payload.put("formRunId", run.getId().toString());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.share_link.created",
                "ShareLink",
                saved.getId(),
                "forms.share_link.created",
                payload);

        return new CreatedShareLinkView(toView(saved, submittedCount(run.getId())), rawToken);
    }

    @Transactional
    public ShareLinkView revoke(CurrentUser actor, UUID shareLinkId) {
        authz.requirePublisherOrAdmin(actor);
        ShareLinkEntity link = shareLinks
                .findByIdAndCompanyId(shareLinkId, actor.companyId())
                .orElseThrow(() -> new DomainException("SHARE_LINK_NOT_FOUND", "Share link not found"));
        runs.requireRun(actor, link.getFormRunId());
        Instant now = Instant.now();
        link.revoke(now);
        ShareLinkEntity saved = shareLinks.save(link);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shareLinkId", saved.getId().toString());
        payload.put("formRunId", saved.getFormRunId().toString());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.share_link.revoked",
                "ShareLink",
                saved.getId(),
                "forms.share_link.revoked",
                payload);
        return toView(saved, submittedCount(saved.getFormRunId()));
    }

    private long submittedCount(UUID formRunId) {
        return responses.countByFormRunIdAndStatus(formRunId, ResponseStatus.SUBMITTED);
    }

    private ShareLinkView toView(ShareLinkEntity e, long submittedCount) {
        return new ShareLinkView(
                e.getId(),
                e.getCompanyId(),
                e.getFormRunId(),
                e.getStatus().name(),
                e.getLabel(),
                e.getExpiresAt(),
                e.getMaxResponses(),
                submittedCount,
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getRevokedAt());
    }

    static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception ex) {
            throw new DomainException("INTERNAL_ERROR", "Unable to hash share link token");
        }
    }

    public record CreateShareLinkCommand(String label, Instant expiresAt, Integer maxResponses) {}

    public record ShareLinkView(
            UUID id,
            UUID companyId,
            UUID formRunId,
            String status,
            String label,
            Instant expiresAt,
            Integer maxResponses,
            long submittedCount,
            UUID createdBy,
            Instant createdAt,
            Instant revokedAt) {}

    public record CreatedShareLinkView(ShareLinkView shareLink, String token) {}
}
