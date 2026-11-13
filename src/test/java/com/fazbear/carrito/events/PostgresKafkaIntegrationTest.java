package com.fazbear.carrito.events;

import com.fazbear.carrito.model.ItemCarrito;
import com.fazbear.carrito.service.CarritoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Opt-in disposable PostgreSQL/Kafka only; never targets the default database. */
@EnabledIfEnvironmentVariable(named = "EV3_DOCKER_TEST", matches = "true")
@ActiveProfiles("ev3-local")
@SpringBootTest(properties = {"ev3.kafka.enabled=true", "ev3.kafka.initial-delay-ms=3600000"})
class PostgresKafkaIntegrationTest {
    @Autowired CarritoService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxPublisher publisher;
    @MockitoBean JwtDecoder decoder;
    // Boot exposes a wildcard-generic template; match its erased type for wrapping.
    @SuppressWarnings("rawtypes")
    @MockitoSpyBean KafkaTemplate kafka;
    @Test void postgresMutationSurvivesDeliveryFailureThenReachesRealKafka() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().contains("/ev3_carrito_test"), "Disposable test DB required");
        }
        String owner = UUID.randomUUID().toString();
        ItemCarrito item = new ItemCarrito();
        item.setProductoId(987L); item.setCantidad(2); item.setNombreProducto("Synthetic test");
        item.setPrecioUnitario(BigDecimal.ONE);
        service.addItem(owner, item);
        String key = "ee0d2c5b-e60b-4c57-9b30-8a38a520c430:" + owner;
        String payload = jdbc.queryForObject("select payload from ev3_outbox where event_key = ?", String.class, key);
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("simulated unavailable broker")))
            .when(kafka).send(anyString(), anyString(), anyString());
        publisher.publish();
        assertFalse(jdbc.queryForObject("select delivered from ev3_outbox where event_key = ?", Boolean.class, key));
        reset(kafka);
        publisher.publish();
        assertTrue(jdbc.queryForObject("select delivered from ev3_outbox where event_key = ?", Boolean.class, key));
        var config = new HashMap<String, Object>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "ev3-test-" + UUID.randomUUID());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<String, String>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("carrito.actualizado.v1"));
            long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.key().equals(key)) {
                        assertEquals(payload, record.value());
                        assertEquals("ADD", new ObjectMapper().readTree(record.value()).at("/data/tipo").asText());
                        return;
                    }
                }
            }
            fail("Committed outbox event not observed in real Kafka");
        }
    }
}
