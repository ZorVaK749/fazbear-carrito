package com.fazbear.carrito.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa el carrito de compras temporal de un usuario.
 * Al confirmar la compra, se convierte en un Pedido en MS Pedidos.
 */
@Entity
@Table(name = "carritos")
public class Carrito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del usuario — claim 'sub' del JWT (o string libre en dev) */
    @Column(name = "usuario_id", nullable = false, length = 100, unique = true)
    private String usuarioId;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @OneToMany(mappedBy = "carrito", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCarrito> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /** Calcula el total del carrito sumando precio × cantidad de cada ítem */
    public BigDecimal getTotal() {
        return items.stream()
                .map(i -> i.getPrecioUnitario().multiply(BigDecimal.valueOf(i.getCantidad())))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b));
    }

    // ─── Getters y Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public List<ItemCarrito> getItems() {
        return items;
    }

    public void setItems(List<ItemCarrito> items) {
        this.items = items;
    }
}
