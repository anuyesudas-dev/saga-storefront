package com.ecommerce.notification.domain.service;

import com.ecommerce.notification.domain.model.NotificationType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationComposerTest {

    @Test
    void everyTypeProducesAMessageContainingTheOrderId() {
        for (NotificationType type : NotificationType.values()) {
            String content = NotificationComposer.compose(type, "order-1", "detail");
            assertThat(content).contains("order-1");
        }
    }

    @Test
    void failureMessagesIncludeTheReason() {
        assertThat(NotificationComposer.compose(NotificationType.PAYMENT_FAILED, "o1",
                "INSUFFICIENT_FUNDS")).contains("INSUFFICIENT_FUNDS");
        assertThat(NotificationComposer.compose(NotificationType.ORDER_FAILED, "o1",
                "out of stock")).contains("out of stock");
    }
}
