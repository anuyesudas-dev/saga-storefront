package com.ecommerce.notification.adapter.out.channel;

import com.ecommerce.notification.domain.model.Channel;
import com.ecommerce.notification.domain.port.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Simulated email delivery — a real adapter would call an SMTP relay or SES. */
@Component
public class SimulatedEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailChannel.class);

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public String recipientFor(String customerId) {
        return customerId + "@example.com";
    }

    @Override
    public void send(String recipient, String content) {
        log.info("[EMAIL] to {}: {}", recipient, content);
    }
}
