package com.dcp.dockerops;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/docker")
public class DockerBuildController {

    private final BuildService builds;
    private final DockerQueryService queries;

    public DockerBuildController(BuildService builds, DockerQueryService queries) {
        this.builds = builds;
        this.queries = queries;
    }

    @PostMapping("/builds")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DockerApi.BuildAccepted create(@Valid @RequestBody DockerApi.UserBuildRequest request) {
        return builds.acceptFromUser(request);
    }

    @GetMapping("/builds/{id}")
    public DockerApi.BuildView get(@PathVariable UUID id) {
        return queries.build(id, false);
    }

    @GetMapping("/builds")
    public List<DockerApi.BuildView> list(@RequestParam(required = false) UUID applicationId) {
        return queries.builds(applicationId);
    }

    @GetMapping("/images")
    public DockerApi.ImageListing images() {
        return queries.images();
    }

    @GetMapping("/containers")
    public List<DockerApi.ContainerView> containers() {
        return queries.containers();
    }

    @GetMapping(value = "/containers/{id}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String logs(@PathVariable String id) {
        return queries.logs(id);
    }

    @GetMapping("/audit-events")
    public List<DockerApi.BuildAuditEvent> audit() {
        return queries.audit();
    }
}
