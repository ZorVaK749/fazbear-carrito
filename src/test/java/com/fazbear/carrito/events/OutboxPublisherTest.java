package com.fazbear.carrito.events;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class OutboxPublisherTest {
    @Test void failureRetainsPendingRowAndStopsBatchWithoutLoggingPayload(
            org.springframework.boot.test.system.CapturedOutput output) {
        OutboxRepository repository = mock(OutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        OutboxEvent first = new OutboxEvent("carrito.actualizado.v1", "owner");
        first.payload = "sensitive-event-never-log";
        OutboxEvent second = new OutboxEvent("carrito.actualizado.v1", "another-owner");
        second.payload = "second";
        when(repository.findTop50ByDeliveredFalseOrderByIdAsc()).thenReturn(List.of(first, second));
        when(kafka.send(first.topic, first.eventKey, first.payload))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sensitive-exception-never-log")));
        new OutboxPublisher(repository, kafka).publish();
        assertFalse(first.delivered);
        verify(repository, never()).save(any());
        verify(kafka, never()).send(second.topic, second.eventKey, second.payload);
        assertFalse(output.getAll().contains("sensitive-event-never-log"));
        assertFalse(output.getAll().contains("sensitive-exception-never-log"));
    }
}
