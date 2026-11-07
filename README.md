# ms-pedidos360-catalog

[![CI](https://github.com/emilio-araya/pedidos360-catalog/actions/workflows/ci.yml/badge.svg)](https://github.com/emilio-araya/pedidos360-catalog/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Oracle](https://img.shields.io/badge/Oracle-F80000?logo=oracle&logoColor=white)](https://www.oracle.com/database/)

Microservicio Spring Boot 3.5 / Java 17 para catálogo, stock y reservas idempotas de Pedidos360.

## Funcionalidad

- CRUD de productos y actualización absoluta de stock.
- Reservas de stock por `orderId`, con respuesta y liberación idempotentes.
- Serialización de operaciones de stock mediante bloqueos pesimistas de JPA.
- Seguridad OAuth2 Resource Server con dos cadenas: Entra para `/api/**` y Cognito para `/aws/api/**`.
- Validación JWT, `token_use=access` para Cognito, `cognito:groups` y errores RFC 9457 `ProblemDetail`.
- Proyección de ventas por producto alimentada por los eventos de dominio de pedido en Kafka.
- Perfil `local` con H2 en memoria, datos de ejemplo y JWT HMAC.
- Perfil `cloud` con Oracle y descubrimiento de llaves de Microsoft Entra ID.

## API

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/api/catalog/products?page=0&size=20` o `/aws/api/catalog/products?page=0&size=20` | cualquier autenticado |
| `GET` | `/api/catalog/products/{id}` | cualquier autenticado |
| `POST` | `/api/catalog/products` | `Admin`, `Operador` |
| `PUT` | `/api/catalog/products/{id}` | `Admin`, `Operador` |
| `DELETE` | `/api/catalog/products/{id}` | `Admin`, `Operador` |
| `PATCH` | `/api/catalog/products/{id}/stock` | `Admin`, `Operador` |
| `POST` | `/internal/catalog/stock/reservations` | `Admin`, `Operador` |
| `DELETE` | `/internal/catalog/stock/reservations/{orderId}` | `Admin`, `Operador` |
| `GET` | `/api/catalog/sales` | `Admin`, `Operador` |
| `GET` | `/api/catalog/sales/products/{productId}` | `Admin`, `Operador` |
| `GET` | `/actuator/health` | público |

El listado de productos es paginado: devuelve `content`, `page`, `size`, `totalElements` y `totalPages`, con
orden estable por nombre. `page` debe ser `>= 0` y `size` debe estar entre `1` y `100`; fuera de rango responde
`400` con código `INVALID_PAGINATION`, en lugar de recortar en silencio.

Cada ruta de producto tiene un equivalente bajo `/aws/api/**`; el decoder y el claim de roles se seleccionan por prefijo. Las reservas internas de Entra conservan `/internal/**` por compatibilidad y las de Cognito usan `/aws/api/internal/**`; ninguna se publica en API Gateway.

`PATCH .../stock` recibe el valor absoluto de stock:

```json
{ "stock": 25 }
```

### Reservas

```http
POST /internal/catalog/stock/reservations
Authorization: Bearer <token>
Content-Type: application/json

{
  "orderId": "d8789590-4d19-4ef0-97be-33a73107ed2c",
  "items": [
    { "productId": "f1e13003-0790-4407-bf60-7028b1593d8c", "quantity": 2 }
  ]
}
```

La primera reserva devuelve `201 Created`; una repetición idéntica devuelve `200 OK` sin descontar stock nuevamente. Si se reutiliza el `orderId` con productos o cantidades distintos, devuelve `409 Conflict`. La repetición después de liberar devuelve la reserva existente en estado `RELEASED` y no vuelve a descontar.

`DELETE` restituye el stock una sola vez; si la reserva no existe o ya fue liberada, devuelve `204 No Content`. Un producto con reserva activa no puede eliminarse.

### Reservas por mensajería

`orders` ya no usa los endpoints de reserva: publica `pedido.aceptado`, `pedido.cancelado` y
`pedido.reserva-huerfana`, y el catálogo los consume desde `stock.reserve` y `stock.release` con el mismo servicio
idempotente. Responde `stock.reservado` o, ante un rechazo de negocio (stock insuficiente, producto inexistente o
inactivo), `stock.reserva-fallida` con el código y el motivo. Los endpoints HTTP se mantienen por compatibilidad.
Contrato, reintentos y DLQ: `pedidos360-infra/docs/MENSAJERIA_RABBITMQ.md`.

### Ventas por producto (Kafka)

Kafka convive con RabbitMQ: RabbitMQ sigue llevando los comandos de stock y Kafka los eventos de dominio de pedido.
El catálogo consume `pedidos360.order-events` con el grupo `catalog-sales-projection` y mantiene, por producto, tres
contadores:

| Evento | Efecto |
|---|---|
| `ORDER_ACCEPTED` | `unitsSold += quantity` por ítem |
| `ORDER_DELIVERED` | `unitsDelivered += quantity` por ítem |
| `ORDER_CANCELLED` con `previousStatus` `ACEPTADO`, `EN_PREPARACION` o `DESPACHADO` | `unitsCancelled += quantity` |
| `ORDER_CANCELLED` desde `CREADO` y cualquier otro tipo | sin cambios |

- **Idempotencia:** cada `eventId` se aplica una sola vez; el registro `processed_order_events` y los contadores de
  `product_sales` se escriben en la misma transacción, así una reentrega no cambia nada. Las tablas las crea JPA,
  igual que el resto del esquema.
- **Errores:** JSON ilegible, `eventType` desconocido, `schemaVersion` distinta de `1` o campos obligatorios ausentes
  van directo a `pedidos360.order-events.DLT`, sin reintentos. Un fallo transitorio (base caída, conflicto de versión)
  se reintenta con backoff exponencial y, agotados los reintentos, también termina en la DLT.

```http
GET /api/catalog/sales/products/f1e13003-0790-4407-bf60-7028b1593d8c
Authorization: Bearer <token Admin u Operador>
```

```json
{ "productId": "f1e13003-0790-4407-bf60-7028b1593d8c", "unitsSold": 5, "unitsDelivered": 3, "unitsCancelled": 2, "netUnits": 3 }
```

`netUnits = unitsSold - unitsCancelled`. `GET /api/catalog/sales` devuelve el catálogo completo ordenado por
`productId`: los productos sin ventas aparecen con todos los contadores en cero, y las estadísticas de productos que
ya no están en el catálogo se conservan para no perder historia. `GET /api/catalog/sales/products/{id}` responde
`404` con código `PRODUCT_SALES_NOT_FOUND` sólo cuando el producto no existe ni tiene ventas. `Cliente` recibe `403`
y una request sin token `401`; ambas rutas tienen su espejo bajo
`/aws/api/catalog/sales`. A diferencia de `/api/catalog/products`, las ventas no son públicas. Contrato del evento:
`pedidos360-infra/docs/MENSAJERIA_KAFKA.md`.

## Ejecución local

Requisitos: JDK 17, Maven 3.9+ y OpenSSL para el script opcional de token.

```bash
mvn clean test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

El perfil local crea tablas H2 y carga nueve productos de ejemplo. Se puede desactivar con `LOCAL_SAMPLE_DATA=false`.

Generar un JWT local (`Admin`, `Operador` o `Cliente`; también se pueden separar por coma):

```bash
TOKEN="$(scripts/create-local-token.sh Operador)"
curl -H "Authorization: Bearer $TOKEN" http://localhost:8082/api/catalog/products
```

Variables locales disponibles:

- `LOCAL_JWT_ISSUER` (predeterminado `https://login.microsoftonline.com/pedidos360-local/v2.0`)
- `LOCAL_JWT_AUDIENCE` (predeterminado `api://150f51db-4084-4979-b1a1-e6a6e7893a01`)
- `LOCAL_JWT_HMAC_SECRET` (mínimo 32 caracteres)
- `LOCAL_SAMPLE_DATA`
- `PORT`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` (por defecto
  `localhost:5672`, `guest`, vhost `/`). Sin broker la API responde y `/actuator/health` informa `DOWN`;
  `mvn test` no necesita broker.
- `SPRING_RABBITMQ_ADDRESSES` (opcional): lista de nodos, por ejemplo `rabbitmq-1:5672,rabbitmq-2:5672` en el clúster
  de `pedidos360-infra`. Tiene prioridad sobre `RABBITMQ_HOST`/`RABBITMQ_PORT` y, si un nodo cae, la conexión se reabre
  contra el otro. Las colas `stock.reserve` y `stock.release` son `classic`: si su nodo cae, el consumo se detiene hasta
  que vuelve.
- `SPRING_KAFKA_BOOTSTRAP_SERVERS` (por defecto `localhost:9092`). Sin broker la API responde y el consumidor
  reintenta la conexión en segundo plano.
- `KAFKA_SALES_PROJECTION_ENABLED` (por defecto `true`): propiedad `app.kafka.sales-projection.enabled`; en `false`
  el consumidor de ventas no arranca. Los reintentos se ajustan con `app.kafka.sales-projection.retry.max-retries`
  (4), `.initial-interval-ms` (1000) y `.max-interval-ms` (10000).

## Perfil cloud / Oracle

Activar `cloud` y configurar la conexión exclusivamente mediante variables de entorno:

```bash
export SPRING_PROFILES_ACTIVE=cloud
export ORACLE_URL='jdbc:oracle:thin:@//oracle-host:1521/SERVICE_NAME'
export ORACLE_USERNAME='catalog_user'
export ORACLE_PASSWORD='...'
export ENTRA_ISSUER='https://login.microsoftonline.com/<tenant-id>/v2.0'
export ENTRA_API_AUDIENCE='<entra-api-client-id>'
export COGNITO_ISSUER='https://cognito-idp.us-east-1.amazonaws.com/us-east-1_example'
export COGNITO_API_AUDIENCE='<cognito-app-client-id>'
export COGNITO_JWK_SET_URI='https://cognito-idp.us-east-1.amazonaws.com/us-east-1_example/.well-known/jwks.json'
java -jar target/ms-pedidos360-catalog-1.0.0.jar
```

Variables opcionales: `ORACLE_POOL_MAX_SIZE` (20), `ORACLE_POOL_MIN_IDLE` (2) y `PORT` (8082). El perfil cloud usa `OracleDialect`, actualiza el esquema con Hibernate (`ddl-auto=update`) y nunca carga datos de ejemplo ni habilita el decoder HMAC.

## Seguridad

- En `cloud`, el Resource Server obtiene las llaves del issuer configurado y valida firma, `iss`, `aud`, `exp` y `nbf` cuando está presente.
- El decoder HMAC está limitado por `@Profile("local")` y exige secreto de al menos 32 caracteres.
- El claim `roles` de Entra y `cognito:groups` de Cognito se convierten a autoridades `ROLE_Admin`, `ROLE_Operador` o `ROLE_Cliente`; la comparación distingue mayúsculas y minúsculas.
- El decoder de Cognito exige `token_use=access`; el ID token no puede acceder a la API.
- No se exponen ni se registran tokens ni credenciales.

## Trazabilidad

Cada request lleva un identificador de correlación `X-Request-Id`. Si el cliente lo envía y cumple
`^[A-Za-z0-9._-]{1,64}$` se respeta; si falta o no cumple (por ejemplo, contiene saltos de línea) se genera un
UUID. El id se devuelve en la respuesta y se imprime en cada línea de log, así una request se puede seguir de
punta a punta entre BFF, orders y catalog. Un valor con saltos de línea se descarta a propósito: permitirlo
habilitaría inyectar entradas falsas en el log.

## Pruebas

```bash
mvn verify
```

Las pruebas cubren reglas de stock, servicio de reservas, CRUD y seguridad por MockMvc, repositorios JPA/H2 e idempotencia integral, además de firma/issuer/audience/expiración/`nbf` del JWT local. La proyección de ventas se prueba contra H2 y con un
broker Kafka embebido (`spring-kafka-test`) que verifica el consumo real y la DLT; el resto de la suite arranca con
el consumidor apagado y no necesita broker.

| Métrica | Valor |
|---|---|
| Pruebas | 30 |
| Cobertura de líneas | 84.3% |
| Cobertura de instrucciones | 81.5% |

La CI ejecuta `mvn verify` en cada push y pull request, muestra el resumen en la página del workflow y adjunta el informe HTML de JaCoCo como artefacto. La cobertura de instrucciones es un umbral, no un dato decorativo: el build falla si baja del 70%.

## Imagen Docker

El `Dockerfile` es multi-stage, compila con Maven, ejecuta las pruebas durante `package` y produce una imagen JRE 17 con usuario no root. No se requiere Docker para las pruebas ni para el perfil local.
