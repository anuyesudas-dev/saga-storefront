package com.ecommerce.notification.adapter.in.rest;

import com.ecommerce.notification.adapter.in.rest.dto.NotificationResponse;
import com.ecommerce.notification.domain.port.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification history (sending is event-driven)")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "List notification history, optionally for one order")
    public List<NotificationResponse> list(@RequestParam(required = false) String orderId) {
        List<com.ecommerce.notification.domain.model.Notification> result =
                orderId == null ? notifications.findAll() : notifications.findByOrderId(orderId);
        return result.stream().map(NotificationResponse::from).toList();
    }
}
