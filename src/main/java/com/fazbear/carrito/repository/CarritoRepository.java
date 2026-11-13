package com.fazbear.carrito.repository;

import com.fazbear.carrito.model.Carrito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CarritoRepository extends JpaRepository<Carrito, Long> {
    Optional<Carrito> findByUsuarioId(String usuarioId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Carrito c where c.usuarioId = :usuarioId")
    Optional<Carrito> findForUpdate(@org.springframework.data.repository.query.Param("usuarioId") String usuarioId);
}
