# EV3 cart producer

Kafka is opt-in: `EV3_KAFKA_ENABLED=false` by default. Boot 4.1.1 manages the Kafka
starter. Dockerfile uses Java 21. CI builds/tests only; no registry or cloud actions.

## Isolated local startup

Set `SPRING_PROFILES_ACTIVE=ev3-local`, `CARRITO_DB_URL` (new disposable PostgreSQL
JDBC database), `CARRITO_DB_USER`, `CARRITO_DB_PASSWORD`, `EV3_KAFKA_ENABLED=true`,
`KAFKA_BOOTSTRAP_SERVERS`. Run `./mvnw spring-boot:run` or the Dockerfile image;
container broker/database addresses must be reachable from that container.
Never point this profile at a legacy H2/R2 or real database.

The profile disables SQL initialization/H2 console, overrides `create-drop` with
PostgreSQL `ddl-auto=update` (additive local schema creation), and creates
`ev3_outbox` with a durable BIGINT identity sequence. No destructive migration is
supplied. Production schema changes require a reviewed migration and validation
mode; do not use local auto-update against real data. Preserve the database and
outbox sequence across restarts. No user/cart fixtures are inserted at startup.

`LOCAL_JWT_JWK_SET_URI` is mapped only under `ev3-local`. Normal signature,
issuer/expiration/audience/tenant checks still apply, as do F1.3 UUID `oid`, exact
delegated `access_as_user`, `/api/carrito/me` and owner-equals-path authorization.
No owner/tenant is accepted from request bodies. Local keys must sign the configured
issuer, tenant and audience. No admin, email, service-auth bypass, or AWS calls.

## Consumer agreement

Topic `carrito.actualizado.v1`, Kafka key/aggregateId `tenantId:ownerId`.
Exact envelope `{eventId:UUID,eventType:'carrito.actualizado.v1',schemaVersion:1,
occurredAt:ISO-UTC,producer:'ms-carrito',aggregateId:string,aggregateVersion:long,
correlationId:UUID,data}`. Data is exactly
`{tenantId:UUID,ownerId:UUID,productoId:long,tipo:'ADD'|'UPDATE'|'REMOVE',cantidad:int}`.
No email, names, item prices or token data is included.

First product insertion emits ADD; adding a delta to an existing item emits UPDATE
with the **resulting quantity** (the existing negative-delta UI contract is retained
when the result stays positive). Removing an existing item emits REMOVE/0; clear or
delete emits one REMOVE/0 per actual item. No-op deletion/empty clear emits nothing.
Zero/negative resulting quantities are rejected; use DELETE to remove an item.
Every event is in the same DB transaction as its actual cart mutation. Pessimistic
cart-row locks serialize concurrent existing-cart changes, avoiding lost deltas.
Concurrent first creation can conflict on the existing unique owner constraint;
the failed request rolls back and can be retried by the caller.

Version is the server-generated outbox BIGINT identity, monotonic but not contiguous
per owner; callers cannot supply it. It remains increasing after process restarts
if the DB/identity sequence is retained. Outbox rows and delivered rows are retained.
Never reset/truncate the identity while consumers retain versions.

The scheduled relay (50 rows/batch, 2-second retry, bounded send/metadata timeout)
does not execute in HTTP requests. Kafka outage leaves pending rows committed and
does not block the cart response. It stops a batch on failure, logs a generic warning
without payload/token/secret/exception messages, retries durably. Kafka ACK followed
by DB marking can duplicate on crash: consumers **deduplicate eventId** and treat
cantidad as resulting state, not a delta. Version-aware state projections should
track `(tenantId,ownerId,productoId)`; do not drop distinct item events merely because
another product under the owner has a later version. Run one relay instance per DB
for ordered delivery; consumers must still tolerate duplicates/reordering.

## Tests

`./mvnw verify`: real local signed-JWT/JWKS F1.3 and ownership regression tests,
transaction rollback, ADD/UPDATE/REMOVE envelopes, spoof/no-op tests. These use new
in-memory test databases only, never legacy files.

Optional PostgreSQL + real Kafka test uses the dedicated compose file in the sibling
product producer repository (`docs/compose-ev3-test.yml`):

```powershell
$env:EV3_DOCKER_TEST='true'
$env:CARRITO_DB_URL='jdbc:postgresql://localhost:56431/ev3_carrito_test'
$env:CARRITO_DB_USER='ev3'
$env:CARRITO_DB_PASSWORD='ev3-local-only'
$env:KAFKA_BOOTSTRAP_SERVERS='localhost:19092'
.\mvnw.cmd -B '-Dtest=PostgresKafkaIntegrationTest' test
```

The test guards its disposable database name, simulates a send failure, verifies
the PG row remains pending, then relays to and consumes from actual local Kafka.
Synthetic cart creation is explicit test-only. No existing containers/volumes are
modified. Compose binds loopback only; stop just that compose project when finished.
