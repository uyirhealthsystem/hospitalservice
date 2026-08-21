p# Hospital API Reference

Base URL: `/api/hospitals` (every path contains `hospital` per project convention)

All request/response bodies are JSON. All endpoints are unauthenticated for now (see `docs/ARCHITECTURE.md` → Security).

---

## Create a hospital

`POST /api/hospitals`

Request body: `HospitalRequest` (see [JSON shape](#hospitalrequest-shape) below). `registrationNumber` must be unique.

| Status | Meaning |
|---|---|
| 201 Created | Returns the created `HospitalResponse`, `Location` header set to `/api/hospitals/{id}` |
| 400 Bad Request | Validation failed (missing required field, malformed email, etc.) |
| 409 Conflict | `registrationNumber` already exists |

```bash
curl -X POST http://localhost:8080/api/hospitals \
  -H "Content-Type: application/json" \
  -d '{
    "hospitalName": "Uyir Multi-Speciality Hospital",
    "registrationNumber": "REG-TN-2024-00123",
    "ownershipType": "PRIVATE",
    "hospitalType": "SUPER_SPECIALITY",
    "address": {
      "addressLine": "12, Anna Salai",
      "city": "Chennai",
      "state": "Tamil Nadu",
      "country": "India",
      "pincode": "600002",
      "location": { "type": "Point", "coordinates": [80.2707, 13.0827] }
    },
    "contactDetails": { "phone": "+91-9876543210", "email": "contact@uyirhospital.in" },
    "surgicalNetwork": { "joinedSurgicalNetwork": false }
  }'
```

## Get a hospital by id

`GET /api/hospitals/{id}` → 200 `HospitalResponse`, or 404 if not found.

## List / search hospitals (paginated)

`GET /api/hospitals`

| Query param | Type | Notes |
|---|---|---|
| `city` | string | case-insensitive exact match against `address.city` |
| `state` | string | case-insensitive exact match against `address.state` |
| `hospitalType` | enum | `CLINIC`, `NURSING_HOME`, `HOSPITAL`, `SUPER_SPECIALITY`, `MEDICAL_COLLEGE` |
| `ownershipType` | enum | `PRIVATE`, `TRUST` |
| `active` | boolean | filter on soft-delete flag |
| `page` | int | 0-indexed, default `0` |
| `size` | int | default `20`, max `100` |
| `sort` | string | e.g. `sort=hospitalName,asc` (default) |

Returns a `PageResponse<HospitalResponse>`:

```json
{
  "content": [ { "id": "...", "hospitalName": "...", "...": "..." } ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "last": false
}
```

```bash
curl "http://localhost:8080/api/hospitals?city=Chennai&hospitalType=SUPER_SPECIALITY&active=true&page=0&size=20"
```

## Find nearby hospitals (geospatial)

`GET /api/hospitals/nearby?longitude={lng}&latitude={lat}&radiusKm={r}`

Uses the `2dsphere` index on `address.location` (a GeoJSON `Point`, `[longitude, latitude]` order). Returns a plain array of `HospitalResponse`, ordered by proximity — **not paginated**, since MongoDB `$near` doesn't return a total count cheaply.

```bash
curl "http://localhost:8080/api/hospitals/nearby?longitude=80.27&latitude=13.08&radiusKm=5"
```

## Update a hospital

`PUT /api/hospitals/{id}` — full replace of the mutable fields (same body shape as create, `surgicalNetwork` included). 404 if not found, 409 if the new `registrationNumber` collides with a different hospital, 400 on validation failure.

Because this is a full replace, not a patch, clearing `surgicalNetwork` after a hospital opts out means sending the fields back explicitly as `false`/`null` (or `"surgicalNetwork": null` to drop the whole group) — just flipping `joinedSurgicalNetwork` to `false` while leaving the old equipment values in the request body does **not** clear them server-side.

## Surgical Network fields

Part of the create/update body — `surgicalNetwork` on both `HospitalRequest` and `HospitalResponse`. Gated by `joinedSurgicalNetwork`: the six fields below only mean something once that's `true`, the same shape as `EmergencyServices.handlesEmergencies` elsewhere in this model.

| Field | Type | Notes |
|---|---|---|
| `joinedSurgicalNetwork` | boolean | The gate. Everything below only matters when this is `true` |
| `operationTheatreCount` | integer | No. of Operation Theatres |
| `laserEquipmentAvailable` | boolean | Laser Equipment |
| `minimallyInvasiveEquipmentAvailable` | boolean | Minimally Invasive Surgical Equipment |
| `postOpRecoveryRoomAvailable` | boolean | Post-Op Recovery Room |
| `endoscopyAvailable` | boolean | Endoscopy |
| `laparoscopyUnitsAvailable` | boolean | Laparoscopy Units |

```json
"surgicalNetwork": {
  "joinedSurgicalNetwork": true,
  "operationTheatreCount": 4,
  "laserEquipmentAvailable": true,
  "minimallyInvasiveEquipmentAvailable": true,
  "postOpRecoveryRoomAvailable": true,
  "endoscopyAvailable": true,
  "laparoscopyUnitsAvailable": false
}
```

Not enforced server-side: nothing requires the six fields to be present/non-null when `joinedSurgicalNetwork` is `true`, or forbids them when it's `false`. The gate is a UI convention, not a validated constraint — same gap that already exists on `EmergencyServices`.

## Activate / deactivate (soft delete)

- `DELETE /api/hospitals/{id}` → sets `active=false`, returns 204. The document is **not** removed — hospitals are regulated entities and deletions must remain auditable.
- `PATCH /api/hospitals/{id}/activate` → sets `active=true`, returns the updated `HospitalResponse`.

## Error format

Every 4xx/5xx response body is an `ApiError`:

```json
{
  "timestamp": "2026-08-15T09:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/hospitals",
  "details": ["address.city: must not be blank"]
}
```

---

## Full body reference

`HospitalResponse` is every field below plus `id`, `active`, `createdAt`, `updatedAt` (server-assigned). `HospitalRequest` is the same shape minus those four. Every group past `hospitalType` is optional and independently nullable — a hospital can be created with only the required top-level fields and nothing else.

### Top level

| Field | Type | Required | Notes |
|---|---|---|---|
| `hospitalName` | string | yes | |
| `registrationNumber` | string | yes | Unique across all hospitals |
| `ownershipType` | enum | yes | `PRIVATE`, `TRUST` |
| `hospitalType` | enum | yes | `CLINIC`, `NURSING_HOME`, `HOSPITAL`, `SUPER_SPECIALITY`, `MEDICAL_COLLEGE` |
| `address` | Address | yes | |
| `contactDetails` | ContactDetails | yes | |
| `facilities` | Facilities | no | |
| `emergencyServices` | EmergencyServices | no | |
| `staffDetails` | StaffDetails | no | |
| `operations` | Operations | no | |
| `surgicalNetwork` | SurgicalNetwork | no | See [Surgical Network fields](#surgical-network-fields) above |

### Address

| Field | Type | Notes |
|---|---|---|
| `addressLine` | string | required |
| `city` | string | required |
| `district` | string | |
| `state` | string | required |
| `country` | string | required |
| `pincode` | string | required |
| `location` | GeoJSON `Point` | `{ "type": "Point", "coordinates": [longitude, latitude] }` — auto-populated via geocoding in principle, but accepted directly today. Powers `/nearby`; see the [coordinate-order gotcha](#find-nearby-hospitals-geospatial) |

### ContactDetails

| Field | Type | Notes |
|---|---|---|
| `phone` | string | required |
| `alternatePhone` | string | |
| `email` | string | must be a valid email if present |
| `emergencyHotline` | string | |
| `website` | string | |

### Facilities

| Field | Type | Notes |
|---|---|---|
| `bedCapacity` | BedCapacity | `{ general, icu, nicu }` — each an integer bed count |
| `specialtyDepartments` | string[] | free-text department names |
| `diagnosticUnits` | DiagnosticUnits | `{ lab, scanCentre, pathology }` — each a boolean |
| `labDetails` | LabDetails | `{ labName, qualification, facilities[], scanCentreName, pcpndtRegistrationNumber }` |

### EmergencyServices

| Field | Type | Notes |
|---|---|---|
| `handlesEmergencies` | boolean | Gate for the next field |
| `specialtyEmergencyConditionsHandled` | string[] | Populated only when `handlesEmergencies` is `true`; expected to match the notified list of 13 specialty emergency conditions |
| `ambulanceAvailable` | boolean | |
| `ambulanceTypes` | enum[] | `NORMAL`, `BLS`, `VENTILATOR` |
| `ambulance24x7Available` | boolean | |
| `willingToAttachAmbulanceToUdhs` | boolean | |

### StaffDetails

| Field | Type | Notes |
|---|---|---|
| `nursingStaffCount` | integer | |
| `supportiveStaffCount` | integer | |
| `udhsStaff` | NodalStaff[] | Each `{ name, mobileNumber, designation }`. At least 2 required in principle — not currently enforced by validation |

### Operations

| Field | Type | Notes |
|---|---|---|
| `hospitalInsured` | boolean | |
| `dedicatedPharmacyAvailable` | boolean | |
| `hospitalManagementSoftwareInUse` | boolean | |
| `empanelledInsuranceCompanies` | string[] | |
| `scansAvailable` | ScansAvailable | `{ usg, xray, ct, mri }` booleans, plus `other: string[]` for anything not in that list |

### Fully populated example

```json
{
  "hospitalName": "Uyir Multi-Speciality Hospital",
  "registrationNumber": "REG-TN-2024-00123",
  "ownershipType": "PRIVATE",
  "hospitalType": "SUPER_SPECIALITY",
  "address": {
    "addressLine": "12, Anna Salai",
    "city": "Chennai",
    "district": "Chennai",
    "state": "Tamil Nadu",
    "country": "India",
    "pincode": "600002",
    "location": { "type": "Point", "coordinates": [80.2707, 13.0827] }
  },
  "contactDetails": {
    "phone": "+91-9876543210",
    "alternatePhone": "+91-9876543211",
    "email": "contact@uyirhospital.in",
    "emergencyHotline": "+91-9876500000",
    "website": "https://uyirhospital.in"
  },
  "facilities": {
    "bedCapacity": { "general": 80, "icu": 12, "nicu": 6 },
    "specialtyDepartments": ["Cardiology", "Orthopedics", "Neurology"],
    "diagnosticUnits": { "lab": true, "scanCentre": true, "pathology": true },
    "labDetails": {
      "labName": "Uyir Diagnostics",
      "qualification": "NABL Accredited",
      "facilities": ["Blood Bank", "Biochemistry"],
      "scanCentreName": "Uyir Imaging Centre",
      "pcpndtRegistrationNumber": "PCPNDT-TN-2024-0088"
    }
  },
  "emergencyServices": {
    "handlesEmergencies": true,
    "specialtyEmergencyConditionsHandled": ["Cardiac Arrest", "Stroke", "Trauma"],
    "ambulanceAvailable": true,
    "ambulanceTypes": ["BLS", "VENTILATOR"],
    "ambulance24x7Available": true,
    "willingToAttachAmbulanceToUdhs": true
  },
  "staffDetails": {
    "nursingStaffCount": 45,
    "supportiveStaffCount": 20,
    "udhsStaff": [
      { "name": "R. Kumar", "mobileNumber": "+91-9876512345", "designation": "UDHS Coordinator" },
      { "name": "S. Priya", "mobileNumber": "+91-9876512346", "designation": "UDHS Nodal Officer" }
    ]
  },
  "operations": {
    "hospitalInsured": true,
    "dedicatedPharmacyAvailable": true,
    "hospitalManagementSoftwareInUse": true,
    "empanelledInsuranceCompanies": ["Star Health", "ICICI Lombard"],
    "scansAvailable": { "usg": true, "xray": true, "ct": true, "mri": false, "other": ["Doppler"] }
  },
  "surgicalNetwork": {
    "joinedSurgicalNetwork": true,
    "operationTheatreCount": 4,
    "laserEquipmentAvailable": true,
    "minimallyInvasiveEquipmentAvailable": true,
    "postOpRecoveryRoomAvailable": true,
    "endoscopyAvailable": true,
    "laparoscopyUnitsAvailable": false
  }
}
```

`HospitalResponse` returns exactly this shape plus `id`, `active`, `createdAt`, `updatedAt`.

---

# Doctor API Reference

Base URL: `/api/hospital/doctors` (contains `hospital` per the same project convention as above)

All request/response bodies are JSON. Unauthenticated for now, same as the Hospital API. Doctors are independent of hospitals in storage — a doctor's `hospitalAssociations` list is how it links to one or more hospitals, there's no ownership/parent-child relationship in Mongo.

## Create a doctor

`POST /api/hospital/doctors`

Request body: `DoctorRequest` (see [full body reference](#full-body-reference-1) below). `tnmcNumber` must be unique.

| Status | Meaning |
|---|---|
| 201 Created | Returns the created `DoctorResponse`, `Location` header set to `/api/hospital/doctors/{id}` |
| 400 Bad Request | Validation failed |
| 409 Conflict | `tnmcNumber` already exists |

```bash
curl -X POST http://localhost:8080/api/hospital/doctors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Dr. Anitha Raman",
    "age": 41,
    "sex": "FEMALE",
    "tnmcNumber": "TNMC-2011-004521",
    "qualifications": ["MBBS", "MD Cardiology"],
    "specialties": ["Cardiology"],
    "yearsOfExperience": 15,
    "consultationFee": 800,
    "availability": { "type": "CUSTOM_TIMINGS", "customTimings": "Mon-Fri 10am-1pm" },
    "engagementType": "REGULAR",
    "doctorCategory": "PRIMARY",
    "contactDetails": { "phone": "+91-9876543212", "email": "anitha.raman@uyirhospital.in" },
    "hospitalAssociations": [
      { "hospitalId": "66f1a2b3c4d5e6f7a8b9c0d1", "department": "Cardiology", "consultationFee": 800, "active": true }
    ]
  }'
```

## Get a doctor by id

`GET /api/hospital/doctors/{id}` → 200 `DoctorResponse`, or 404 if not found.

## List all doctors

`POST /api/hospital/doctors/list`

No request body. Returns the full, unpaginated `List<DoctorResponse>`.

```bash
curl -X POST http://localhost:8080/api/hospital/doctors/list
```

## Search doctors (paginated)

`POST /api/hospital/doctors/search`

Filters go in the JSON request body (`DoctorSearchRequest`); pagination stays in query params.

| Body field | Type | Notes |
|---|---|---|
| `specialty` | string | case-insensitive exact match against any entry in `specialties` |
| `hospitalId` | string | doctors with a `hospitalAssociations` entry for this hospital id |
| `engagementType` | enum | `REGULAR`, `ON_CALL` |
| `doctorCategory` | enum | `PRIMARY`, `FREELANCER` |
| `active` | boolean | filter on soft-delete flag |

| Query param | Type | Notes |
|---|---|---|
| `page` | int | 0-indexed, default `0` |
| `size` | int | default `20`, max `100` |
| `sort` | string | e.g. `sort=name,asc` (default) |

All body fields are optional — an empty/omitted body returns every doctor, paginated. Returns a `PageResponse<DoctorResponse>` — same envelope shape as the hospital search endpoint.

```bash
curl -X POST "http://localhost:8080/api/hospital/doctors/search?page=0&size=20" \
  -H "Content-Type: application/json" \
  -d '{ "specialty": "Cardiology", "active": true }'
```

## Update a doctor

`PUT /api/hospital/doctors/{id}` — full replace of the mutable fields (same body shape as create). 404 if not found, 409 if the new `tnmcNumber` collides with a different doctor, 400 on validation failure.

## Activate / deactivate (soft delete)

- `DELETE /api/hospital/doctors/{id}` → sets `active=false`, returns 204. Not removed — TNMC registration is a regulated medical license, and deactivations must remain auditable, same reasoning as hospitals.
- `PATCH /api/hospital/doctors/{id}/activate` → sets `active=true`, returns the updated `DoctorResponse`.

Errors use the same `ApiError` shape as the Hospital API.

## Live check-in / check-out

A doctor can be associated with several hospitals via `hospitalAssociations`, but can only be physically present at one at a time. `currentHospitalId` + `checkedInAt` on `Doctor` record that live fact directly, so anything that needs to know "which hospital is this doctor actually available at right now" (e.g. an emergency/SOS routing feature) can filter on `currentHospitalId` instead of naively treating every hospital in `hospitalAssociations` as available.

`POST /api/hospital/doctors/{id}/check-in`

```bash
curl -X POST http://localhost:8080/api/hospital/doctors/<id>/check-in \
  -H "Content-Type: application/json" \
  -d '{ "hospitalId": "66f1a2b3c4d5e6f7a8b9c0d1" }'
```

Sets `currentHospitalId` and stamps `checkedInAt`. Checking in elsewhere implicitly checks the doctor out of wherever they were before — `currentHospitalId` is single-valued, so a doctor can never appear checked into two hospitals at once.

| Status | Meaning |
|---|---|
| 200 OK | Returns the updated `DoctorResponse` |
| 400 Bad Request | Doctor is inactive, or `hospitalId` isn't one of the doctor's own `hospitalAssociations` |
| 404 Not Found | Doctor id or hospital id doesn't exist |

`POST /api/hospital/doctors/{id}/check-out`

```bash
curl -X POST http://localhost:8080/api/hospital/doctors/<id>/check-out
```

Clears `currentHospitalId` and `checkedInAt` to `null`. Idempotent — checking out a doctor who isn't checked in anywhere just returns their current (already-null) state, no error.

> **Known weak point, not yet addressed:** this only reflects reality if check-out actually happens. A doctor who forgets to check out before leaving stays "present" indefinitely, and anything reading `currentHospitalId` (like an emergency router) would keep treating them as available. Not building auto-expiry or a shift-based check-out trigger yet — flagging it here so it isn't a surprise later.

## Full body reference

`DoctorResponse` is every field below plus `id`, `active`, `createdAt`, `updatedAt` (server-assigned). `DoctorRequest` is the same shape minus those four.

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | yes | |
| `age` | integer | no | |
| `sex` | enum | yes | `MALE`, `FEMALE`, `OTHER` |
| `tnmcNumber` | string | yes | Unique across all doctors |
| `qualifications` | string[] | no | |
| `specialties` | string[] | no | |
| `yearsOfExperience` | integer | no | |
| `consultationFee` | decimal | no | |
| `availability` | Availability | no | `{ type, customTimings }` — `type` is one of the `AvailabilityType` enum values; `customTimings` is free text, populated only when `type` is `CUSTOM_TIMINGS` |
| `engagementType` | enum | yes | `REGULAR`, `ON_CALL` |
| `doctorCategory` | enum | no | `PRIMARY`, `FREELANCER` — doctors 2 and 3 on a hospital's roster are tagged `FREELANCER` rather than `PRIMARY` |
| `eSignDocumentUrl` | string | no | Required in practice for teleconsultation prescriptions, not enforced by validation |
| `contactDetails` | ContactDetails | yes | Same shape as the Hospital API's `contactDetails` |
| `hospitalAssociations` | HospitalAssociation[] | no | Each `{ hospitalId, department, consultationFee, active }` — links this doctor to a hospital. No referential check against real `Hospital` ids yet |
| `currentHospitalId` | string | server-managed | Set only via [check-in](#live-check-in--check-out) — not accepted on create/update |
| `checkedInAt` | timestamp | server-managed | Set only via [check-in](#live-check-in--check-out) — not accepted on create/update |

Not enforced server-side: nothing checks that `hospitalId` values in `hospitalAssociations` correspond to real hospitals, and nothing requires at least one association to exist.

---

# Emergency SOS API Reference

Base URL: `/api/hospital/emergency-sos` (contains `hospital` per the same project convention as above)

Cross-cutting feature: joins `Hospital` (geospatial + emergency-handling data) and `Doctor` (live check-in state) to answer "which nearby hospitals can actually treat this emergency right now." Read-only — there's no create/update here, just this one query.

## Find hospitals for an emergency

`GET /api/hospital/emergency-sos`

| Query param | Type | Required | Notes |
|---|---|---|---|
| `emergencyType` | string | yes | Case-insensitive match against a hospital's `emergencyServices.specialtyEmergencyConditionsHandled` |
| `longitude` | double | yes | |
| `latitude` | double | yes | |
| `radiusKm` | double | no | default `10` |

```bash
curl "http://localhost:8080/api/hospital/emergency-sos?emergencyType=Cardiac%20Arrest&longitude=80.27&latitude=13.08&radiusKm=10"
```

**How a hospital qualifies** — all three, in order:
1. Within `radiusKm` of the given point (same `2dsphere` `$near` query `/nearby` uses — results come back nearest-first already, no separate distance sort needed).
2. `active == true` **and** `emergencyServices.handlesEmergencies == true` **and** `emergencyType` appears in `specialtyEmergencyConditionsHandled` (case-insensitive).
3. At least one `Doctor` currently checked in there (`currentHospitalId` equals this hospital's id) with `active == true`. A hospital with zero currently-checked-in doctors is **excluded from the results entirely** — not returned with a "no doctor" flag, just left out.

Returns a plain array of `EmergencyHospitalSuggestion`, not paginated (same reasoning as `/nearby` — `$near` doesn't produce a cheap total count):

```json
[
  {
    "hospitalId": "66f1a2b3c4d5e6f7a8b9c0d1",
    "hospitalName": "Uyir Multi-Speciality Hospital",
    "address": { "addressLine": "12, Anna Salai", "city": "Chennai", "...": "..." },
    "contactDetails": { "phone": "+91-9876543210", "emergencyHotline": "+91-9876500000", "...": "..." },
    "availableDoctorCount": 2,
    "availableSpecialties": ["Cardiology", "General Medicine"]
  }
]
```

> **Doctor specialty is not matched against the emergency type.** `availableDoctorCount` / `availableSpecialties` reflect *any* active doctor currently checked in at that hospital, not specifically a cardiologist for a "Cardiac Arrest" case. There's no clean mapping in the data model between emergency condition names (`specialtyEmergencyConditionsHandled`, e.g. "Cardiac Arrest") and doctor specialties (`Doctor.specialties`, e.g. "Cardiology") — inventing one felt worse than being upfront that "doctor availability" here means general presence, not a specialty-matched guarantee.

> **Inherits the check-in feature's weak point.** This reads `Doctor.currentHospitalId` directly — if a doctor forgot to check out after leaving (see [Live check-in / check-out](#live-check-in--check-out)), a hospital can appear as having emergency coverage it no longer actually has.

**Also fixed while building this** (affects `/nearby` too, not just this endpoint): a missing or malformed required query parameter — omitting `emergencyType`, or passing non-numeric `longitude` — previously fell through to the generic 500 handler instead of a proper `400`. `GlobalExceptionHandler` now handles `MissingServletRequestParameterException` and `MethodArgumentTypeMismatchException` explicitly.
