package com.fazbear.carrito.events;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ev3.kafka.enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
        // The framework's default error listener logs the failed record value. Never log event data.
        kafka.setProducerListener(new org.springframework.kafka.support.ProducerListener<>() { });
    }
    @Scheduled(fixedDelayString = "${ev3.kafka.poll-ms:2000}", initialDelayString = "${ev3.kafka.initial-delay-ms:2000}")
    public void publish() {
        try {
            for (OutboxEvent row : repository.findTop50ByDeliveredFalseOrderByIdAsc()) {
                kafka.send(row.topic, row.eventKey, row.payload).get(5, TimeUnit.SECONDS);
                row.delivered = true;
                repository.save(row);
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Kafka outbox delivery deferred; pending events retained");
        }
    }
}
