package com.fazbear.carrito.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

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

    /** oid verificado del usuario dentro del único tenant permitido; no sub libre. */
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

    /** Identifica las líneas y cantidades actuales sin cambiar el esquema persistente. */
    public String getRevisionCompra() {
        var lineas = items.stream()
                .map(item -> item.getId() + ":" + item.getProductoId() + ":" + item.getCantidad()).sorted()
                .collect(Collectors.joining(";"));
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(hash.digest((id + "|" + lineas).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
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
