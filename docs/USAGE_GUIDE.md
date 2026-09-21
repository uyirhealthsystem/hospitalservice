# Hospital Service — Usage Guide

A scenario-based walkthrough of this service: what each part is for, who calls it, and
worked examples with mock data you can copy straight into Postman/curl.

For raw copy-paste request bodies without the narrative, see `docs/sample-test-data.txt`.
For the full endpoint list, see `docs/API.md` / `openapi.yaml`.

---

## 0. Before you start: how auth works here

This service does **not** validate login tokens itself. It trusts two headers that (in
production) the API gateway attaches after validating the caller's JWT:

| Header          | Meaning                              |
|------------------|---------------------------------------|
| `X-User-Id`      | who is calling (a patient id or a hospital id) |
| `X-User-Role`    | `PATIENT` or `HOSPITAL`               |

When testing locally/directly against this service, you set these headers yourself.
Endpoints that don't need to know "who", like `GET /api/hospital/emergency-sos`
(anyone can search for a nearby hospital), don't require them at all.

---

## Scenario 1 — Onboarding a new hospital

**Who does this:** an admin / ops team, registering a hospital into the system.

A hospital record holds its identity (`registrationNumber`), address (with geo
coordinates used for "nearby" search), contact info, and — importantly for
emergencies — an `emergencyServices` block describing what it can handle.

**Request**

```
POST {{baseUrl}}/api/hospitals
Content-Type: application/json

{
  "hospitalName": "Apollo General Hospital",
  "registrationNumber": "REG-TN-0001",
  "ownershipType": "PRIVATE",
  "hospitalType": "HOSPITAL",
  "address": {
    "addressLine": "21 Greams Road",
    "city": "Chennai",
    "state": "Tamil Nadu",
    "country": "India",
    "pincode": "600006",
    "location": { "type": "Point", "coordinates": [80.2707, 13.0827] }
  },
  "contactDetails": {
    "phone": "+914428290200",
    "email": "contact@apollochennai.example.com",
    "emergencyHotline": "+914428290911"
  },
  "emergencyServices": {
    "handlesEmergencies": true,
    "specialtyEmergencyConditionsHandled": ["Cardiac Arrest", "Trauma", "Stroke"],
    "ambulanceAvailable": true,
    "ambulanceTypes": ["NORMAL", "VENTILATOR"],
    "ambulance24x7Available": true
  },
  "active": true
}
```

**What matters here:**
- `address.location` uses GeoJSON `[longitude, latitude]` order (not lat/long) — get
  this backwards and "nearby" searches silently return nothing.
- `emergencyServices.handlesEmergencies` is the master switch for whether this
  hospital can ever appear in Emergency SOS results (see Scenario 4). A hospital
  can list conditions in `specialtyEmergencyConditionsHandled` but still be
  invisible to SOS if this flag is `false`.
- `registrationNumber` must be unique — reusing one returns `409 Conflict`.

**Response** (`201 Created`, `Location` header points at the new resource)

```json
{
  "id": "68d5f1a2c9e1a2b3c4d5e601",
  "hospitalName": "Apollo General Hospital",
  "registrationNumber": "REG-TN-0001",
  "ownershipType": "PRIVATE",
  "hospitalType": "HOSPITAL",
  "address": { "...": "as sent" },
  "contactDetails": { "...": "as sent" },
  "emergencyServices": { "...": "as sent" },
  "active": true,
  "createdAt": "2026-09-20T10:00:00Z"
}
```

Save the returned `id` — every other scenario below refers to it as `HOSPITAL_ID`.

**Turning emergency handling off later:** rather than resending the whole record,
a hospital can flip just that flag:

```
PATCH {{baseUrl}}/api/hospitals/{{hospitalId}}/emergency-services/toggle
Content-Type: application/json

{ "handlesEmergencies": false }
```

Do this and the hospital drops out of Emergency SOS results immediately, and any
new Emergency Booking against it will be rejected with `400 Bad Request`
("does not handle emergency type ..."), even if a patient never re-checked SOS.

---

## Scenario 2 — Onboarding a doctor and putting them "on shift"

**Who does this:** hospital staff, adding a doctor to the roster and checking
them in/out as they arrive/leave.

There are **two related concepts** here, and the order you do them in matters:

| Concept | Field | Set by | Meaning |
|---|---|---|---|
| Roster / profile association | `hospitalAssociations[]` | `POST`/`PUT /doctors` | "This doctor formally works at hospital X" (department, fee) — paperwork |
| Live presence | `currentHospitalId` | `POST /doctors/{id}/check-in` | "This doctor is physically here right now" — drives Emergency SOS availability |

