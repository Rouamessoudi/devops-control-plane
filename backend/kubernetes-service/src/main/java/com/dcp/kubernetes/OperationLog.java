package com.dcp.kubernetes;

import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OperationLog {

    private final K8sOperationRepository operations;

    public OperationLog(K8sOperationRepository operations) {
        this.operations = operations;
    }

    @Transactional
    public void record(UUID actorId, String operation, String resourceName, String status, String message) {
        K8sOperation row = new K8sOperation();
        row.setId(UUID.randomUUID());
        row.setActorId(actorId);
        row.setOperation(operation);
        row.setResourceName(resourceName);
        row.setStatus(status);
        row.setMessage(clip(message, 1000));
        row.setCreatedAt(Instant.now());
        operations.save(row);
    }

    @Transactional(readOnly = true)
    public java.util.List<KubernetesApi.OperationAudit> audit() {
        JwtPrincipal user = CurrentUser.require();
        java.util.List<K8sOperation> rows = user.hasRole("ADMIN")
                ? operations.findAllByOrderByCreatedAtDesc()
                : operations.findByActorIdOrderByCreatedAtDesc(user.userId());
        return rows.stream().map(OperationLog::toAudit).toList();
    }

    private static KubernetesApi.OperationAudit toAudit(K8sOperation row) {
        return new KubernetesApi.OperationAudit(
                row.getId(),
                row.getActorId(),
                row.getOperation(),
                row.getResourceName(),
                row.getStatus(),
                row.getMessage(),
                row.getCreatedAt());
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
