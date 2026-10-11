package com.dcp.monitoring.api;

import com.dcp.monitoring.api.MonitoringDtos.GrafanaView;
import com.dcp.monitoring.api.MonitoringDtos.MonitorTargetView;
import com.dcp.monitoring.api.MonitoringDtos.OverviewView;
import com.dcp.monitoring.api.MonitoringDtos.SeriesView;
import com.dcp.monitoring.api.MonitoringDtos.TargetAuditEvent;
import com.dcp.monitoring.service.MonitoringService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final MonitoringService monitoring;

    public MonitoringController(MonitoringService monitoring) {
        this.monitoring = monitoring;
    }

    @GetMapping("/overview")
    public OverviewView overview() {
        return monitoring.overview();
    }

    @GetMapping("/series")
    public SeriesView series(@RequestParam(required = false) String metric,
                             @RequestParam(required = false) String range) {
        return monitoring.series(metric, range);
    }

    @GetMapping("/grafana")
    public GrafanaView grafana() {
        return monitoring.grafana();
    }

    @GetMapping("/targets")
    public List<MonitorTargetView> targets() {
        return monitoring.targets();
    }

    @GetMapping("/logs")
    public void logs(@RequestParam(required = false) String pod,
                     @RequestParam(required = false) Integer tail,
                     HttpServletResponse response) throws IOException {
        write(response, "text/plain;charset=UTF-8", monitoring.logs(pod, tail));
    }

    @GetMapping("/events")
    public void events(HttpServletResponse response) throws IOException {
        write(response, "application/json;charset=UTF-8", monitoring.events());
    }

    @GetMapping("/audit-events")
    public List<TargetAuditEvent> audit() {
        return monitoring.audit();
    }

    private static void write(HttpServletResponse response, String contentType, String body) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(contentType);
        response.getWriter().write(body == null ? "" : body);
    }
}
