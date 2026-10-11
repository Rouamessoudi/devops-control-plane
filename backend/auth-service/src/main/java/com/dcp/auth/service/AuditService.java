package com.dcp.auth.service;

import com.dcp.auth.api.AuditEventResponse;
import com.dcp.auth.domain.AuthAudit;
import com.dcp.auth.domain.Role;
import com.dcp.auth.repo.AuthAuditRepository;
import com.dcp.common.error.ApiException;
import com.dcp.common.security.JwtPrincipal;
import com.dcp.common.web.CurrentUser;
import com.dcp.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String USER_DELETED = "USER_DELETED";

    private static final int MAX_DETAILS = 500;
    private static final int MAX_IP = 64;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuthAuditRepository audits;

    public AuditService(AuthAuditRepository audits) {
        this.audits = audits;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String ipAddress, String details) {
        AuthAudit event = new AuthAudit();
        event.setActorId(actorId);
        event.setAction(action);
        event.setIpAddress(limit(ipAddress, MAX_IP));
        event.setDetails(limit(details, MAX_DETAILS));
        audits.save(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("page must be >= 0 and size must be between 1 and 100");
        }
        JwtPrincipal principal = CurrentUser.require();
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuthAudit> result = principal.hasRole(Role.ADMIN.name())
                ? audits.findAll(request)
                : audits.findByActorId(principal.userId(), request);
        List<AuditEventResponse> items = result.getContent().stream().map(AuditEventResponse::from).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
