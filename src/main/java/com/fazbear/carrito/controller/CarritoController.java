package com.fazbear.carrito.controller;

import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.security.CarritoOwnerAuthorization;
import com.fazbear.carrito.service.CarritoService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST del Carrito de Compras — Freddy Fazbear's Pizza.
 * Expone endpoints bajo /api/carrito/{usuarioId}
 */
@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService carritoService;
    private final CarritoOwnerAuthorization ownerAuthorization;
    private final CarritoCatalogo catalogo;

    public CarritoController(CarritoService carritoService, CarritoOwnerAuthorization ownerAuthorization,
                            CarritoCatalogo catalogo) {
        this.carritoService = carritoService;
        this.ownerAuthorization = ownerAuthorization;
        this.catalogo = catalogo;
    }

    /** Ruta literal: no crea un carrito con ID "me" ni acepta identidad de cliente. */
    @GetMapping("/me")
    public ResponseEntity<CarritoOwnerAuthorization.AuthenticatedOwner> me(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerAuthorization.requireIdentity(jwt));
    }

    /**
     * GET /api/carrito/{usuarioId}
     * Obtiene el carrito de un usuario (o crea uno vacío).
     */
    @GetMapping("/{usuarioId}")
    public ResponseEntity<CarritoCatalogo.Vista> getCarrito(@PathVariable String usuarioId,
                                             @AuthenticationPrincipal Jwt jwt) {
        var carrito = carritoService.getOrCreate(ownerAuthorization.requireOwner(jwt, usuarioId));
        var precios = carrito.getItems().isEmpty() ? java.util.Map.<Long, CarritoCatalogo.Producto>of()
            : catalogo.precios(jwt);
        return ResponseEntity.ok(catalogo.ver(carrito, precios));
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
     *   "precioUnitario": 12990
     * }
     */
    @PostMapping("/{usuarioId}/items")
    public ResponseEntity<CarritoCatalogo.Vista> addItem(@PathVariable String usuarioId,
                                           @RequestBody AgregarItemRequest item,
                                           @AuthenticationPrincipal Jwt jwt) {
        String owner = ownerAuthorization.requireOwner(jwt, usuarioId);
        var precios = catalogo.precios(jwt);
        return ResponseEntity.ok(catalogo.ver(carritoService.addItem(owner, catalogo.item(item, precios)), precios));
    }

    /**
     * DELETE /api/carrito/{usuarioId}/items/{itemId}
     * Elimina un ítem específico del carrito.
     */
    @DeleteMapping("/{usuarioId}/items/{itemId}")
    public ResponseEntity<CarritoCatalogo.Vista> removeItem(@PathVariable String usuarioId,
                                               @PathVariable Long itemId,
                                               @AuthenticationPrincipal Jwt jwt) {
        String owner = ownerAuthorization.requireOwner(jwt, usuarioId);
        var precios = catalogo.precios(jwt);
        return ResponseEntity.ok(catalogo.ver(carritoService.removeItem(owner, itemId), precios));
    }

    /**
     * DELETE /api/carrito/{usuarioId}
     * Vacía completamente el carrito de un usuario.
     */
    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<Carrito> clearCarrito(@PathVariable String usuarioId,
                                               @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(carritoService.clear(ownerAuthorization.requireOwner(jwt, usuarioId)));
    }
}
