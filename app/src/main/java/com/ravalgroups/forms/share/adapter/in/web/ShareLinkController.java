package com.ravalgroups.forms.share.adapter.in.web;

import com.ravalgroups.forms.security.CurrentUser;
import com.ravalgroups.forms.share.application.ShareLinkApplicationService;
import com.ravalgroups.forms.share.application.ShareLinkApplicationService.CreateShareLinkCommand;
import com.ravalgroups.forms.share.application.ShareLinkApplicationService.CreatedShareLinkView;
import com.ravalgroups.forms.share.application.ShareLinkApplicationService.ShareLinkView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Share links")
@SecurityRequirement(name = "bearer-jwt")
public class ShareLinkController {

    private final ShareLinkApplicationService shareLinks;

    public ShareLinkController(ShareLinkApplicationService shareLinks) {
        this.shareLinks = shareLinks;
    }

    @GetMapping("/runs/{runId}/share-links")
    public List<ShareLinkView> list(@PathVariable UUID runId) {
        return shareLinks.list(CurrentUser.require(), runId);
    }

    @PostMapping("/runs/{runId}/share-links")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedShareLinkView create(@PathVariable UUID runId, @RequestBody(required = false) CreateShareLinkRequest request) {
        CreateShareLinkRequest body = request == null ? new CreateShareLinkRequest(null, null, null) : request;
        return shareLinks.create(
                CurrentUser.require(),
                runId,
                new CreateShareLinkCommand(body.label(), body.expiresAt(), body.maxResponses()));
    }

    @PostMapping("/share-links/{shareLinkId}/revoke")
    public ShareLinkView revoke(@PathVariable UUID shareLinkId) {
        return shareLinks.revoke(CurrentUser.require(), shareLinkId);
    }

    public record CreateShareLinkRequest(String label, Instant expiresAt, Integer maxResponses) {}
}
