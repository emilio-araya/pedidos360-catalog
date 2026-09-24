# ms-pedidos360-catalog

Microservicio Spring Boot 3.5 / Java 17 para catálogo, stock y reservas idempotas de Pedidos360.

## Funcionalidad

- CRUD de productos y actualización absoluta de stock.
- Reservas de stock por `orderId`, con respuesta y liberación idempotentes.
- Serialización de operaciones de stock mediante bloqueos pesimistas de JPA.
- Seguridad OAuth2 Resource Server, validación JWT y errores RFC 9457 `ProblemDetail`.
- Perfil `local` con H2 en memoria, datos de ejemplo y JWT HMAC.
- Perfil `cloud` con Oracle y descubrimiento de llaves de Microsoft Entra ID.

## API

| Método | Ruta | Acceso |
|---|---|---|
| `GET` | `/api/catalog/products` | cualquier autenticado |
| `GET` | `/api/catalog/products/{id}` | cualquier autenticado |
| `POST` | `/api/catalog/products` | `Admin`, `Operador` |
| `PUT` | `/api/catalog/products/{id}` | `Admin`, `Operador` |
| `DELETE` | `/api/catalog/products/{id}` | `Admin`, `Operador` |
| `PATCH` | `/api/catalog/products/{id}/stock` | `Admin`, `Operador` |
| `POST` | `/internal/catalog/stock/reservations` | `Admin`, `Operador` |
| `DELETE` | `/internal/catalog/stock/reservations/{orderId}` | `Admin`, `Operador` |
| `GET` | `/actuator/health` | público |

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

## Ejecución local

Requisitos: JDK 17, Maven 3.9+ y OpenSSL para el script opcional de token.

```bash
mvn clean test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

El perfil local crea tablas H2 y carga tres productos de ejemplo. Se puede desactivar con `LOCAL_SAMPLE_DATA=false`.

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

## Perfil cloud / Oracle

Activar `cloud` y configurar la conexión exclusivamente mediante variables de entorno:

```bash
export SPRING_PROFILES_ACTIVE=cloud
export ORACLE_URL='jdbc:oracle:thin:@//oracle-host:1521/SERVICE_NAME'
export ORACLE_USERNAME='catalog_user'
export ORACLE_PASSWORD='...'
export ENTRA_ISSUER='https://login.microsoftonline.com/<tenant-id>/v2.0'
export ENTRA_API_AUDIENCE='150f51db-4084-4979-b1a1-e6a6e7893a01'
java -jar target/ms-pedidos360-catalog-1.0.0.jar
```

Variables opcionales: `ORACLE_POOL_MAX_SIZE` (20), `ORACLE_POOL_MIN_IDLE` (2) y `PORT` (8082). El perfil cloud usa `OracleDialect`, actualiza el esquema con Hibernate (`ddl-auto=update`) y nunca carga datos de ejemplo ni habilita el decoder HMAC.

## Seguridad

- En `cloud`, el Resource Server obtiene las llaves del issuer configurado y valida firma, `iss`, `aud`, `exp` y `nbf` cuando está presente.
- El decoder HMAC está limitado por `@Profile("local")` y exige secreto de al menos 32 caracteres.
- El claim `roles` de Entra se convierte a autoridades `ROLE_Admin`, `ROLE_Operador` o `ROLE_Cliente`; la comparación distingue mayúsculas y minúsculas.
- No se exponen ni se registran tokens ni credenciales.

## Pruebas

```bash
mvn test
```

Las pruebas cubren reglas de stock, servicio de reservas, CRUD y seguridad por MockMvc, repositorios JPA/H2 e idempotencia integral, además de firma/issuer/audience/expiración/`nbf` del JWT local.

## Imagen Docker

El `Dockerfile` es multi-stage, compila con Maven, ejecuta las pruebas durante `package` y produce una imagen JRE 17 con usuario no root. No se requiere Docker para las pruebas ni para el perfil local.
