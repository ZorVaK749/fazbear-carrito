package com.fazbear.carrito;

import com.fazbear.carrito.model.Carrito;
import com.fazbear.carrito.repository.CarritoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de arranque real para ms-carrito utilizando el perfil efectivo del contenedor Docker (ev3-aws)
 * con persistencia H2 en archivo y puerto 8083.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("ev3-aws")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:file:./target/test-data-effective/carritodb_effective;AUTO_SERVER=FALSE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=update",
    "ev3.kafka.enabled=true",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.main.allow-bean-definition-overriding=true"
})
@Transactional
class CarritoEffectiveProfileStartupTest {

    @TestConfiguration
    static class StubJwtDecoderConfig {
        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            return token -> null;
        }
    }

    @Autowired
    private CarritoRepository carritoRepository;

    @Test
    @DisplayName("El perfil efectivo ev3-aws debe inicializar el contexto y permitir operaciones de carrito")
    void testCarritoStartupUnderEffectiveProfile() {
        Carrito carrito = new Carrito();
        carrito.setUsuarioId("user-startup-test");
        Carrito saved = carritoRepository.save(carrito);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsuarioId()).isEqualTo("user-startup-test");

        var found = carritoRepository.findByUsuarioId("user-startup-test");
        assertThat(found).isPresent();
    }
}
