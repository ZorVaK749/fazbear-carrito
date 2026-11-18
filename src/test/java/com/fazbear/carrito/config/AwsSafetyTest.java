package com.fazbear.carrito.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AwsSafetyTest {
    MockEnvironment safe() { var e=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://db.invalid/test")
        .withProperty("spring.jpa.hibernate.ddl-auto","validate").withProperty("ev3.kafka.enabled","true");
        e.setActiveProfiles("ev3-aws"); return e; }
    @Test void permitsAwsWithoutLabIdentity() { assertDoesNotThrow(() -> new AwsSafety(safe()).afterPropertiesSet()); }
    @Test void permitsPersistentH2FileWithUpdate() {
        var e = new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:h2:file:/var/lib/fazbear/carrito/carritodb")
            .withProperty("spring.jpa.hibernate.ddl-auto", "update")
            .withProperty("ev3.kafka.enabled", "true");
        e.setActiveProfiles("ev3-aws");
        assertDoesNotThrow(() -> new AwsSafety(e).afterPropertiesSet());
    }
    @ParameterizedTest @ValueSource(strings={"local", "ev3-local", "test"})
    void rejectsMixedProfiles(String p) { var e=safe(); e.setActiveProfiles("ev3-aws",p);
        assertThrows(IllegalStateException.class, () -> new AwsSafety(e).afterPropertiesSet()); }
    @ParameterizedTest @ValueSource(strings={"LOCAL_JWT_JWK_SET_URI", "spring.security.oauth2.resourceserver.jwt.jwk-set-uri"})
    void rejectsJwksOverride(String key) { var e=safe().withProperty(key,"http://lab-auth/jwks");
        assertThrows(IllegalStateException.class, () -> new AwsSafety(e).afterPropertiesSet()); }
    @ParameterizedTest @ValueSource(strings={"create", "create-drop"})
    void rejectsImplicitSchemaMutation(String ddl) { var e=safe().withProperty("spring.jpa.hibernate.ddl-auto",ddl);
        assertThrows(IllegalStateException.class, () -> new AwsSafety(e).afterPropertiesSet()); }
    @Test void rejectsH2Mem() { var e=safe().withProperty("spring.datasource.url","jdbc:h2:mem:unsafe");
        assertThrows(IllegalStateException.class, () -> new AwsSafety(e).afterPropertiesSet()); }
}
