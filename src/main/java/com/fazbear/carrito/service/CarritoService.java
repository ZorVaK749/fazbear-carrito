package com.fazbear.carrito.service;

import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.model.ItemCarrito;
import com.fazbear.carrito.repository.CarritoRepository;
import org.springframework.stereotype.Service;
import com.fazbear.carrito.events.EventOutbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.util.Optional;

/**
 * Servicio del Carrito de compras.
 * Gestiona el carrito temporal por usuario.
 */
@Service
@Transactional
public class CarritoService {

    private final CarritoRepository carritoRepository;

    private final EventOutbox outbox;
    private final String tenantId;

    public CarritoService(CarritoRepository carritoRepository, EventOutbox outbox,
            @Value("${spring.security.oauth2.resourceserver.jwt.tenant-id}") String tenantId) {
        this.carritoRepository = carritoRepository;
        this.outbox = outbox;
        this.tenantId = tenantId;
    }

    /** Obtiene el carrito de un usuario, o crea uno vacío si no existe */
    public Carrito getOrCreate(String usuarioId) {
        return carritoRepository.findForUpdate(usuarioId)
                .orElseGet(() -> {
                    Carrito nuevo = new Carrito();
                    nuevo.setUsuarioId(usuarioId);
                    return carritoRepository.save(nuevo);
                });
    }

    /** Retorna el carrito de un usuario si existe */
    public Optional<Carrito> findByUsuarioId(String usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId);
    }

    /** Agrega un ítem al carrito. Si el producto ya existe, suma la cantidad. */
    public Carrito addItem(String usuarioId, ItemCarrito nuevoItem) {
        if (nuevoItem.getProductoId() == null || nuevoItem.getProductoId() <= 0
                || nuevoItem.getCantidad() == null || nuevoItem.getCantidad() == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Producto y cantidad positivos requeridos");
        }
        Carrito carrito = getOrCreate(usuarioId);

        // Buscar si el producto ya está en el carrito
        Optional<ItemCarrito> existente = carrito.getItems().stream()
                .filter(i -> i.getProductoId().equals(nuevoItem.getProductoId()))
                .findFirst();

        if (existente.isPresent()) {
            int result;
            try { result = Math.addExact(existente.get().getCantidad(), nuevoItem.getCantidad()); }
            catch (ArithmeticException overflow) { throw invalidQuantity(); }
            if (result <= 0) throw invalidQuantity();
            existente.get().setCantidad(result);
        } else {
            if (nuevoItem.getCantidad() < 0) throw invalidQuantity();
            nuevoItem.setCarrito(carrito);
            carrito.getItems().add(nuevoItem);
        }
        carritoRepository.saveAndFlush(carrito);
        emit(usuarioId, nuevoItem.getProductoId(), existente.isPresent() ? "UPDATE" : "ADD",
            existente.map(ItemCarrito::getCantidad).orElse(nuevoItem.getCantidad()));
        return carrito;
    }

    /** Elimina un ítem del carrito por ID del ítem */
    public Carrito removeItem(String usuarioId, Long itemId) {
        Carrito carrito = getOrCreate(usuarioId);
        carrito.getItems().stream().filter(i -> i.getId().equals(itemId)).findFirst()
            .ifPresent(i -> emit(usuarioId, i.getProductoId(), "REMOVE", 0));
        carrito.getItems().removeIf(i -> i.getId().equals(itemId));
        return carritoRepository.save(carrito);
    }

    /** Vacía completamente el carrito de un usuario */
    public Carrito clear(String usuarioId) {
        Carrito carrito = getOrCreate(usuarioId);
        carrito.getItems().forEach(i -> emit(usuarioId, i.getProductoId(), "REMOVE", 0));
        carrito.getItems().clear();
        return carritoRepository.save(carrito);
    }

    /** Elimina el carrito completo de un usuario */
    public void deleteByUsuarioId(String usuarioId) {
        carritoRepository.findForUpdate(usuarioId)
                .ifPresent(carrito -> {
                    carrito.getItems().forEach(i -> emit(usuarioId, i.getProductoId(), "REMOVE", 0));
                    carritoRepository.delete(carrito);
                });
    }

    private void emit(String owner, Long productoId, String tipo, int cantidad) {
        outbox.append("carrito.actualizado.v1", tenantId + ":" + owner, Map.of(
            "tenantId", tenantId, "ownerId", owner, "productoId", productoId,
            "tipo", tipo, "cantidad", cantidad));
    }

    private org.springframework.web.server.ResponseStatusException invalidQuantity() {
        return new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Cantidad resultante fuera de rango");
    }
}
