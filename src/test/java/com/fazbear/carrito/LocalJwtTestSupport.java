package com.fazbear.carrito;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Claves efímeras de test, solo JWKS público en loopback. Sin Azure/tokens reales. */
public abstract class LocalJwtTestSupport {
    protected static final String TENANT = "11111111-1111-4111-8111-111111111111";
    protected static final String ISSUER = "https://login.microsoftonline.com/" + TENANT + "/v2.0";
    protected static final String AUDIENCE = "22222222-2222-4222-8222-222222222222";
    protected static final String A = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    protected static final String B = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    protected static final RSAKey KEY = newKey("local-test-key");
    private static final HttpServer JWKS = startJwks();

    @DynamicPropertySource
    static void localProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.client-id", () -> AUDIENCE);
        registry.add("spring.security.oauth2.resourceserver.jwt.tenant-id", () -> TENANT);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:carrito-security-test;DB_CLOSE_DELAY=-1");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.h2.console.enabled", () -> "false");
    }

    protected static RSAKey newKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwks() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] json = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                try (var response = exchange.getResponseBody()) { response.write(json); }
                exchange.close();
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String token(String oid) {
        return token(oid, Map.of(), KEY);
    }

    protected static String token(String oid, Map<String, Object> overrides) {
        return token(oid, overrides, KEY);
    }

    protected static String token(String oid, Map<String, Object> overrides, RSAKey signingKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("sub", "pairwise-sub-not-the-object-id");
        claims.put("iat", Date.from(Instant.now()));
        claims.put("exp", Date.from(Instant.now().plusSeconds(300)));
        claims.put("tid", TENANT);
        claims.put("oid", oid);
        claims.put("scp", "access_as_user");
        claims.putAll(overrides);
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID()).build(), builder.build());
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
