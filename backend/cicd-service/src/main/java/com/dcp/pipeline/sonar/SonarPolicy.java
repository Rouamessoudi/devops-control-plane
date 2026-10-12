package com.dcp.pipeline.sonar;

import com.dcp.pipeline.api.QualityView;
import com.dcp.pipeline.config.SonarProperties;
import org.springframework.stereotype.Service;

@Service
public class SonarPolicy {

    private final SonarProperties properties;
    private final SonarProbe probe;

    public SonarPolicy(SonarProperties properties, SonarProbe probe) {
        this.properties = properties;
        this.probe = probe;
    }

    public SonarDecision evaluate() {
        String mode = properties.mode();
        if ("off".equals(mode)) {
            return SonarDecision.skip("SonarQube is not required (DCP_SONAR_MODE=off). This project is not analyzed and deployment continues.");
        }
        QualityView status = probe.read();
        String gate = status.qualityGate();
        if (!status.available() || gate == null || gate.isBlank()) {
            if ("required".equals(mode)) {
                return SonarDecision.block(status.message());
            }
            return SonarDecision.skip(status.message() + " DCP_SONAR_MODE=optional, deployment continues.");
        }
        if ("OK".equalsIgnoreCase(gate)) {
            return SonarDecision.pass(status.message());
        }
        if ("required".equals(mode)) {
            return SonarDecision.block("Quality gate " + gate + ". Deployment is blocked.");
        }
        return SonarDecision.skip("Quality gate " + gate + ". DCP_SONAR_MODE=optional, deployment continues.");
    }

    public record SonarDecision(String action, String log) {
        public static SonarDecision skip(String log) {
            return new SonarDecision("skip", log);
        }

        public static SonarDecision pass(String log) {
            return new SonarDecision("pass", log);
        }

        public static SonarDecision block(String log) {
            return new SonarDecision("block", log);
        }

        public boolean skip() {
            return "skip".equals(action);
        }

        public boolean pass() {
            return "pass".equals(action);
        }
    }
}
