package com.dcp.monitoring.api;

import com.dcp.monitoring.api.MonitoringDtos.MonitorTargetView;
import com.dcp.monitoring.api.MonitoringDtos.UpsertTargetRequest;
import com.dcp.monitoring.service.MonitoringService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/monitoring")
public class InternalMonitoringController {

    private final MonitoringService monitoring;

    public InternalMonitoringController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @PostMapping("/targets")
    public MonitorTargetView upsert(@Valid @RequestBody UpsertTargetRequest request) {
        return monitoring.upsert(request);
    }
}
