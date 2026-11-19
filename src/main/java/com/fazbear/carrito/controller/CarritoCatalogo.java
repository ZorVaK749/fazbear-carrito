package com.fazbear.carrito.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.model.ItemCarrito;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
class CarritoCatalogo {
    private final RestClient catalogo;

    CarritoCatalogo(@Value("${carrito.catalogo-url:}") String base) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(4));
        this.catalogo = base.isBlank() ? null
            : RestClient.builder().baseUrl(base).requestFactory(timeouts).build();
    }

    Map<Long, Producto> precios(Jwt jwt) {
        if (catalogo == null) return Map.of();
        try {
            var productos = catalogo.get().uri("/api/productos")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Producto[].class);
            if (productos == null) throw new RestClientException("Missing catalog");
            Map<Long, Producto> precios = new HashMap<>();
            for (Producto p : productos) {
                if (p.id() != null && p.nombre() != null && p.precio() != null
                        && p.precio().signum() > 0 && p.precio().stripTrailingZeros().scale() <= 0) {
                    precios.put(p.id(), p);
                }
            }
            return precios;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo consultar el catálogo");
        }
    }

    ItemCarrito item(AgregarItemRequest request, Map<Long, Producto> precios) {
        var item = request.toItem();
        if (catalogo == null) return item;
        var producto = precios.get(request.productoId());
        if (producto == null && request.cantidad() != null && request.cantidad() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Producto sin precio vigente en CLP");
        }
        if (producto != null) {
            item.setNombreProducto(producto.nombre());
            item.setPrecioUnitario(producto.precio());
        }
        return item;
    }

    Vista ver(Carrito carrito, Map<Long, Producto> precios) {
        var items = carrito.getItems().stream().map(item -> {
            var producto = precios.get(item.getProductoId());
            boolean vigente = catalogo == null || producto != null;
            var precio = catalogo == null ? item.getPrecioUnitario()
                : producto == null ? BigDecimal.ZERO : producto.precio();
            return new Linea(item.getId(), item.getProductoId(),
                producto == null ? item.getNombreProducto() : producto.nombre(),
                item.getCantidad(), precio, vigente);
        }).toList();
        var total = items.stream().map(i -> i.precioUnitario().multiply(BigDecimal.valueOf(i.cantidad())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Vista(carrito.getId(), carrito.getUsuarioId(), carrito.getCreadoEn(), items, total);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Producto(Long id, String nombre, BigDecimal precio) {}
    record Linea(Long id, Long productoId, String nombreProducto, int cantidad,
                 BigDecimal precioUnitario, boolean precioVigente) {}
    record Vista(Long id, String usuarioId, LocalDateTime creadoEn, List<Linea> items, BigDecimal total) {}
}
