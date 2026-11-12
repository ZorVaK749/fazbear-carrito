package com.fazbear.carrito.security;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Política de propietario para el único tenant permitido por este despliegue. */
@Component
public class CarritoOwnerAuthorization {

    private static final Pattern OBJECT_ID = Pattern.compile(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private final String tenantId;

    public CarritoOwnerAuthorization(
            @Value("${spring.security.oauth2.resourceserver.jwt.tenant-id}") String tenantId) {
        this.tenantId = tenantId;
    }

    /** Devuelve la clave verificada, nunca un identificador libre del path/body. */
    public String requireOwner(Jwt jwt, String usuarioId) {
        AuthenticatedOwner owner = requireIdentity(jwt);
        if (!owner.usuarioId().equals(usuarioId)) {
            throw denied();
        }
        return owner.usuarioId();
    }

    /** Contrato compartido por /me y las cuatro operaciones del carrito. */
    public AuthenticatedOwner requireIdentity(Jwt jwt) {
        if (jwt == null || !tenantId.equals(jwt.getClaims().get("tid"))) {
            throw denied();
        }
        Object oidClaim = jwt.getClaims().get("oid");
        Object scopeClaim = jwt.getClaims().get("scp");
        if (!(oidClaim instanceof String oid) || !OBJECT_ID.matcher(oid).matches()
                || !(scopeClaim instanceof String scopes)
                || Arrays.stream(scopes.split("\\s+")).noneMatch("access_as_user"::equals)) {
            throw denied();
        }
        // No fallback a sub, email, homeAccountId, roles admin ni identidad app-only.
        // oid es clave compatible solo bajo el tenant único validado por el decoder.
        return new AuthenticatedOwner(oid, tenantId);
    }

    public record AuthenticatedOwner(String usuarioId, String tenantId) { }

    private AccessDeniedException denied() {
        return new AccessDeniedException("No autorizado para este carrito");
    }
}
