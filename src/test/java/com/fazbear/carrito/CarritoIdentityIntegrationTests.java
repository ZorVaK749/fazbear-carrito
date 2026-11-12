package com.fazbear.carrito;

import com.fazbear.carrito.repository.CarritoRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CarritoIdentityIntegrationTests extends LocalJwtTestSupport {
    @Autowired MockMvc mvc;
    @Autowired CarritoRepository repository;

    @BeforeEach
    void clear() { repository.deleteAll(); }

    @ParameterizedTest
    @ValueSource(strings = {A, B})
    void meReturnsOnlyValidatedIdentityAndDoesNotCreateCart(String owner) throws Exception {
        mvc.perform(get("/api/carrito/me").header("Authorization", "Bearer " + token(owner)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.usuarioId").value(owner))
            .andExpect(jsonPath("$.tenantId").value(TENANT))
            .andExpect(jsonPath("$.length()").value(2));
        assertEquals(0, repository.count());
        // La ruta variable sigue existiendo y aplica el mismo principal.
        mvc.perform(get("/api/carrito/" + owner).header("Authorization", "Bearer " + token(owner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(owner));
        assertEquals(1, repository.count());
    }

    @Test
    void queryParametersCannotSelectAnotherIdentityOrCreateMeCart() throws Exception {
        mvc.perform(get("/api/carrito/me").param("usuarioId", B).param("tenantId", "other")
            .header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.usuarioId").value(A))
            .andExpect(jsonPath("$.tenantId").value(TENANT));
        assertEquals(0, repository.count());
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/carrito/me")).andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"oid-missing", "oid-invalid", "scope-missing", "scope-invalid", "app-only"})
    void meUsesExistingIdentityAndScopeRules(String variant) throws Exception {
        Map<String, Object> overrides = new HashMap<>();
        switch (variant) {
            case "oid-missing" -> { overrides.put("oid", null); overrides.put("sub", A); }
            case "oid-invalid" -> overrides.put("oid", "invalid");
            case "scope-missing" -> overrides.put("scp", null);
            case "scope-invalid" -> overrides.put("scp", "openid");
            case "app-only" -> { overrides.put("scp", null); overrides.put("roles", new String[]{"Admin"}); }
            default -> throw new IllegalArgumentException(variant);
        }
        mvc.perform(get("/api/carrito/me").header("Authorization", "Bearer " + token(A, overrides)))
            .andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }

    @Test
    void meRejectsAnotherTenant() throws Exception {
        mvc.perform(get("/api/carrito/me").header("Authorization", "Bearer " + token(A,
            Map.of("tid", "33333333-3333-4333-8333-333333333333"))))
            .andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @Test
    void meHasSameCorsPolicy() throws Exception {
        String frontend = "https://p5gsy16utd.execute-api.us-east-1.amazonaws.com";
        mvc.perform(options("/api/carrito/me").header("Origin", frontend)
            .header("Access-Control-Request-Method", "GET")
            .header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", frontend));
        mvc.perform(get("/api/carrito/me").header("Origin", frontend)
            .header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", frontend));
    }

    @Test
    void meDoesNotBecomeAnAliasForMutations() throws Exception {
        mvc.perform(delete("/api/carrito/me").header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/carrito/me/items").header("Authorization", "Bearer " + token(A))
            .contentType("application/json")
            .content("{\"productoId\":7,\"cantidad\":1,\"precioUnitario\":12.99}"))
            .andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }
}
