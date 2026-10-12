package com.dcp.pipeline.sonar;

import com.dcp.pipeline.api.QualityView;
import com.dcp.pipeline.config.SonarProperties;
import com.dcp.pipeline.sonar.SonarPolicy.SonarDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonarPolicyTest {

    @Test
    void offModeSkipsWithoutReadingSonar() {
        SonarDecision decision = policy("off", new QualityView(true, "off", "demo", "ERROR", null, null, null, null, null, null, null, "ignored")).evaluate();

        assertTrue(decision.skip());
        assertTrue(decision.log().contains("DCP_SONAR_MODE=off"));
    }

    @Test
    void requiredModeBlocksWhenSonarIsUnreachable() {
        SonarDecision decision = policy("required", new QualityView(false, "required", "demo", null, null, null, null, null, null, null, null, "down")).evaluate();

        assertEquals("block", decision.action());
        assertEquals("down", decision.log());
    }

    @Test
    void requiredModeBlocksAFailedGate() {
        SonarDecision decision = policy("required", new QualityView(true, "required", "demo", "ERROR", 10.0, 1, 0, 2, null, null, null, "failed")).evaluate();

        assertEquals("block", decision.action());
        assertTrue(decision.log().contains("ERROR"));
    }

    @Test
    void optionalModeContinuesWhenTheGateFails() {
        SonarDecision decision = policy("optional", new QualityView(true, "optional", "demo", "ERROR", null, null, null, null, null, null, null, "failed")).evaluate();

        assertTrue(decision.skip());
        assertTrue(decision.log().contains("optional"));
    }

    @Test
    void anOkGatePasses() {
        SonarDecision decision = policy("required", new QualityView(true, "required", "demo", "OK", 40.0, 0, 0, 1, null, null, null, "Quality gate read from SonarQube: OK")).evaluate();

        assertTrue(decision.pass());
    }

    private static SonarPolicy policy(String mode, QualityView view) {
        SonarProperties properties = new SonarProperties();
        properties.setMode(mode);
        return new SonarPolicy(properties, new SonarProbe(properties) {
            @Override
            public QualityView read() {
                return view;
            }
        });
    }
}
