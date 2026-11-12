# Fase 1.2 — propietario y CORS de ms-carrito

Fecha: 11/11/2026. Base: `49fddca7d0819ea0ec8e4d5014293057d3a93eb1`.
Rama: `ev3/seguridad-carrito-f1-2`. Cambios exclusivos de Carrito; sin despliegue.

## Política de identidad

El decoder conserva validación de firma Azure, issuer, audience y tiempo y agrega
`tid` exactamente igual a `spring.security.oauth2.resourceserver.jwt.tenant-id`.
El valor por defecto coincide con el tenant específico del issuer existente.
Sin JWT, firma inválida, tenant/issuer/audience inválidos o token fuera de vigencia:
401. El scope delegado requerido es `access_as_user`, ya solicitado por Angular.

Los cuatro endpoints exigen un `oid` GUID y el scope delegado; verifican path == oid
antes de acceder al servicio y pasan **el oid verificado**, no un ID libre.
Identidad lógica: **(tenant fijo permitido, oid)**. La clave persistida sigue siendo
oid para conservar JSON/rutas/carritos existentes; esto solo es seguro con un único
tenant permitido y almacenamiento aislado para ese tenant. No se agregó soporte
multitenant. Cambiar tenant sobre la misma BD requiere migrar/aislar datos primero.

JWT válido sin oid, con oid inválido, sin scope o intentando acceder a otro usuario:
403. No fallback a sub, email, nombre o homeAccountId; roles admin/app-only no tienen
excepción para acceder a carritos ajenos. El rechazo no revela IDs ni crea carrito.
El itemId se busca solo dentro del carrito autorizado; si no está, conserva el
comportamiento anterior de no-op. El DTO POST ignora id/relaciones/usuario enviados
por el cliente y crea una entidad nueva sin ID persistente, evitando mass assignment.
No cambia el contrato productoId/nombreProducto/cantidad/precioUnitario ni el delta
de cantidades existente. Precios y concurrencia de negocio siguen fuera de alcance.

## Análisis MSAL y compatibilidad condicionada

Microsoft documenta `oid` como ID inmutable de usuario o service principal, `tid`
como tenant y `sub` como identificador pairwise por aplicación. `scp` corresponde
a tokens de usuario. Fuente consultada:
https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference

Fuente de la versión MSAL instalada en el frontend recuperado:
`@azure/msal-common/dist/cache/entities/AccountEntity.mjs:112-116` selecciona
`clientInfo.uid`, luego `idTokenClaims.oid`, luego `idTokenClaims.sub` para
localAccountId. `dist/account/AccountInfo.mjs` también construye perfiles con oid/sub.
Por tanto **no hay equivalencia universal localAccountId == access-token oid**.
No se inspeccionaron tokens reales ni se declara comprobado el mapeo del laboratorio.

- Cuenta del tenant cuyo localAccountId coincide exactamente con oid: conserva URL,
  respuesta y persistencia sin cambio Angular.
- Cuentas guest/multitenant/cache con otro ID, fallback a sub o distinta capitalización:
  fail closed 403; no crear alias automáticamente. Debe verificarse antes de desplegar.
- Si hay diferencia, proponer cambio coordinado posterior: resolver identidad del
  tenant activo hacia el oid de recurso validado (posible `/me` backend), usar esa
  misma clave en carrito/orden y migrar datos con evidencia de propiedad. Nunca
  aceptar ambos IDs por mera coincidencia ni confiar en un claim leído sin validar.

No se modificó Angular. El scope ya coincide con API_SCOPE access_as_user.
La prueba con sub distinto demuestra que no se lo confunde con oid.

## Orden → Carrito

En snapshot EV3 `cda0597f...`, OrdenService reenvía el header Bearer original al
DELETE `/carrito/{request.usuarioId}`, con el prefijo /api en su base configurada.
Carrito acepta ese DELETE sin Origin si el path coincide con oid del token
delegado. No requiere token de servicio ni privilegios nuevos. Test local reproduce
ese contrato de entrada sin invocar Orden, compras, SMTP o RabbitMQ.

Orden aún toma usuarioId de su request: si difiere del oid, el DELETE se rechaza
y Orden registra el fallo como no crítico después de crear pedido/publicar evento.
Esta fase no corrige ownership de Orden ni valida su flujo completo. Un token
app-only futuro necesitaría diseño y autorización explícitos, no bypass aquí.

## CORS

Una política central en Spring Security para `/api/carrito/**` sustituye el
@CrossOrigin antiguo. Orígenes exactos:

- `https://p5gsy16utd.execute-api.us-east-1.amazonaws.com`
- `http://localhost:4200`

