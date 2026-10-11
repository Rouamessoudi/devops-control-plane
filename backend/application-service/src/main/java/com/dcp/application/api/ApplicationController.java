package com.dcp.application.api;

import com.dcp.application.service.ApplicationService;
import com.dcp.common.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationView> create(@RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(request));
    }

    @GetMapping
    public PageResponse<ApplicationView> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String status) {
        return applicationService.list(page, size, status);
    }

    @GetMapping("/audit-events")
    public PageResponse<HistoryView> auditEvents(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return applicationService.auditEvents(page, size);
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable UUID id) {
        return applicationService.get(id);
    }

    @PutMapping("/{id}")
    public ApplicationView update(@PathVariable UUID id, @RequestBody ApplicationRequest request) {
        return applicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/history")
    public List<HistoryView> history(@PathVariable UUID id) {
        return applicationService.history(id);
    }

    @PostMapping("/{id}/env")
    public ResponseEntity<EnvVarView> addEnv(@PathVariable UUID id, @RequestBody EnvVarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.addEnv(id, request));
    }

    @GetMapping("/{id}/env")
    public List<EnvVarView> listEnv(@PathVariable UUID id) {
        return applicationService.listEnv(id);
    }

    @DeleteMapping("/{id}/env/{envId}")
    public ResponseEntity<Void> deleteEnv(@PathVariable UUID id, @PathVariable UUID envId) {
        applicationService.deleteEnv(id, envId);
        return ResponseEntity.noContent().build();
    }
}
