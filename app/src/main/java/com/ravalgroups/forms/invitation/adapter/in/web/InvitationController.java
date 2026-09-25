package com.ravalgroups.forms.invitation.adapter.in.web;

import com.ravalgroups.forms.invitation.application.InvitationApplicationService;
import com.ravalgroups.forms.invitation.application.InvitationApplicationService.CreateInvitationCommand;
import com.ravalgroups.forms.invitation.application.InvitationApplicationService.CreatedInvitationView;
import com.ravalgroups.forms.invitation.application.InvitationApplicationService.InvitationConsumeView;
import com.ravalgroups.forms.invitation.application.InvitationApplicationService.InvitationView;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Invitations")
public class InvitationController {

    private final InvitationApplicationService invitations;

    public InvitationController(InvitationApplicationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping("/api/v1/runs/{runId}/invitations")
    @SecurityRequirement(name = "bearer-jwt")
    public List<InvitationView> list(@PathVariable UUID runId) {
        return invitations.list(CurrentUser.require(), runId);
    }

    @PostMapping("/api/v1/runs/{runId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearer-jwt")
    public CreatedInvitationView create(@PathVariable UUID runId, @RequestBody CreateInvitationRequest request) {
        boolean sendNotification = request.sendNotification() == null || request.sendNotification();
        return invitations.create(
                CurrentUser.require(),
                runId,
                new CreateInvitationCommand(request.respondentReference(), request.expiresAt(), sendNotification));
    }

    @PostMapping("/api/v1/invitations/{invitationId}/revoke")
    @SecurityRequirement(name = "bearer-jwt")
    public InvitationView revoke(@PathVariable UUID invitationId) {
        return invitations.revoke(CurrentUser.require(), invitationId);
    }

    /**
     * Token consume is authenticated (eligibility) but does not expose respondent identity on the
     * eventual anonymous response. Requires a valid Forms service JWT.
     */
    @PostMapping("/api/v1/invitations/consume")
    @SecurityRequirement(name = "bearer-jwt")
    public InvitationConsumeView consume(@RequestBody ConsumeRequest request) {
        CurrentUser.require();
        return invitations.consume(request.token());
    }

    public record CreateInvitationRequest(String respondentReference, Instant expiresAt, Boolean sendNotification) {}

    public record ConsumeRequest(@NotBlank String token) {}
}