Métodos GET/POST/DELETE/OPTIONS, headers Authorization/Content-Type, maxAge 3600s.
Sin wildcard ni cookies (`allowCredentials=false`); Bearer explícito sigue permitido.
CorsFilter procesa preflight sin JWT antes de autenticar, sin invocar negocio;
solicitudes reales siguen requiriendo JWT y propietario. CORS no protege de clientes
no navegador ni sustituye JWT. Origen EV2 y dominios parecidos quedan rechazados.
Gateway puede administrar también CORS: verificar política efectiva en ambas capas.

## Pruebas locales y build

Comando final ejecutado: `mvn -B clean verify` (Java 21, release 17 del pom preservado).
Resultado 11/11/2026 22:40 UTC-03: **44 tests, cero fallos/errores/omitidos,
BUILD SUCCESS**, JAR ejecutable generado. 43 casos en integración y uno de contexto.
Warnings visibles: dialecto H2 explícito, open-in-view y auto-attach Mockito/ByteBuddy
en Java 21; no se ocultaron ni se cambió configuración global para eliminarlos.
No es evidencia de Azure/AWS. Sin cambios de dependencias/pom/workflow.

Tests: contexto Spring; JWT RS256 efímeros firmados en memoria; JWKS público servido
solo en loopback, puerto aleatorio. Nimbus real verifica firma y los validadores de
producción. No `jwt()` de MockMvc que omita decoder, no bean JwtDecoder simulado,
ni tokens reales/secretos persistidos; propiedades del test usan tenant/audience
ficticios. No se hace discovery ni se obtiene JWKS de Azure durante los tests.

Casos: endpoints propios/ajenos, ausencia JWT, claims inválidos, scp/roles, mismo oid
en tenant distinto, firma/issuer/audience/exp/nbf, preflight y requests reales CORS,
headers en 401/403, métodos/headers no permitidos, alta/repetición/delta/total/lectura/
DELETE/vaciado, itemId ajeno e inyección por body, DELETE con Bearer reenviado.

## Procedimiento de despliegue posterior — NO ejecutado

1. Revisar y aprobar diff/PR. Confirmar mapeo de identidad con cuentas A/B del tenant
   mediante comprobaciones locales privadas (solo compartir booleanos/resultados,
   nunca JWT completos, cookies o headers Authorization). La respuesta propia 200
   exige ya path=oid por backend; no relajar política si hay un 403.
2. Usuario/ChatGPT reúne por SSM instancia/TG/puerto, digest y configuración actuales,
   issuer/client-id/tenant-id coherentes, URL interna de Carrito en Orden terminada
   en /api y estado de almacenamiento. No reutilizar IDs/IPs EV2. No compartir secretos.
3. Advertir antes del reinicio: configuración actual H2 mem y ddl-auto=create-drop
   pierde carritos al recrear proceso. Si hay datos a conservar, acordar exportación/
   migración verificable antes de swap. Rollback de imagen no recupera H2.
4. Tras nueva autorización, publicar imagen identificada por digest. Workflow actual
   escucha ev3/ci-carrito o dispatch y Dockerfile omite tests; esta rama no lo dispara.
   No ejecutar dispatch ni mover tag mutable hoy. Exigir tests antes de publicación.
5. Planificar cambio controlado/rollback de imagen/config con el usuario, comprobar
   salud del TG y rutas /api/carrito en ALB/Gateway. Una ruta health autenticada puede
   requerir configuración de chequeo existente; no abrir endpoints por conveniencia.
6. Preflight sin JWT desde frontend/localhost acepta GET/POST/DELETE y headers;
   orígenes ajenos/antiguo rechazan; GET real sin token 401 con CORS correcto.
   Distinguir error Gateway, JWT y autorización 403; revisar logs saneados.
7. Con autorización de pruebas y carritos desechables: A consulta/agrega/cambia/
   elimina/vacía solo A; B solo B; A sobre endpoints de B devuelve 403 sin efectos.
   Verificar recarga y cuenta activa en frontend. DELETE con Bearer delegado se
   prueba aisladamente: no ejecutar confirmar compra real para probar limpieza.
8. Registrar digest/config/resultados de ambos componentes antes de marcar E2E.
   Si identidad/Orden requieren adaptación, detener y pedir autorización específica.

## Riesgos conservados fuera de F1.2

H2 efímero, consola H2 permitida por configuración histórica, precios cliente,
validación de cantidad y concurrencia entre pestañas, idempotencia de escrituras,
ownership de Orden/Pedidos y su resultado parcial. No se modifican sin autorización.
Endpoint /h2-console y headers/CSRF existentes preservados; revisar exposición por
Gateway/ALB y deshabilitar consola en producción en otro bloque autorizado.
No Kafka/pagos/FazCoins/recomendaciones ni infraestructura nueva.
