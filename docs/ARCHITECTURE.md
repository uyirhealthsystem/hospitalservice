# Architecture

## Layering

```
controller  →  service (interface + impl)  →  repository  →  MongoDB
     ↑                    ↑
   dto (Request/Response)  mapper (entity ↔ dto)
```

- **`model`** — MongoDB documents (`Hospital`, `Doctor`) and embedded value objects (`Address`, `ContactDetails`, ...). These are the persistence shape.
- **`dto`** — `HospitalRequest` / `HospitalResponse` / `PageResponse<T>`. The API's public shape, decoupled from persistence so the schema can evolve independently (e.g. add a `@Version` field to the entity later without breaking clients).
- **`mapper`** — `HospitalMapper` converts between entity and DTO. Plain Java, no reflection-based mapping library, to keep the dependency footprint small.
- **`repository`** — `HospitalRepository` (Spring Data `MongoRepository`) for simple CRUD/derived queries, plus `HospitalRepositoryCustom`/`HospitalRepositoryCustomImpl` for the dynamic multi-filter search built with `MongoTemplate` + `Criteria` (a derived-query method per filter combination doesn't scale).
- **`service`** — business rules: uniqueness checks, soft delete, geospatial query orchestration.
- **`controller`** — thin HTTP layer: binds request params, delegates to service, no business logic.
- **`exception`** — `GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes error → HTTP status mapping so controllers stay free of try/catch.
- **`config`** — `SecurityConfig` (HTTP security posture) and `MongoIndexConfig` (index bootstrap on startup).

## Key design decisions

**Soft delete over hard delete.** `DELETE /api/hospitals/{id}` sets `active=false` rather than removing the document. Hospitals are regulated entities (registration numbers, UDHS staff records) — losing that history isn't acceptable, and downstream reports/audits need to see deactivated hospitals, not just current ones.

**Uniqueness is enforced twice.** The service pre-checks `existsByRegistrationNumber` for a fast, friendly 409 in the common case, but the real guarantee is the `@Indexed(unique = true)` on `Hospital.registrationNumber` (`Hospital.java:32`) plus `GlobalExceptionHandler` catching `DuplicateKeyException`. The pre-check alone has a check-then-insert race under concurrent writes; the unique index is the actual source of truth.

**Custom search over derived query explosion.** `GET /api/hospitals` accepts five independent optional filters. A derived-query method per combination (`findByAddressCityAndHospitalTypeAndActive...`) doesn't scale past two or three filters. `HospitalRepositoryCustomImpl` builds a `Criteria` list from whatever filters are present and runs one `count` + one `find`, both windowed identically so pagination metadata stays correct.

**Geospatial as a first-class query.** `Address.location` is a `GeoJsonPoint` (`Address.java:23`) specifically so `$near` queries work. `MongoIndexConfig` ensures a `2dsphere` index on `address.location` at startup — MongoDB requires this index for `$near` against GeoJSON points. The `/nearby` endpoint returns a plain list rather than a `Page`, since `$near` result ordering (by distance) doesn't come with a cheap total count.

**Regex search input is escaped.** The `city`/`state` filters build a case-insensitive regex (`^city$`, flag `i`). User input is passed through `Pattern.quote(...)` before being embedded in the regex string to avoid regex-injection / ReDoS from unescaped special characters in a query param.

## Scalability considerations

- **Pagination everywhere** — the list/search endpoint never returns an unbounded result set; `spring.data.web.pageable.max-page-size=100` caps `size` server-side regardless of what a client requests.
- **Indexes** for every filterable/sortable field used by the API: `registrationNumber` (unique), `address.city`, `hospitalType`, `active`, plus the `2dsphere` geo index. These keep `search()` and `/nearby` off collection scans as the dataset grows.
- **Stateless REST** — no server-side session state, so the service can be horizontally scaled behind a load balancer without sticky sessions.
- **DTO boundary** — because the API shape is decoupled from the entity, the entity can gain fields (e.g. optimistic-locking `@Version`, audit fields, denormalized counters) without an API version bump.

## Security (current state — TODO before production)

`SecurityConfig` currently `permitAll()`s every request. `spring-boot-starter-security` is on the classpath but no auth mechanism (JWT/OAuth2/API key) is wired up yet. This was a deliberate scaffolding choice — without it, Spring Security's default auto-configuration would put a random generated password behind HTTP Basic on every endpoint, making the API unusable until a real strategy is picked. Follow-up: choose an auth model (this is hospital/patient-adjacent data — likely OAuth2/JWT with role-based access for hospital-admin vs. read-only consumers) and replace the `permitAll()` rule.

## Not yet built (roadmap)

- Doctor CRUD (this pass scoped to Hospital only)
- Optimistic locking (`@Version`) for concurrent updates
- Rate limiting / API versioning
- Integration tests (`spring-boot-starter-data-mongodb-test` / Testcontainers is already a test dependency, just unused so far)
