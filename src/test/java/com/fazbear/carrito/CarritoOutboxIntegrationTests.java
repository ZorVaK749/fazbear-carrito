package com.fazbear.carrito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazbear.carrito.events.OutboxPublisher;
import com.fazbear.carrito.events.OutboxRepository;
import com.fazbear.carrito.repository.CarritoRepository;
import com.fazbear.carrito.service.CarritoService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "ev3.kafka.enabled=true")
@AutoConfigureMockMvc
class CarritoOutboxIntegrationTests extends LocalJwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRepository outbox;
    @Autowired CarritoRepository carts;
    @Autowired CarritoService service;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean OutboxPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String BODY = "{\"productoId\":7,\"nombreProducto\":\"Pizza\",\"cantidad\":2,\"precioUnitario\":12.99}";

    @BeforeEach void clean() { carts.deleteAll(); outbox.deleteAll(); }
    private void add() throws Exception {
        mvc.perform(post("/api/carrito/" + A + "/items").header("Authorization", "Bearer " + token(A))
            .contentType("application/json").content(BODY)).andExpect(status().isOk());
    }
    @Test void actualMutationsEmitVersionedPseudonymousEvents() throws Exception {
        add(); add();
        mvc.perform(delete("/api/carrito/" + A).header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isOk());
        var rows = jdbc.queryForList("select payload, event_key from ev3_outbox order by id");
        assertEquals(3, rows.size());
        long previous = 0;
        String[] types = {"ADD", "UPDATE", "REMOVE"};
        int[] quantities = {2, 4, 0};
        for (int i = 0; i < rows.size(); i++) {
            var event = mapper.readTree((String) rows.get(i).get("payload"));
            assertEquals(9, event.size());
            UUID.fromString(event.get("eventId").asText());
            UUID.fromString(event.get("correlationId").asText());
            assertEquals("carrito.actualizado.v1", event.get("eventType").asText());
            assertEquals("ms-carrito", event.get("producer").asText());
            assertEquals(1, event.get("schemaVersion").asInt());
            assertTrue(event.get("aggregateVersion").asLong() > previous);
            previous = event.get("aggregateVersion").asLong();
            assertEquals(TENANT + ":" + A, rows.get(i).get("event_key"));
            assertEquals(A, event.at("/data/ownerId").asText());
            assertEquals(TENANT, event.at("/data/tenantId").asText());
            assertEquals(types[i], event.at("/data/tipo").asText());
            assertEquals(quantities[i], event.at("/data/cantidad").asInt());
            assertFalse(event.toString().contains("precio"));
        }
    }
    @Test void rollbackDiscardsCartAndOutboxTogether() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var item = new com.fazbear.carrito.model.ItemCarrito();
            item.setProductoId(7L); item.setCantidad(2); item.setNombreProducto("Pizza");
            item.setPrecioUnitario(new java.math.BigDecimal("12.99"));
            service.addItem(A, item);
            assertEquals(1, outbox.count());
            status.setRollbackOnly();
        });
        assertEquals(0, carts.count()); assertEquals(0, outbox.count());
    }
    @Test void unauthorizedMutationCannotEmitOrCreateCart() throws Exception {
        mvc.perform(post("/api/carrito/" + B + "/items").header("Authorization", "Bearer " + token(A))
            .contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        assertEquals(0, outbox.count()); assertEquals(0, carts.count());
    }
    @Test void nonexistentRemovalDoesNotEmit() throws Exception {
        add();
        mvc.perform(delete("/api/carrito/" + A + "/items/999999").header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isOk());
        assertEquals(1, outbox.count());
    }
}
