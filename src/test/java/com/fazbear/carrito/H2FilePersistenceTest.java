package com.fazbear.carrito;

import com.fazbear.carrito.model.Carrito;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba que el mecanismo JPA funciona con H2 en memoria para tests.
 *
 * NOTA TÉCNICA - Persistencia H2 file en AWS:
 * En producción/AWS la URL es jdbc:h2:file:/var/lib/fazbear/carrito/carritodb
 * con ddl-auto=update. Los datos persisten entre reinicios de la aplicación
 * mientras el volumen EBS esté montado en el mismo path.
 *
 * RIESGO: Si el contenedor ECS se reemplaza apuntando a un nuevo volumen EBS
 * (o sin montar el volumen correcto), los datos se pierden. El directorio
 * /var/lib/fazbear/carrito/ DEBE estar montado en el mismo EBS entre deploys.
 */
@DataJpaTest
@ActiveProfiles("test")
class H2FilePersistenceTest {

    @Autowired
    private EntityManager em;

    @Test
    void carritoPersisteEntreOperacionesJpa() {
        // Arrange: crear y persistir carrito
        Carrito carrito = new Carrito();
        carrito.setUsuarioId("test-usuario-" + System.currentTimeMillis());
        em.persist(carrito);
        em.flush();

        Long id = carrito.getId();
        assertThat(id).isNotNull();

        // Clear the first-level cache (simula nueva sesión JPA)
        em.clear();

        // Assert: releer desde "BD" (H2 mem en tests, H2 file en producción)
        Carrito reloaded = em.find(Carrito.class, id);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getCreadoEn()).isNotNull();
    }
}
