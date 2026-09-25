package com.ravalgroups.forms.invitation.application;

import com.ravalgroups.forms.audit.application.DomainEventRecorder;
import com.ravalgroups.forms.authorization.FormsAuthorizationService;
import com.ravalgroups.forms.invitation.adapter.out.persistence.InvitationEntity;
import com.ravalgroups.forms.invitation.adapter.out.persistence.InvitationJpaRepository;
import com.ravalgroups.forms.notification.application.port.NotificationPort;
import com.ravalgroups.forms.run.adapter.out.persistence.FormRunEntity;
import com.ravalgroups.forms.run.application.FormRunApplicationService;
import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.shared.exception.DomainException;
import com.ravalgroups.forms.shared.id.UuidV7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationApplicationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationJpaRepository invitations;
    private final FormRunApplicationService runs;
    private final FormsAuthorizationService authz;
    private final DomainEventRecorder events;
    private final NotificationPort notifications;

    public InvitationApplicationService(
            InvitationJpaRepository invitations,
            FormRunApplicationService runs,
            FormsAuthorizationService authz,
            DomainEventRecorder events,
            NotificationPort notifications) {
        this.invitations = invitations;
        this.runs = runs;
        this.authz = authz;
        this.events = events;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<InvitationView> list(CurrentUser actor, UUID runId) {
        authz.requirePublisherOrAdmin(actor);
        runs.requireRun(actor, runId);
        return invitations.findByCompanyIdAndFormRunIdOrderByCreatedAtDesc(actor.companyId(), runId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public CreatedInvitationView create(CurrentUser actor, UUID runId, CreateInvitationCommand command) {
        authz.requirePublisherOrAdmin(actor);
        FormRunEntity run = runs.requireRun(actor, runId);
        if (command.respondentReference() == null || command.respondentReference().isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "respondentReference is required");
        }
        Instant now = Instant.now();
        Instant expiresAt = command.expiresAt() == null ? now.plus(14, ChronoUnit.DAYS) : command.expiresAt();
        if (!expiresAt.isAfter(now)) {
            throw new DomainException("VALIDATION_ERROR", "expiresAt must be in the future");
        }
        String rawToken = generateToken();
        InvitationEntity saved = invitations.save(InvitationEntity.create(
                UuidV7.create(),
                actor.companyId(),
                run.getId(),
                command.respondentReference().trim(),
                hashToken(rawToken),
                expiresAt,
                now));

        if (command.sendNotification() && looksLikeEmail(saved.getRespondentReference())) {
            notifications.sendEmail(
                    saved.getRespondentReference(),
                    "You are invited to complete a form",
                    "You have been invited to participate in \"" + run.getName()
                            + "\". Use your invitation token securely. Token expires at "
                            + expiresAt
                            + ".",
                    "forms.invitation.created");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invitationId", saved.getId().toString());
        payload.put("formRunId", run.getId().toString());
        events.record(
                actor.companyId(),
                actor.userId(),
                "forms.invitation.created",
                "Invitation",
                saved.getId(),
                "forms.invitation.created",
                payload);

        // Raw token returned once — never stored.
        return new CreatedInvitationView(toView(saved), rawToken);
    }

    @Transactional
    public InvitationView revoke(CurrentUser actor, UUID invitationId) {
        authz.requirePublisherOrAdmin(actor);
        InvitationEntity invitation = invitations
                .findByIdAndCompanyId(invitationId, actor.companyId())
                .orElseThrow(() -> new DomainException("INVITATION_NOT_FOUND", "Invitation not found"));
        invitation.revoke(Instant.now());
        return toView(invitations.save(invitation));
    }

    /**
     * Consumes a raw invitation token. Returns invitation metadata for starting a response.
     * Does not start the response — caller uses runId from the view.
     */
    @Transactional
    public InvitationConsumeView consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DomainException("VALIDATION_ERROR", "token is required");
        }
        Instant now = Instant.now();
        InvitationEntity invitation = invitations
                .findByTokenHash(hashToken(rawToken.trim()))
                .orElseThrow(() -> new DomainException("INVITATION_NOT_FOUND", "Invitation not found"));
        invitation.expireIfNeeded(now);
        invitation.markUsed(now);
        invitations.save(invitation);
        return new InvitationConsumeView(
                invitation.getId(),
                invitation.getCompanyId(),
                invitation.getFormRunId(),
                invitation.getStatus().name(),
                invitation.getUsedAt());
    }

    @Scheduled(fixedDelayString = "${forms.invitation.expire-poll-interval-ms:60000}")
    @Transactional
    public void expirePending() {
        Instant now = Instant.now();
        for (InvitationEntity invitation :
                invitations.findExpiredPending(now, org.springframework.data.domain.PageRequest.of(0, 100))) {
            invitation.expireIfNeeded(now);
            invitations.save(invitation);
        }
    }

    private InvitationView toView(InvitationEntity e) {
        return new InvitationView(
                e.getId(),
                e.getCompanyId(),
                e.getFormRunId(),
                e.getRespondentReference(),
                e.getStatus().name(),
                e.getExpiresAt(),
                e.getUsedAt(),
                e.getCreatedAt());
    }

    private static String generateToken() {
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
            throw new DomainException("INTERNAL_ERROR", "Unable to hash invitation token");
        }
    }

    private static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@") && value.length() > 3;
    }

    public record CreateInvitationCommand(String respondentReference, Instant expiresAt, boolean sendNotification) {}

    public record InvitationView(
            UUID id,
            UUID companyId,
            UUID formRunId,
            String respondentReference,
            String status,
            Instant expiresAt,
            Instant usedAt,
            Instant createdAt) {}

    public record CreatedInvitationView(InvitationView invitation, String token) {}

    public record InvitationConsumeView(
            UUID invitationId, UUID companyId, UUID formRunId, String status, Instant usedAt) {}
}
