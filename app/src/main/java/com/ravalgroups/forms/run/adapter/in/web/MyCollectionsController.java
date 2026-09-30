package com.ravalgroups.forms.run.adapter.in.web;

import com.ravalgroups.forms.run.application.RunAudienceService;
import com.ravalgroups.forms.run.application.RunAudienceService.MyCollectionView;
import com.ravalgroups.forms.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Me")
@SecurityRequirement(name = "bearer-jwt")
public class MyCollectionsController {

    private final RunAudienceService audience;

    public MyCollectionsController(RunAudienceService audience) {
        this.audience = audience;
    }

    @GetMapping("/api/v1/me/collections")
    public List<MyCollectionView> listMine() {
        return audience.listMyCollections(CurrentUser.require());
    }
}
