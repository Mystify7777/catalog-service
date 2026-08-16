# CatalogService

A small, genuinely-runnable inventory/catalog REST API, built as the starter
repo for the freeCodeCamp/NHCarrigan Summer 2026 Cohort sprint phase.

Products have a name, SKU, category, price, and stock quantity. The API
supports full CRUD, searching by name or by category, and a dedicated
stock-adjustment endpoint that enforces "stock can never go negative" as a
hard business rule.

This is a real Spring Boot app, not a toy: layered architecture (controller →
service → repository), Bean Validation on every input, transactional
stock-adjustment paths, seed data, and a test suite. The domain is
intentionally shallow so a newcomer can understand the whole codebase and
ship a meaningful PR within a day — see [CONTRIBUTING.md](CONTRIBUTING.md)
for how to claim an issue.

## Stack

- Java 21, Spring Boot 3.3 (Web, Data JPA, Validation, Actuator)
- PostgreSQL 16 for local/dev/prod, via Docker Compose
- H2 (in-memory) for the test suite — no external database needed to run tests
- Maven (with the Maven Wrapper, so you don't need Maven installed globally)

## Quickstart

### Option A: Docker Compose (app + Postgres)

```bash
docker-compose up --build
```

This starts Postgres and the app together. The API will be available at
`http://localhost:8080` once both containers report healthy.

### Option B: Run locally against Docker Postgres

Start just the database:

```bash
docker-compose up postgres
```

Then run the app with the wrapper:

```bash
./mvnw spring-boot:run
```

The app defaults to `jdbc:postgresql://localhost:5432/catalog` with
credentials `catalog`/`catalog` (see `src/main/resources/application.yml`,
overridable via `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
environment variables).

On first startup, `DataSeeder` (a `CommandLineRunner`) inserts seven sample
products if the table is empty, so there's always something to query.

### Running tests

Tests use an in-memory H2 database (configured in
`src/test/resources/application.yml`) and never touch Postgres:

```bash
./mvnw test
```

## API overview

Base path: `/api/products`

| Method | Path                     | Description                                   |
|--------|--------------------------|------------------------------------------------|
| GET    | `/api/products`          | List all products                              |
| GET    | `/api/products/{id}`     | Get a single product by id                     |
| GET    | `/api/products/search?name=` | Search products by name (substring, case-insensitive) |
| GET    | `/api/products/search?category=` | Search products by category (substring, case-insensitive) |
| GET    | `/api/products/inventory-value` | Get the total inventory value and its breakdown by category. |
| POST   | `/api/products`          | Create a product                               |
| PUT    | `/api/products/{id}`     | Replace a product's fields                     |
| DELETE | `/api/products/{id}`     | Delete a product                               |
| PATCH  | `/api/products/{id}/stock` | Adjust stock by a signed delta (`{"delta": -3}`); rejected with `422` if it would go below zero |
| PATCH  | `/api/products/stock/bulk` | Apply multiple stock adjustments atomically; rejected with `422` if any adjustment would make stock negative |

`name` and `category` are mutually exclusive: passing both (even if one is blank) returns `400`.

Health check: `GET /actuator/health` (Spring Boot Actuator).

### Example: create a product

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Desk Lamp","sku":"SKU-9001","category":"Furniture","price":22.50,"stockQuantity":30}'
```

### Example: adjust stock

```bash
curl -X PATCH http://localhost:8080/api/products/1/stock \
  -H "Content-Type: application/json" \
  -d '{"delta": -5}'
```

Validation failures, not-found lookups, duplicate SKUs, and insufficient-stock
errors all return a consistent JSON error body (see
`GlobalExceptionHandler`) with an appropriate HTTP status (`400`, `404`,
`409`, `422`).

Creation with duplicate names (and distinct SKUs) is allowed, but the response (`201`) contains a `"warning"` field.

All requests to the base path and its subpaths are logged with their method, path, and response status, in the format `[<method>] <path>: <status>`. For example, the stock adjustment example logs `[PATCH] /api/products/1/stock: 200`.
### Example: bulk stock adjustment

Bulk stock adjustments accept a list of product IDs and signed stock deltas:

```bash
curl -X PATCH http://localhost:8080/api/products/stock/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {"productId": 1, "delta": 5},
    {"productId": 2, "delta": -3}
  ]'
```

Bulk adjustments use all-or-nothing transaction semantics. The entire
batch is validated before any stock quantity is changed. If any product does
not exist or any adjustment would result in negative stock, the entire batch
is rejected and no stock changes are persisted.

For example, if a batch contains:

```
Product 1: +5
Product 2: -11
```

and Product 2 only has 10 units in stock, the request returns `422 Unprocessable Entity`
and Product 1's `+5` adjustment is also rolled back.

A product may appear more than once in the same batch. Adjustments are applied
sequentially to the projected stock quantity, while the batch remains atomic.

Bulk requests must contain at least one adjustment, and each adjustment
requires both `productId` and `delta`.

Validation failures return `400 Bad Request`. Missing products return
`404 Not Found`, and insufficient stock returns `422 Unprocessable Entity`.

## Project layout

```
src/main/java/com/nhcarrigan/catalogservice/
  entity/       JPA entities (Product)
  dto/          Request payloads
  repository/   Spring Data JPA repositories
  service/      Business logic (ProductService)
  controller/   REST controllers
  exception/    Custom exceptions + global exception handler
  config/       Startup seed data (DataSeeder)
```

## Contributing

Want to pick up an issue? See [CONTRIBUTING.md](CONTRIBUTING.md) for the
claiming flow and how to run everything locally.

## License

[MIT](LICENSE)
