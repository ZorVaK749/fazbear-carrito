package com.fazbear.carrito.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fazbear.carrito.model.ItemCarrito;
import java.math.BigDecimal;

/** No enlazar IDs persistentes ni relaciones JPA suministrados por el cliente. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgregarItemRequest(Long productoId, String nombreProducto,
                                 Integer cantidad, BigDecimal precioUnitario) {
    public ItemCarrito toItem() {
        ItemCarrito item = new ItemCarrito();
        item.setProductoId(productoId);
        item.setNombreProducto(nombreProducto);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        return item;
    }
}
