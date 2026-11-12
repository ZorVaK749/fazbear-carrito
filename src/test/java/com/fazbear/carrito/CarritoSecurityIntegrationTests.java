package com.fazbear.carrito;

import com.fazbear.carrito.repository.CarritoRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Filtro real + decoder Nimbus/JWKS local + controller/service/JPA/H2 reales. */
@SpringBootTest
@AutoConfigureMockMvc
class CarritoSecurityIntegrationTests extends LocalJwtTestSupport {
    private static final String BASE = "/api/carrito/";
    private static final String FRONTEND = "https://p5gsy16utd.execute-api.us-east-1.amazonaws.com";
    private static final String ITEM = """
        {"productoId":7,"nombreProducto":"Pizza Freddy","cantidad":2,"precioUnitario":12.99}
        """;
    @Autowired MockMvc mvc;
    @Autowired CarritoRepository repository;

    @BeforeEach
    void cleanDatabase() { repository.deleteAll(); }

    static Stream<Arguments> endpoints() {
        return Stream.of(Arguments.of("GET", ""), Arguments.of("POST", "/items"),
            Arguments.of("DELETE", "/items/1"), Arguments.of("DELETE", ""));
    }

    private ResultActions call(String method, String oid, String suffix, String bearer) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), BASE + oid + suffix);
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (method.equals("POST")) request.contentType("application/json").content(ITEM);
        return mvc.perform(request);
    }

    private long add(String oid) throws Exception {
        String json = call("POST", oid, "/items", token(oid)).andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarioId").value(oid))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.items[0].id")).longValue();
    }

    @Test
    void ownerCanGetOwnCartEvenWhenSubDiffersFromOid() throws Exception {
        call("GET", A, "", token(A)).andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarioId").value(A)).andExpect(jsonPath("$.items").isEmpty());
        assertEquals(1, repository.count());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void ownerCannotUseAnyOtherUsersEndpoint(String method, String suffix) throws Exception {
        call(method, B, suffix, token(A)).andExpect(status().isForbidden());
        assertEquals(0, repository.count(), "Denegar antes de crear o modificar un carrito");
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void missingJwtIsUnauthorizedForEveryEndpoint(String method, String suffix) throws Exception {
        call(method, A, suffix, null).andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "blank", "invalid", "number", "sub-only", "app-only", "scope"})
    void authenticatedTokenWithoutValidDelegatedIdentityIsForbidden(String variant) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        switch (variant) {
            case "missing" -> claims.put("oid", null);
            case "blank" -> claims.put("oid", "");
            case "invalid" -> claims.put("oid", "not-an-object-id");
            case "number" -> claims.put("oid", 42);
            case "sub-only" -> { claims.put("oid", null); claims.put("sub", A); }
            case "app-only" -> { claims.put("scp", null); claims.put("roles", new String[]{"Admin"}); }
            case "scope" -> claims.put("scp", "other_access_as_user openid");
            default -> throw new IllegalArgumentException(variant);
        }
        call("GET", A, "", token(A, claims)).andExpect(status().isForbidden());
        assertEquals(0, repository.count());
    }

    @Test
    void matchingSubDoesNotGrantAccessToSomeoneElsesOid() throws Exception {
        call("GET", A, "", token(B, Map.of("sub", A))).andExpect(status().isForbidden());
    }

    @Test
    void ownerWithSeveralScopesIsAcceptedButAdminRoleDoesNotBypassOwnership() throws Exception {
        call("GET", A, "", token(A, Map.of("scp", "openid access_as_user profile")))
            .andExpect(status().isOk());
        call("GET", B, "", token(A, Map.of("roles", new String[]{"Admin"})))
            .andExpect(status().isForbidden());
        assertEquals(1, repository.count());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void sameOidFromAnotherTenantCannotUseAnyEndpoint(String method, String suffix) throws Exception {
        call(method, A, suffix, token(A, Map.of("tid", "33333333-3333-4333-8333-333333333333")))
            .andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant", "missing-tenant", "numeric-tenant", "issuer", "audience", "expired", "future", "signature", "malformed"})
    void decoderRejectsInvalidAzureTokenBeforeController(String variant) throws Exception {
        Map<String, Object> claims = new HashMap<>();
        switch (variant) {
            case "tenant" -> claims.put("tid", "33333333-3333-4333-8333-333333333333");
            case "missing-tenant" -> claims.put("tid", null);
            case "numeric-tenant" -> claims.put("tid", 42);
            case "issuer" -> claims.put("iss", "https://untrusted.example/issuer");
            case "audience" -> claims.put("aud", "different-api");
            case "expired" -> claims.put("exp", Date.from(Instant.now().minusSeconds(300)));
            case "future" -> claims.put("nbf", Date.from(Instant.now().plusSeconds(300)));
            default -> { }
        }
        String jwt = variant.equals("signature") ? token(A, claims, newKey(KEY.getKeyID())) : token(A, claims);
        if (variant.equals("malformed")) jwt = "not-a-jwt";
        call("GET", A, "", jwt).andExpect(status().isUnauthorized());
        assertEquals(0, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {FRONTEND, "http://localhost:4200"})
    void allowedPreflightDoesNotRequireJwt(String origin) throws Exception {
        for (String method : new String[]{"GET", "POST", "DELETE"}) {
            mvc.perform(options(BASE + A + "/items").header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
        assertEquals(0, repository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://w8xu8o4pd7.execute-api.us-east-1.amazonaws.com", "https://evil.example", "https://p5gsy16utd.execute-api.us-east-1.amazonaws.com.evil.example", "null"})
    void unauthorizedOriginsRejectedAtPreflightAndActualRequest(String origin) throws Exception {
        mvc.perform(options(BASE + A).header("Origin", origin)
            .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(get(BASE + A).header("Origin", origin).header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        assertEquals(0, repository.count());
    }

    @Test
    void corsDoesNotBypassJwtOrOwnershipAndRetainsHeadersOnErrors() throws Exception {
        mvc.perform(get(BASE + A).header("Origin", FRONTEND)).andExpect(status().isUnauthorized())
            .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
        mvc.perform(get(BASE + B).header("Origin", FRONTEND).header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isForbidden()).andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
        mvc.perform(get(BASE + A).header("Origin", FRONTEND).header("Authorization", "Bearer " + token(A)))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", FRONTEND));
    }

    @Test
    void preflightRejectsUnapprovedMethodOrHeader() throws Exception {
        mvc.perform(options(BASE + A).header("Origin", FRONTEND).header("Access-Control-Request-Method", "PUT"))
            .andExpect(status().isForbidden());
        mvc.perform(options(BASE + A).header("Origin", FRONTEND).header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "X-User-Id"))
            .andExpect(status().isForbidden());
    }

    @Test
    void existingAddRepeatDeltaReadRemoveAndClearBehaviorIsPreserved() throws Exception {
        long id = add(A);
        call("POST", A, "/items", token(A)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.items[0].cantidad").value(4)).andExpect(jsonPath("$.total").value(51.96));
        mvc.perform(post(BASE + A + "/items").header("Authorization", "Bearer " + token(A))
            .contentType("application/json").content(ITEM.replace("\"cantidad\":2", "\"cantidad\":-1")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cantidad").value(3));
        call("GET", A, "", token(A)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(38.97));
        call("DELETE", A, "/items/" + id, token(A)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        add(A);
        call("DELETE", A, "", token(A)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        call("GET", A, "", token(A)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void foreignItemIdCannotBeDeletedThroughOwnCart() throws Exception {
        long foreignId = add(B);
        add(A);
        call("DELETE", A, "/items/" + foreignId, token(A)).andExpect(status().isOk());
        call("GET", B, "", token(B)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(foreignId)).andExpect(jsonPath("$.items[0].cantidad").value(2));
    }

    @Test
    void bodyCannotInjectForeignPersistentIdOrOwner() throws Exception {
        long foreignId = add(B);
        String injected = ITEM.replace("{", "{\"id\":" + foreignId + ",\"usuarioId\":\"" + B + "\",");
        mvc.perform(post(BASE + A + "/items").header("Authorization", "Bearer " + token(A))
            .contentType("application/json").content(injected)).andExpect(status().isOk())
            .andExpect(jsonPath("$.usuarioId").value(A));
        call("GET", B, "", token(B)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(foreignId)).andExpect(jsonPath("$.items[0].cantidad").value(2));
        String json = call("GET", A, "", token(A)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertNotEquals(foreignId, ((Number) JsonPath.read(json, "$.items[0].id")).longValue());
    }

    @Test
    void ordenStyleDeleteWithOriginalBearerWorksWithoutOriginOrServiceBypass() throws Exception {
        add(A);
        call("DELETE", A, "", token(A)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        add(B);
        call("DELETE", B, "", token(A)).andExpect(status().isForbidden());
        call("GET", B, "", token(B)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cantidad").value(2));
    }
}
