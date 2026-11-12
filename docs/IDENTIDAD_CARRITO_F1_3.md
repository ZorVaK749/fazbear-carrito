# F1.3 — contrato de identidad Carrito / Angular

Fecha: 12/11/2026. Base backend: `8478261e454f43f0fdfd9b7a847e814e5a553f5d`.
Rama: `ev3/identidad-carrito-f1-3`. Sin merge, imagen ni despliegue.

## Contrato

`GET /api/carrito/me`, autenticado con el mismo Bearer delegado Azure que las
operaciones de carrito. Respuesta 200 con `Cache-Control: no-store`:

```json
{
  "usuarioId": "oid-validado-del-access-token",
  "tenantId": "tid-validado-del-access-token"
}
```

La representación anterior es orientativa; los dos valores reales son GUIDs.
Solo expone esos dos identificadores propios, sin JWT, scopes, email o saldos.
No crea/lee un carrito ni modifica BD. No reconoce parámetros de identidad:
`?usuarioId=B&tenantId=otro` no cambia el principal ni la respuesta del usuario A.

`CarritoOwnerAuthorization.requireIdentity()` reutiliza reglas F1.2: tid del tenant
único permitido, oid GUID y scope delegado exacto access_as_user. El decoder Nimbus
sigue verificando firma/JWKS/issuer/audience/vigencia/tenant. `requireOwner()` llama
a ese mismo método y además compara path == oid para las cuatro rutas anteriores.

- Sin JWT o validación criptográfica/tenant fallida: 401.
- JWT autenticado sin oid válido o sin scope requerido: 403.
- Operaciones sobre usuario ajeno: 403.
- Mapping GET literal `/me` tiene prioridad sobre `/{usuarioId}`; no existe un
  carrito `me`. Mutaciones a ese identificador no pasan la política GUID/propietario.

No se relajó CORS ni se agregaron dependencias, excepciones admin/app-only o scopes.

## Consumidor frontend

Frontend parte de `92cbbcad3eb8598266e9a117af3e1cc42a2ca94e`, rama de igual nombre.
Inicialización serial: `/me` → GET carrito con usuarioId devuelto → escrituras FIFO.
No fallback a localAccountId/sub; MSAL/interceptor continúan obteniendo Bearer.
El tenant devuelto debe coincidir con la autoridad Azure configurada en frontend,
no se exige coincidencia con el tenant de origen de una cuenta guest.

Cada sesión captura la cuenta MSAL y mantiene su propia identidad solo en memoria.
Logout/cambio de cuenta cancelan peticiones y descartan callbacks/colas anteriores;
comprobar sesión después del await evita respuesta tardía, incluso si ya llegó.
Error/timeout de /me bloquea GET/escrituras/checkout; ACTUALIZAR reintenta lectura,
nunca reenvía las escrituras descartadas. 404 del backend antiguo también falla cerrado.
El snapshot confirmado propaga usuarioId correcto al JSON de Orden. tenantId es
metadato local y no altera su contrato. Backend sigue autorizando cada operación:
la respuesta /me y las validaciones del navegador no sustituyen autorización.

## Datos históricos y Orden

No se cambian esquema/clave usuario_id ni se migran/mezclan carritos. Si un carrito
histórico ya usa oid del tenant fijo, sigue accesible. Si usa sub/clientInfo.uid u
otro alias, no se lo reasigna automáticamente; puede aparecer vacío el carrito con
clave correcta. Migración/recuperación requiere demostrar propiedad de ambas claves
y autorización aparte. No aceptar ambos IDs para ocultar diferencias.

Orden reenvía el Bearer delegado original y usuarioId del payload; al recibir oid
desde el snapshot, su DELETE sigue siendo compatible con la política de Carrito.
No se modificó ni se probó el servicio Orden real. Su propia autorización/total y
su tratamiento de fallo de limpieza siguen pendientes; /me no los corrige.

## Pruebas y límites

`mvn -B clean verify`: **56/56**, sin fallos/errores/omitidos, JAR generado.
Se conservan 44 casos F1.2 y agregan 12 sobre /me: A/B, solo dos campos/no-store,
sin efectos BD, parámetros sin autoridad, sin JWT/oid/scope, tenant inválido,
CORS y rutas antiguas/mutaciones sin alias. JWT RS256 efímeros y JWKS loopback
de test; Nimbus y filtros/controlador/JPA reales, sin Azure ni tokens reales.
Warnings H2Dialect/open-in-view/Mockito existentes no ocultados.

## Despliegue futuro — NO ejecutado

1. Aprobar ramas y publicación de imágenes por digest en otro bloque.
2. Backend primero: incluye F1.2 y /me. Confirmar rutas Gateway `/api/carrito/me`
   y cuatro rutas originales, sin quitar JWT ni depender de `$default` accidentalmente.
   Validar CORS Spring/Gateway/ALB para GET/preflight con Authorization.
3. Revisar configuración issuer/audience/tenant, digest previo y persistencia H2:
   mem/create-drop pierde datos al reiniciar; acordar preservación antes del swap.
4. Solo después publicar/desplegar frontend compatible con /me. Si se despliega
   frontend primero contra backend antiguo, falla cerrado; no añadir fallback.
5. Con cuentas/carritos de prueba y autorización: /me identifica A/B, cada uno usa
   su oid, accesos ajenos 403, sin JWT 401, recarga/logout/cambio de cuenta y errores.
   Probar DELETE delegado aislado, sin compras/SMTP/RabbitMQ.
6. Evidencia por digest/config de ambos componentes antes de E2E. Rollback frontend
   requiere revisar mapeo del cliente viejo; rollback backend sin /me obliga a
   coordinar frontend. Rollback Docker no recupera datos H2 ni migra aliases.

IMPLEMENTADO / PROBADO LOCALMENTE; publicación registrada en informe acumulativo.
NO DESPLEGADO / NO VALIDADO E2E. PAUSADO — esperando autorización.
