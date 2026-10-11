package com.dcp.pipeline.api;

import com.dcp.common.error.ApiException;
import com.dcp.common.web.PageResponse;
import com.dcp.pipeline.api.PipelineDtos.CreatePipelineRequest;
import com.dcp.pipeline.api.PipelineDtos.PipelineAuditEvent;
import com.dcp.pipeline.api.PipelineDtos.PipelineView;
import com.dcp.pipeline.service.PipelineService;
import com.dcp.pipeline.service.PipelineStore;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/pipelines")
public class PipelineController {

    private final PipelineService pipelines;
    private final PipelineStore store;

    public PipelineController(PipelineService pipelines, PipelineStore store) {
        this.pipelines = pipelines;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<PipelineView> create(@Valid @RequestBody CreatePipelineRequest request) {
        return ResponseEntity.accepted().body(pipelines.start(request.applicationId()));
    }

    @GetMapping
    public PageResponse<PipelineView> list(@RequestParam(required = false) UUID applicationId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("page must be >= 0 and size must be between 1 and 100");
        }
        return store.list(applicationId, page, size);
    }

    @GetMapping("/audit-events")
    public List<PipelineAuditEvent> audit() {
        return store.audit();
    }

    @GetMapping("/{id}")
    public PipelineView get(@PathVariable UUID id) {
        return store.get(id);
    }

    @GetMapping(value = "/{id}/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String logs(@PathVariable UUID id) {
        return store.logs(id);
    }
}
