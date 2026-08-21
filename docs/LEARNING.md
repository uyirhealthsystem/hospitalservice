# Learning Guide: Hospital CRUD

Walkthrough for someone new to this codebase — how a request flows end to end, and how to extend it.

## Follow one request through the stack

`POST /api/hospitals` with a JSON body:

1. **`HospitalController.create`** (`controller/HospitalController.java`) — Spring binds the JSON body into a `HospitalRequest`. `@Valid` triggers Jakarta Bean Validation (`@NotBlank`, `@NotNull`, `@Valid` cascading into nested `Address`/`ContactDetails`). If validation fails, Spring throws `MethodArgumentNotValidException` *before* the controller method body even runs.
2. Controller calls `hospitalService.create(request)` — no business logic lives in the controller itself.
3. **`HospitalServiceImpl.create`** (`service/impl/HospitalServiceImpl.java`) checks `registrationNumber` uniqueness, converts the DTO to an entity via `HospitalMapper.toEntity`, stamps `active=true` and timestamps, then saves.
4. **`HospitalRepository.save`** — inherited from Spring Data's `MongoRepository`, no custom code needed for a single-document insert.
5. Service maps the saved entity back to a `HospitalResponse` via `HospitalMapper.toResponse` and returns it.
6. Controller wraps it in `201 Created` with a `Location` header pointing at the new resource.

If anything throws along the way (`DuplicateResourceException`, `ResourceNotFoundException`, validation errors, or an unexpected exception), it never reaches the controller's `catch` — there isn't one. **`GlobalExceptionHandler`** (`exception/GlobalExceptionHandler.java`) is a `@RestControllerAdvice`: Spring intercepts the exception globally and converts it into the right `ApiError` JSON + HTTP status.

## Why DTOs instead of returning the entity directly

`Hospital` (the entity) is annotated for MongoDB (`@Document`, `@Id`, `@Indexed`). Returning it straight from the controller works today, but couples the wire format to the persistence format — add a `@Version` field for optimistic locking later, and it leaks into every API response whether you want it there or not. `HospitalRequest`/`HospitalResponse` are the seam that lets those evolve independently. `HospitalMapper` is where the translation happens — deliberately hand-written rather than pulling in MapStruct, since the field set is small enough that a mapping library adds more indirection than it saves.

## Why search isn't five derived-query methods

Spring Data lets you write `findByAddressCityAndActive(...)` and it "just works" via method-name parsing. That's great for one or two filters, but this endpoint has five independent optional filters (`city`, `state`, `hospitalType`, `ownershipType`, `active`) — the combinatorial method-name approach doesn't scale. Instead, `HospitalRepositoryCustomImpl.search` (`repository/HospitalRepositoryCustomImpl.java`) builds a `List<Criteria>` from whichever filters are non-null and combines them with `andOperator`. This is the standard escape hatch in Spring Data Mongo: define a `*Custom` interface, implement it with `MongoTemplate`, and Spring Data automatically composes it into the main repository (`HospitalRepository extends MongoRepository<...>, HospitalRepositoryCustom`) — no extra wiring needed, just naming convention (`HospitalRepository` + `Impl` suffix on the custom impl).

## Gotchas worth knowing

- **GeoJSON coordinate order is `[longitude, latitude]`**, not `[lat, lng]`. This trips up nearly everyone once. `Address.java` has a comment calling it out; `HospitalServiceImpl.findNearby` builds `new Point(longitude, latitude)` in that order deliberately.
- **`$near` requires a `2dsphere` index** when querying a GeoJSON `Point` (as opposed to legacy `[lng, lat]` array coordinates, which use a `2d` index). `MongoIndexConfig` ensures this index exists at startup via `@PostConstruct` — if you see a "unable to find index for $geoNear query" error, it means that component didn't run (e.g., MongoDB wasn't reachable at boot).
- **Soft delete, not hard delete.** `DELETE /api/hospitals/{id}` flips `active` to `false`; the document stays in the collection. If you're writing a query and results seem to include "deleted" hospitals, that's expected — filter on `active=true` explicitly.
- **The service checks uniqueness twice for different reasons.** The `existsByRegistrationNumber` pre-check gives a clean 409 in the common case; the `@Indexed(unique = true)` + `DuplicateKeyException` handling in `GlobalExceptionHandler` is what actually prevents duplicates under concurrent requests (the pre-check has a race window between the check and the insert).
- **No auth yet.** `SecurityConfig` permits everything. Don't mistake that for "this is meant to be public in production" — see `docs/ARCHITECTURE.md` → Security.

## How to add a new field

Example: add `bedOccupancyRate` (a `Double`) to `Hospital`.

1. Add the field to `model/Hospital.java`.
2. Add it to `dto/HospitalRequest.java` and `dto/HospitalResponse.java` (with validation annotations on the request side if it has constraints, e.g. `@DecimalMin("0") @DecimalMax("1")`).
3. Wire it into `mapper/HospitalMapper.java` in all three methods (`toEntity`, `updateEntity`, `toResponse`).
4. Update `docs/API.md`'s example payload.

## How to add a new filter to search

Example: filter by `ownershipType` is already there — to add, say, `minBedCapacity`:

1. Add the param to `HospitalService.search(...)` and `HospitalController.search(...)` (`@RequestParam(required = false) Integer minBedCapacity`).
2. Add a field to `repository/HospitalSearchCriteria.java`.
3. Add a conditional `Criteria.where("facilities.bedCapacity.general").gte(...)` block in `HospitalRepositoryCustomImpl.search`.

## Running it locally

Needs a MongoDB instance reachable at `spring.data.mongodb.uri` (defaults to `mongodb://localhost:27017/hospital`, overridable via the `MONGODB_URI` env var). `MongoIndexConfig` will fail fast on startup if MongoDB isn't reachable, since it calls `ensureIndex` in `@PostConstruct`.
