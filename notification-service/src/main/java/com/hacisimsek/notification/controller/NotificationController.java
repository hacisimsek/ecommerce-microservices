package com.hacisimsek.notification.controller;

import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Notification>> getNotificationsByCustomer(@PathVariable UUID customerId) {
        return ResponseEntity.ok(notificationService.getNotificationsByRecipient(customerId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Notification>> getNotificationsByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(notificationService.getNotificationsByOrder(orderId));
    }
}