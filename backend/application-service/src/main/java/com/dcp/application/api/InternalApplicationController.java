package com.dcp.application.api;

import com.dcp.application.service.ApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/applications")
public class InternalApplicationController {

    private final ApplicationService applicationService;

    public InternalApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable UUID id) {
        return applicationService.getInternal(id);
    }

    @GetMapping("/{id}/env")
    public List<InternalEnvVarView> env(@PathVariable UUID id) {
        return applicationService.listInternalEnv(id);
    }

    @PatchMapping("/{id}/status")
    public ApplicationView updateStatus(@PathVariable UUID id, @RequestBody StatusUpdateRequest request) {
        return applicationService.updateStatus(id, request);
    }
}
