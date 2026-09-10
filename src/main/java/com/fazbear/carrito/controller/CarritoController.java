package com.fazbear.carrito.controller;

import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.model.ItemCarrito;
import com.fazbear.carrito.service.CarritoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST del Carrito de Compras — Freddy Fazbear's Pizza.
 * Expone endpoints bajo /api/carrito/{usuarioId}
 */
@RestController
@RequestMapping("/api/carrito")
@CrossOrigin(origins = "http://35.175.9.254")
public class CarritoController {

    private final CarritoService carritoService;

    public CarritoController(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    /**
     * GET /api/carrito/{usuarioId}
     * Obtiene el carrito de un usuario (o crea uno vacío).
     */
    @GetMapping("/{usuarioId}")
    public ResponseEntity<Carrito> getCarrito(@PathVariable String usuarioId) {
        return ResponseEntity.ok(carritoService.getOrCreate(usuarioId));
    }

    /**
     * POST /api/carrito/{usuarioId}/items
     * Agrega un ítem al carrito. Si el producto ya existe, suma la cantidad.
     *
     * Body de ejemplo:
     * {
     *   "productoId": 1,
     *   "nombreProducto": "Pizza Margherita de Freddy",
     *   "cantidad": 2,
     *   "precioUnitario": 12.99
     * }
     */
    @PostMapping("/{usuarioId}/items")
    public ResponseEntity<Carrito> addItem(@PathVariable String usuarioId,
                                           @RequestBody ItemCarrito item) {
        return ResponseEntity.ok(carritoService.addItem(usuarioId, item));
    }

    /**
     * DELETE /api/carrito/{usuarioId}/items/{itemId}
     * Elimina un ítem específico del carrito.
     */
    @DeleteMapping("/{usuarioId}/items/{itemId}")
    public ResponseEntity<Carrito> removeItem(@PathVariable String usuarioId,
                                               @PathVariable Long itemId) {
        return ResponseEntity.ok(carritoService.removeItem(usuarioId, itemId));
    }

    /**
     * DELETE /api/carrito/{usuarioId}
     * Vacía completamente el carrito de un usuario.
     */
    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Carrito> clearCarrito(@PathVariable String usuarioId) {
        return ResponseEntity.ok(carritoService.clear(usuarioId));
    }
}
