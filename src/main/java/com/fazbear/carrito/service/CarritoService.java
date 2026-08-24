package com.fazbear.carrito.service;

import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.model.ItemCarrito;
import com.fazbear.carrito.repository.CarritoRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Servicio del Carrito de compras.
 * Gestiona el carrito temporal por usuario.
 */
@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;

    public CarritoService(CarritoRepository carritoRepository) {
        this.carritoRepository = carritoRepository;
    }

    /** Obtiene el carrito de un usuario, o crea uno vacío si no existe */
    public Carrito getOrCreate(String usuarioId) {
        return carritoRepository.findByUsuarioId(usuarioId)
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
        Carrito carrito = getOrCreate(usuarioId);

        // Buscar si el producto ya está en el carrito
        Optional<ItemCarrito> existente = carrito.getItems().stream()
                .filter(i -> i.getProductoId().equals(nuevoItem.getProductoId()))
                .findFirst();

        if (existente.isPresent()) {
            existente.get().setCantidad(existente.get().getCantidad() + nuevoItem.getCantidad());
        } else {
            nuevoItem.setCarrito(carrito);
            carrito.getItems().add(nuevoItem);
        }
        return carritoRepository.save(carrito);
    }

    /** Elimina un ítem del carrito por ID del ítem */
    public Carrito removeItem(String usuarioId, Long itemId) {
        Carrito carrito = getOrCreate(usuarioId);
        carrito.getItems().removeIf(i -> i.getId().equals(itemId));
        return carritoRepository.save(carrito);
    }

    /** Vacía completamente el carrito de un usuario */
    public Carrito clear(String usuarioId) {
        Carrito carrito = getOrCreate(usuarioId);
        carrito.getItems().clear();
        return carritoRepository.save(carrito);
    }

    /** Elimina el carrito completo de un usuario */
    public void deleteByUsuarioId(String usuarioId) {
        carritoRepository.findByUsuarioId(usuarioId)
                .ifPresent(carritoRepository::delete);
    }
}
