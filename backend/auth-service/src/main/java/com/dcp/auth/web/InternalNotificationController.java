package com.dcp.auth.web;

import com.dcp.auth.api.CreateNotificationRequest;
import com.dcp.auth.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final NotificationService notificationService;

    public InternalNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody CreateNotificationRequest request) {
        notificationService.create(request.userId(), request.title(), request.message());
        return ResponseEntity.accepted().build();
    }
}
