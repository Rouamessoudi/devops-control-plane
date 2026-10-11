package com.dcp.dockerops;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/docker")
@PreAuthorize("hasRole('INTERNAL')")
public class DockerInternalController {

    private final BuildService builds;
    private final DockerQueryService queries;

    public DockerInternalController(BuildService builds, DockerQueryService queries) {
        this.builds = builds;
        this.queries = queries;
    }

    @PostMapping("/builds")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DockerApi.BuildAccepted create(@Valid @RequestBody DockerApi.BuildRequest request) {
        return builds.acceptInternal(request);
    }

    @GetMapping("/builds/{id}")
    public DockerApi.BuildView get(@PathVariable UUID id) {
        return queries.build(id, true);
    }
}
