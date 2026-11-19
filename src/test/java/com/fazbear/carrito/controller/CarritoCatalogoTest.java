package com.fazbear.carrito.controller;

import com.fazbear.carrito.model.Carrito;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;

class CarritoCatalogoTest {
    @Test void savedCartsUseCurrentCatalogWithoutReplacingItemsOrQuantities() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/productos", exchange -> {
            int status = "Bearer delegated".equals(exchange.getRequestHeaders().getFirst("Authorization")) ? 200 : 401;
            byte[] json = """
                [{"id":7,"nombre":"Pizza Freddy","precio":12990.00,"stock":20}]
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, json.length);
            try (var output = exchange.getResponseBody()) { output.write(json); }
        });
        server.start();
        try {
            var catalog = new CarritoCatalogo("http://127.0.0.1:" + server.getAddress().getPort());
            var jwt = Jwt.withTokenValue("delegated").header("alg", "RS256").claim("sub", "test").build();
            var request = new AgregarItemRequest(7L, "Antes", 2, new BigDecimal("12.99"));
            var saved = request.toItem();
            saved.setId(51L);
            var cart = new Carrito();
            cart.getItems().add(saved);
            var prices = catalog.precios(jwt);
            assertThat(catalog.item(request, prices).getPrecioUnitario()).isEqualByComparingTo("12990");
            var view = catalog.ver(cart, prices);
            assertThat(view.total()).isEqualByComparingTo("25980");
            assertThat(view.items().get(0).id()).isEqualTo(51L);
            assertThat(view.items().get(0).cantidad()).isEqualTo(2);
            assertThat(saved.getPrecioUnitario()).isEqualByComparingTo("12.99");
            assertThat(catalog.ver(cart, Map.of()).items().get(0).precioVigente()).isFalse();
        } finally {
            server.stop(0);
        }
    }
}
