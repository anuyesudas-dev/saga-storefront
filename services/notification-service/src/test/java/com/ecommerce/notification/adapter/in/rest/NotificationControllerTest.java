package com.ecommerce.notification.adapter.in.rest;

import com.ecommerce.notification.domain.model.Channel;
import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.model.NotificationType;
import com.ecommerce.notification.domain.port.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationRepository notifications;

    @Test
    void listsHistoryForAnOrder() throws Exception {
        when(notifications.findByOrderId("order-1")).thenReturn(List.of(
                Notification.sent("n1", "order-1", "cust-1", NotificationType.ORDER_RECEIVED,
                        Channel.EMAIL, "cust-1@example.com", "Your order order-1 ...", NOW),
                Notification.sent("n2", "order-1", "cust-1", NotificationType.ORDER_RECEIVED,
                        Channel.SMS, "+1-555-0001", "Your order order-1 ...", NOW)));

        mvc.perform(get("/api/v1/notifications").param("orderId", "order-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", notNullValue()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[1].channel").value("SMS"));
    }

    @Test
    void emptyHistoryIsAnEmptyListNotAnError() throws Exception {
        when(notifications.findByOrderId("ghost")).thenReturn(List.of());

        mvc.perform(get("/api/v1/notifications").param("orderId", "ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
