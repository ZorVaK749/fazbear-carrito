package com.fazbear.carrito.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventOutbox {
    private final OutboxRepository repository;
    private final boolean enabled;
    private final ObjectMapper mapper = new ObjectMapper();
    public EventOutbox(OutboxRepository repository, @Value("${ev3.kafka.enabled:false}") boolean enabled) {
        this.repository = repository;
        this.enabled = enabled;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, Map<String, Object> data) {
        if (!enabled) return;
        OutboxEvent row = repository.saveAndFlush(new OutboxEvent(topic, key));
        try {
            row.payload = mapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(), "eventType", topic,
                "schemaVersion", 1, "occurredAt", Instant.now().toString(),
                "producer", "ms-carrito", "aggregateId", key, "aggregateVersion", row.id,
                "correlationId", UUID.randomUUID().toString(), "data", data));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot serialize event");
        }
    }
}
