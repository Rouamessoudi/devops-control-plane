package com.dcp.deployment.api;

import com.dcp.deployment.service.DeploymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/deployments")
public class InternalDeploymentController {

    private final DeploymentService deploymentService;

    public InternalDeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public ResponseEntity<DeploymentView> create(@RequestBody InternalDeploymentRequest request) {
        return ResponseEntity.accepted().body(deploymentService.createForInternal(request));
    }

    @GetMapping("/{id}")
    public DeploymentView get(@PathVariable UUID id) {
        return deploymentService.getInternal(id);
    }
}
