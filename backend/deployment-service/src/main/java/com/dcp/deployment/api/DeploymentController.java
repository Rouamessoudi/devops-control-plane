package com.dcp.deployment.api;

import com.dcp.common.web.PageResponse;
import com.dcp.deployment.service.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public ResponseEntity<DeploymentView> create(@RequestBody CreateDeploymentRequest request) {
        return ResponseEntity.accepted().body(deploymentService.createForUser(request));
    }

    @GetMapping
    public PageResponse<DeploymentView> list(@RequestParam(required = false) UUID applicationId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return deploymentService.list(applicationId, page, size);
    }

    @GetMapping("/audit-events")
    public PageResponse<DeploymentEventView> auditEvents(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return deploymentService.auditEvents(page, size);
    }

    @GetMapping("/{id}")
    public DeploymentView get(@PathVariable UUID id) {
        return deploymentService.get(id);
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<DeploymentView> rollback(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(deploymentService.rollback(id));
    }

    @GetMapping("/{id}/revisions")
    public List<DeploymentView> revisions(@PathVariable UUID id) {
        return deploymentService.revisions(id);
    }
}