⚠️ **Check-in is NOT independent of the association** — `DoctorServiceImpl.checkIn`
requires the doctor to already have a `hospitalAssociations` entry for the target
`hospitalId`; calling check-in for a hospital the doctor has no association with
fails with `400 Bad Request` ("has no hospitalAssociations entry for hospital
'...'"). So the real order is: **1) add the association via `PUT`, 2) then
check in.**

**Create the doctor**

```
POST {{baseUrl}}/api/hospital/doctors
Content-Type: application/json

{
  "name": "Dr. Ananya Rao",
  "sex": "FEMALE",
  "tnmcNumber": "TN-CARD-1001",
  "specialties": ["Cardiology"],
  "yearsOfExperience": 15,
  "consultationFee": 800,
  "engagementType": "REGULAR",
  "doctorCategory": "PRIMARY",
  "contactDetails": { "phone": "+919840010001" }
}
```

`tnmcNumber` must be globally unique across all doctors — reusing one, even
across an update to a *different* doctor, returns `409 Conflict`.

Save the returned `id` as `DOCTOR_ID`.

**Associate the doctor with the hospital** (required before check-in will work —
note this `PUT` replaces the *entire* `hospitalAssociations` array, so if the
doctor already has other associations, include those too):

```
PUT {{baseUrl}}/api/hospital/doctors/{{doctorId}}
Content-Type: application/json

{
  "name": "Dr. Ananya Rao",
  "sex": "FEMALE",
  "tnmcNumber": "TN-CARD-1001",
  "specialties": ["Cardiology"],
  "engagementType": "REGULAR",
  "contactDetails": { "phone": "+919840010001" },
  "hospitalAssociations": [
    { "hospitalId": "{{hospitalId}}", "department": "Cardiology", "consultationFee": 800, "active": true }
  ]
}
```

**Then check the doctor in** (this is the step that actually matters for
Emergency SOS availability):

```
POST {{baseUrl}}/api/hospital/doctors/{{doctorId}}/check-in
Content-Type: application/json

{ "hospitalId": "{{hospitalId}}" }
```

Skipping the association step and calling check-in directly fails with
`400 Bad Request` ("has no hospitalAssociations entry for hospital '...'").

A doctor can be checked in at only one hospital at a time. At end of shift:

```
POST {{baseUrl}}/api/hospital/doctors/{{doctorId}}/check-out
```

**Why this two-step design exists:** a doctor might be formally associated with
three hospitals (roster) but is only physically present at one at any given
moment (check-in). Emergency dispatch needs to know "who's actually here right
now," not "who's on paper" — so SOS matching (Scenario 4) only ever looks at
`currentHospitalId`, never `hospitalAssociations`.

---

## Scenario 3 — A patient searches for care nearby (non-emergency)

**Who does this:** a patient browsing hospitals, or the patient app's "find a
hospital near me" screen.

```
GET {{baseUrl}}/api/hospitals/nearby?longitude=80.27&latitude=13.08&radiusKm=10
```

Returns any active hospital within the radius, nearest first — no emergency
capability filtering, no doctor-availability filtering. This is the general
directory search, distinct from Scenario 4 below.

---

## Scenario 4 — Emergency SOS: a patient is having a medical emergency right now

**Who does this:** the patient app's SOS button, when someone needs urgent
hospital care.

This is the most involved scenario because several conditions must line up
before a hospital is shown as a valid option.

```
GET {{baseUrl}}/api/hospital/emergency-sos?emergencyType=Cardiac%20Arrest&longitude=80.27&latitude=13.08&radiusKm=10
```

**A hospital only appears in the result if ALL of these are true:**

1. **Nearby** — within `radiusKm` of the given point (MongoDB geo `$near` query
   on `address.location`).
2. **Active** — `hospital.active == true`.
3. **Willing** — `emergencyServices.handlesEmergencies == true` AND
   `emergencyType` (case-insensitive) is in `specialtyEmergencyConditionsHandled`.
   This is the only condition that filters by `emergencyType` — the hospital's
   own declared capability list, nothing else.
4. **Staffed** — at least one doctor is currently checked in there
   (`currentHospitalId`) and active. Any checked-in active doctor counts,
   regardless of specialty — the service does not cross-check doctor specialty
   against `emergencyType` (a dermatologist checked in still counts toward
   availability for a cardiac emergency). This is a known, intentional gap;
   see the note below.

**Response** (using the hospital/doctor from Scenarios 1–2, with the doctor
checked in):

```json
[
  {
    "hospitalId": "68d5f1a2c9e1a2b3c4d5e601",
    "hospitalName": "Apollo General Hospital",
    "address": { "...": "..." },
    "contactDetails": { "...": "..." },
    "availableDoctorCount": 1,
    "availableSpecialties": ["Cardiology"]
  }
]
```

If any one of the four conditions above fails, the hospital simply doesn't show
up — there's no error, just an empty (or shorter) list.

**Then the patient books at the chosen hospital:**

```
POST {{baseUrl}}/api/hospital/emergency-bookings
Content-Type: application/json
X-User-Id: patient-001
X-User-Role: PATIENT

{
  "hospitalId": "68d5f1a2c9e1a2b3c4d5e601",
  "emergencyType": "Cardiac Arrest",
  "patientName": "Suresh Kumar",
  "patientPhone": "9876543210"
}
```

The server re-checks condition 3 (hospital active + willing) independently at
booking time — a client can't bypass the SOS search and book directly against a
hospital that doesn't actually handle that emergency type; that request is
rejected with `400 Bad Request`.

**The hospital views its incoming emergency requests:**

```
GET {{baseUrl}}/api/hospital/emergency-bookings
X-User-Id: 68d5f1a2c9e1a2b3c4d5e601
X-User-Role: HOSPITAL
```

Note `X-User-Id` here is the *hospital's own id* — this endpoint only ever
returns bookings for the caller's own hospital, there's no `hospitalId` query
param to look at someone else's.

---

## Scenario 5 — A patient books a routine doctor appointment

**Who does this:** a patient scheduling a non-urgent visit with a specific
doctor (contrast with Scenario 4, which is for emergencies and doesn't target
a specific doctor).

```
POST {{baseUrl}}/api/hospital/doctor-appointments
Content-Type: application/json
X-User-Id: patient-002
X-User-Role: PATIENT

{
  "hospitalId": "68d5f1a2c9e1a2b3c4d5e601",
  "doctorId": "68d5f1a2c9e1a2b3c4d5e602",
  "appointmentDateTime": "2026-09-25T10:30:00Z",
  "reason": "Routine cardiac checkup",
  "patientName": "Meena Pillai",
  "patientPhone": "9123456780"
}
```

`appointmentDateTime` must be in the future — the server rejects past
timestamps with `400 Bad Request`.

**The hospital reschedules it** (only the hospital can; patients can't):

```
PATCH {{baseUrl}}/api/hospital/doctor-appointments/{{bookingId}}/reschedule
Content-Type: application/json
X-User-Id: 68d5f1a2c9e1a2b3c4d5e601
X-User-Role: HOSPITAL

{ "appointmentDateTime": "2026-09-26T11:00:00Z" }
```

Emergency bookings (Scenario 4) have no equivalent reschedule endpoint — by
design, an emergency isn't something you push to next week.

---

## Quick reference: who can call what

| Endpoint | Caller role | Notes |
|---|---|---|
| `POST /api/hospitals` | (unrestricted today) | Admin/ops action |
| `PATCH /api/hospitals/{id}/emergency-services/toggle` | (unrestricted today) | See warning below |
| `POST /api/hospital/doctors/{id}/check-in` | (unrestricted today) | |
| `GET /api/hospital/emergency-sos` | none required | Public search |
| `POST /api/hospital/emergency-bookings` | `PATIENT` | Creates for the calling patient |
| `GET /api/hospital/emergency-bookings` | `HOSPITAL` | Only the caller's own bookings |
| `POST /api/hospital/doctor-appointments` | `PATIENT` | |
| `PATCH /api/hospital/doctor-appointments/{id}/reschedule` | `HOSPITAL` | Only the caller's own hospital's bookings |
| `GET /api/hospital/doctor-appointments` | `HOSPITAL` | Only the caller's own bookings |

⚠️ **Heads up:** hospital and doctor management endpoints (`HospitalController`,
`DoctorController`) currently have **no role restriction at all** — anyone can
create/update/activate/deactivate/toggle any hospital or doctor by id, unlike
the booking endpoints which are gated by `X-User-Role`. If that's not
intentional, it's worth locking those down the same way.

---

## Common errors you'll hit while testing

| Status | Message pattern | Cause |
|---|---|---|
| `400` | `Validation failed` | Missing/blank required field |
| `400` | `does not handle emergency type '...'` | Booking a hospital that doesn't handle that `emergencyType` (or has `handlesEmergencies: false`) |
| `404` | `Hospital not found with id '...'` | Bad/mistyped id, or hospital is inactive (inactive hospitals are treated as not-found for booking) |
| `409` | `Hospital with registration number '...' already exists` | Duplicate `registrationNumber` on create/update |
| `409` | `Doctor with TNMC number '...' already exists` | Duplicate `tnmcNumber` — remember it's checked across *all* doctors, not just the one you're updating |
| `403` | `This action requires the 'HOSPITAL'/'PATIENT' role` | Missing or wrong `X-User-Role` header on a role-gated endpoint |
