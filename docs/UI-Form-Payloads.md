# Create Hospital & Create Doctor: Form Payloads for the UI

This is everything the UI needs to build the two registration forms: which fields are dropdowns, the exact values to **send**, labels to **show**, which fields are required, and a complete sample payload for each.

| Form | Endpoint | Who can call |
|---|---|---|
| Create Hospital | `POST /api/v1/hospital` | Logged-in **HOSPITAL** account (the gateway turns the token into `X-User-Role: HOSPITAL`) |
| Create Doctor | `POST /api/v1/hospital/doctors` | Any logged-in user (no role check yet) |

**Golden rule:** show the `label`, send the `value`. Values are case-sensitive for the enum dropdowns (section 1).

> **Just need the dropdown values?** [`UI-Form.md`](UI-Form.md) lists every one in one place, including all 131 specialties and the 16 emergency conditions, with a copy-paste JSON block. This page covers the form rules and full sample payloads.

---

## 1. Fixed dropdowns (the server rejects anything else)

These are Java enums. Sending any other value, including a different case, returns `400`.

### Create Hospital

| Field (JSON path) | Type | Required | Value → Label |
|---|---|---|---|
| `ownershipType` | single select | **yes** | `PRIVATE` → Private · `TRUST` → Trust |
| `hospitalType` | single select | **yes** | `CLINIC` → Clinic · `NURSING_HOME` → Nursing Home · `HOSPITAL` → Hospital · `SUPER_SPECIALITY` → Super Speciality · `MEDICAL_COLLEGE` → Medical College |
| `emergencyServices.ambulanceTypes` | multi select | no | `NORMAL` → Normal · `BLS` → BLS (Basic Life Support) · `VENTILATOR` → Ventilator |
| `facilities.specialtyDepartments` | multi select, grouped by category | no | **Load from `GET /api/v1/hospital/specialties`**, the same master list and rules as doctor specialties (below). Send codes or exact names; stored and returned as names. An unknown value → `400` with a message starting `facilities.specialtyDepartments: unknown ...` |
| `emergencyServices.specialtyEmergencyConditionsHandled` | multi select | no | **Load from `GET /api/v1/hospital/emergency-conditions`** (the 16 SOS conditions). Emergency booking only, not linked to departments. See [Emergency conditions](#emergency-conditions-the-16-sos-choices) |

### Departments vs emergency conditions

| List | Used for | Hospital field | Doctor field |
|---|---|---|---|
| **Specialties** (131, `GET /specialties`) | **Appointment booking** and doctor search | `facilities.specialtyDepartments` | `specialties` |
| **Emergency conditions** (16, `GET /emergency-conditions`) | **Emergency booking** (SOS) only | `emergencyServices.specialtyEmergencyConditionsHandled` | `emergencyConditionsHandled` |

The two lists are independent: choosing one never requires or limits the other.

### Emergency conditions (the 16 SOS choices)

The patient's SOS screen, the hospital form and the doctor form all use the **same 16 conditions**.

`GET /api/v1/hospital/emergency-conditions` is public. It returns the 16 in display order:

```json
[
  { "code": "EMG001", "name": "Chest Pain / Sweating / Palpitation", "icon": "❤️" },
  { "code": "EMG002", "name": "Stroke / Paralysis / Sudden Weakness", "icon": "🧠" },
  { "code": "EMG016", "name": "Other Emergency", "icon": "❗" }
]
```

| Code | Condition | Code | Condition |
|---|---|---|---|
| EMG001 | ❤️ Chest Pain / Sweating / Palpitation | EMG009 | 🩸 Severe Bleeding / Trauma |
| EMG002 | 🧠 Stroke / Paralysis / Sudden Weakness | EMG010 | 🦴 Fall / Fracture |
| EMG003 | ⚡ Fits / Seizures / Fainting | EMG011 | 🤕 Abdominal Pain |
| EMG004 | 🚨 Unconscious / Unresponsive | EMG012 | 🚗 Road Traffic Accident |
| EMG005 | 🫁 Breathing Problem | EMG013 | 🌼 Allergy / Anaphylaxis |
| EMG006 | 🤰 Pregnancy Related Emergency | EMG014 | ☠️ Poison Intake |
| EMG007 | 👶 Child Emergency | EMG015 | 🐍 Snake Bite / Animal Bite |
| EMG008 | 🔥 Burns / Electric Shock | EMG016 | ❗ Other Emergency |

**When is a hospital offered for an emergency?** When the patient picks a condition, a hospital appears in SOS, and the booking is accepted, only if **all four** hold:

1. The hospital is active and **handles emergencies** (`emergencyServices.handlesEmergencies = true`).
2. The **hospital chose** that condition (`emergencyServices.specialtyEmergencyConditionsHandled`).
3. A **doctor associated** with the hospital **also chose** it (doctor's `emergencyConditionsHandled`).
4. That doctor is **on duty there right now**: active, checked in at this hospital, and holding one of its seats.

"Other Emergency" follows the same four rules. If the only matching doctor checks out, the hospital drops out of SOS straight away.

- **Patient SOS screen:** show `icon` + `name` and send the `code` as `emergencyType`: `GET /emergency-sos?emergencyType=EMG002&...` and `POST /emergency-bookings` `{ "emergencyType": "EMG002", ... }`. The exact name also works. Anything else → `400` (`emergencyType: unknown emergency condition ...`). Bookings store and return the official name.
- **Booking errors:** `400` = the hospital doesn't handle emergencies or never chose this condition. **`409`** = it does, but no doctor handling this condition is on duty there right now (`No doctor on duty at hospital ... right now - choose another hospital`). On `409`, refresh SOS results and let the patient pick another hospital.
- **Hospital form** and **doctor form:** offer the 16 as a multi-select and send codes, e.g. `["EMG001", "EMG016"]`. Stored and returned as names; an unknown value → `400` naming it.
- **Before go-live:** hospitals saved with their own condition names (e.g. "Cardiac Arrest") must re-save with the 16, and **every doctor must fill in `emergencyConditionsHandled`**. Until then no hospital appears in SOS.

### Create Doctor

| Field (JSON path) | Type | Required | Value → Label |
|---|---|---|---|
| `sex` | single select | **yes** | `MALE` → Male · `FEMALE` → Female · `OTHER` → Other |
| `engagementType` | single select | **yes** | `REGULAR` → Regular · `ON_CALL` → On Call |
| `doctorCategory` | single select | no | `PRIMARY` → Primary · `FREELANCER` → Freelancer |
| `availability.type` | single select | no | `CUSTOM_TIMINGS` → Custom Timings · `WHATSAPP_BUSINESS_HOURS` → WhatsApp Business Hours |
| `specialties` | multi select, grouped by category | no | **Load from `GET /api/v1/hospital/specialties`** (30 categories, 131 specialties, see below). Appointments and search only |
| `emergencyConditionsHandled` | multi select | no (but needed for SOS) | **Load from `GET /api/v1/hospital/emergency-conditions`** (the 16). The emergencies this doctor attends; a hospital only appears in SOS for a condition while a doctor who chose it is on duty there. See [Emergency conditions](#emergency-conditions-the-16-sos-choices) |

### Doctor specialties (master list, enforced by the server)

Doctors must pick specialties from the master list. Free text is rejected.

`GET /api/v1/hospital/specialties` is public (no login needed). It returns the list grouped by category:

```json
[
  { "category": "General Medicine", "specialties": [
      { "code": "GEN001", "name": "General Physician" },
      { "code": "GEN002", "name": "Family Medicine" } ] },
  { "category": "Heart & Vascular", "specialties": [
      { "code": "CAR001", "name": "Cardiology" },
      { "code": "CAR002", "name": "Interventional Cardiology" } ] }
]
```

- **UI:** show a searchable multi-select grouped by `category`. Display `name`, send `code`, e.g. `"specialties": ["CAR001", "CAR002"]`. The exact `name` is also accepted (case and extra spaces don't matter).
- **Stored and returned as names.** The server converts every code to its official name and removes duplicates, so the doctor response contains `"specialties": ["Cardiology", "Interventional Cardiology"]`. To pre-select values on an edit form, match the returned names against `name` in the list.
- **An unknown value returns `400`** and the message names every bad entry, e.g. `specialties: unknown specialties [Heart Doctor, XYZ999]. Choose from GET /api/v1/hospital/specialties ...`. Nothing is saved.
- **Specialties are optional.** Leaving out `specialties` (or sending `[]`) is allowed.
- **Hemato-Oncology** appears under both Cancer Care (`ONC005`) and Blood Disorders (`HEM003`). Either code saves the same specialty.
- **Doctors saved before this change** keep their old values until someone edits them. On edit, any old free-text value must be re-picked from the list, or the save returns `400`.
- **Doctor search** (`POST /api/v1/hospital/doctors/search`, field `specialty`) accepts a code or a name.

---
 
## 2. Suggested dropdowns (the server accepts any text, so the UI should control the values)

The server stores these as plain strings. Use a dropdown anyway, because other features compare the text:

- **District** is used by district reports and `GET /district/{district}`. "Chennai" and "Chenai" would count as two different districts.

| Field (JSON path) | Form | Type | Allow "Other" free text? |
|---|---|---|---|
| `address.district` | Hospital | single select | No |
| `address.state` | Hospital | single select | No |
| `address.country` | Hospital | fixed `India` | No |
| `operations.empanelledInsuranceCompanies` | Hospital | multi select | Yes |
| `operations.scansAvailable.other` | Hospital | multi select | Yes |
| `facilities.labDetails.facilities` | Hospital | multi select | Yes |
| `staffDetails.udhsStaff[].designation` | Hospital | single select | Yes |
| `hospitalAssociations[].department` | Doctor | single select (use the specialty names from `GET /api/v1/hospital/specialties`) | Yes |
| `qualifications` | Doctor | multi select | Yes |

The lists are in section 4 as JSON you can copy straight into the frontend.

---

## 3. Form rules the server does NOT enforce (the UI must)

| Rule | Why |
|---|---|
| `address.location.coordinates` is **`[longitude, latitude]`**, longitude first | GeoJSON order. Swapping them puts the hospital in the wrong place, and nearby search and SOS break. Chennai is `[80.2707, 13.0827]` |
| Show `emergencyServices.specialtyEmergencyConditionsHandled` only when `handlesEmergencies` is `true` | A hospital with `handlesEmergencies: false` never matches SOS, even if conditions are filled in |
| Show `ambulanceTypes`, `ambulance24x7Available`, `willingToAttachAmbulanceToUdhs` only when `ambulanceAvailable` is `true` | |
| Show the other `surgicalNetwork.*` fields only when `joinedSurgicalNetwork` is `true` | |
| `staffDetails.udhsStaff`: at least **2** people | Programme requirement, not validated by the server |
| Show `availability.customTimings` (free text, e.g. `Mon-Fri 10am-1pm`) only when `availability.type` is `CUSTOM_TIMINGS` | |
| Show `facilities.labDetails.*` only when `diagnosticUnits.lab` or `diagnosticUnits.scanCentre` is ticked; `pcpndtRegistrationNumber` applies to scan centres | |
| `pincode`: 6 digits. Phones: 10-digit Indian mobile, optionally with a `+91` prefix | Only "not blank" is checked server-side |

Server-side checks the UI should expect:

| Status | When |
|---|---|
| `400` | Required field missing or blank, bad enum value, malformed `email` |
| `409` | `registrationNumber` (hospital) or `tnmcNumber` (doctor) already exists |
| `403` | Create Hospital called by a non-HOSPITAL account |

---

## 4. Dropdown data (copy into the frontend)

```json
{
  "hospital": {
    "ownershipType": [
      { "value": "PRIVATE", "label": "Private" },
      { "value": "TRUST", "label": "Trust" }
    ],
    "hospitalType": [
      { "value": "CLINIC", "label": "Clinic" },
      { "value": "NURSING_HOME", "label": "Nursing Home" },
      { "value": "HOSPITAL", "label": "Hospital" },
      { "value": "SUPER_SPECIALITY", "label": "Super Speciality" },
      { "value": "MEDICAL_COLLEGE", "label": "Medical College" }
    ],
    "ambulanceTypes": [
      { "value": "NORMAL", "label": "Normal" },
      { "value": "BLS", "label": "BLS (Basic Life Support)" },
      { "value": "VENTILATOR", "label": "Ventilator" }
    ],
    "district": [
      "Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore", "Dharmapuri", "Dindigul",
      "Erode", "Kallakurichi", "Kancheepuram", "Kanniyakumari", "Karur", "Krishnagiri", "Madurai",
      "Mayiladuthurai", "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai",
      "Ramanathapuram", "Ranipet", "Salem", "Sivaganga", "Tenkasi", "Thanjavur", "Theni",
      "Thoothukudi", "Tiruchirappalli", "Tirunelveli", "Tirupathur", "Tiruppur", "Tiruvallur",
      "Tiruvannamalai", "Tiruvarur", "Vellore", "Viluppuram", "Virudhunagar"
    ],
    "state": ["Tamil Nadu", "Puducherry"],
    "country": ["India"],
    "specialtyEmergencyConditionsHandled": "load from GET /api/v1/hospital/emergency-conditions (enforced by the server, see section 1)",
    "specialtyDepartments": "load from GET /api/v1/hospital/specialties (enforced by the server, see section 1)",
    "scansAvailableOther": ["PET-CT", "Mammography", "DEXA", "Echo", "ECG", "TMT", "EEG", "Doppler"],
    "labFacilities": [
      "Haematology", "Biochemistry", "Microbiology", "Pathology", "Histopathology",
      "Serology", "Clinical Pathology", "Blood Bank"
    ],
    "empanelledInsuranceCompanies": [
      "CMCHIS (Chief Minister's Comprehensive Health Insurance Scheme)", "PM-JAY (Ayushman Bharat)",
      "New India Assurance", "United India Insurance", "Oriental Insurance", "National Insurance",
      "Star Health", "Niva Bupa", "Care Health", "HDFC ERGO", "ICICI Lombard", "Tata AIG",
      "Aditya Birla Health", "ManipalCigna", "SBI General"
    ],
    "udhsStaffDesignation": [
      "Medical Officer", "Duty Doctor", "Staff Nurse", "Nursing Superintendent",
      "Hospital Administrator", "Front Office", "Ambulance Coordinator"
    ]
  },
  "doctor": {
    "sex": [
      { "value": "MALE", "label": "Male" },
      { "value": "FEMALE", "label": "Female" },
      { "value": "OTHER", "label": "Other" }
    ],
    "engagementType": [
      { "value": "REGULAR", "label": "Regular" },
      { "value": "ON_CALL", "label": "On Call" }
    ],
    "doctorCategory": [
      { "value": "PRIMARY", "label": "Primary" },
      { "value": "FREELANCER", "label": "Freelancer" }
    ],
    "availabilityType": [
      { "value": "CUSTOM_TIMINGS", "label": "Custom Timings" },
      { "value": "WHATSAPP_BUSINESS_HOURS", "label": "WhatsApp Business Hours" }
    ],
    "specialties": "load from GET /api/v1/hospital/specialties (enforced by the server, see section 1)",
    "qualifications": [
      "MBBS", "MD", "MS", "DNB", "DM", "MCh", "Diploma (PG)", "FRCS", "MRCP",
      "BDS", "MDS", "BSMS (Siddha)", "BAMS (Ayurveda)", "BHMS (Homeopathy)", "BUMS (Unani)", "BNYS"
    ]
  }
}
```

**About the lists in section 4:**
- **Enum lists** (any entry with `value`/`label`) come from the server code and are exact. If the backend adds a value, this file gets updated.
- **District** is Tamil Nadu's 38 districts. Send exactly these spellings.
- **Emergency conditions** come from `GET /api/v1/hospital/emergency-conditions` (the 16 SOS conditions, section 1).
- **Doctor specialties and hospital departments** are not in this block. They come from `GET /api/v1/hospital/specialties`, so the UI always matches what the server accepts.
- **Qualifications, insurance, scans, lab facilities, designations** are reasonable starting lists, not official ones. Product can add to them freely, and the server accepts any text.

---

## 5. Complete sample: Create Hospital

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
    "emergencyHotline": "044-12345678",
    "website": "https://uyirhospital.in"
  },
  "facilities": {
    "bedCapacity": { "general": 120, "icu": 20, "nicu": 8 },
    "specialtyDepartments": ["CAR001", "NEU001", "ORT001"],
    "diagnosticUnits": { "lab": true, "scanCentre": true, "pathology": true },
    "labDetails": {
      "labName": "Uyir Diagnostics",
      "qualification": "MD Pathology",
      "facilities": ["Haematology", "Biochemistry", "Microbiology"],
      "scanCentreName": "Uyir Imaging",
      "pcpndtRegistrationNumber": "PCPNDT-TN-0457"
    }
  },
  "emergencyServices": {
    "handlesEmergencies": true,
    "specialtyEmergencyConditionsHandled": ["EMG001", "EMG002", "EMG010", "EMG016"],
    "ambulanceAvailable": true,
    "ambulanceTypes": ["BLS", "VENTILATOR"],
    "ambulance24x7Available": true,
    "willingToAttachAmbulanceToUdhs": true
  },
  "staffDetails": {
    "nursingStaffCount": 60,
    "supportiveStaffCount": 40,
    "udhsStaff": [
      { "name": "R. Kumar", "mobileNumber": "9876500001", "designation": "Medical Officer" },
      { "name": "S. Priya", "mobileNumber": "9876500002", "designation": "Staff Nurse" }
    ]
  },
  "operations": {
    "hospitalInsured": true,
    "dedicatedPharmacyAvailable": true,
    "hospitalManagementSoftwareInUse": true,
    "empanelledInsuranceCompanies": ["CMCHIS (Chief Minister's Comprehensive Health Insurance Scheme)", "Star Health"],
    "scansAvailable": { "usg": true, "xray": true, "ct": true, "mri": false, "other": ["Echo"] }
  },
  "surgicalNetwork": {
    "joinedSurgicalNetwork": true,
    "operationTheatreCount": 4,
    "laserEquipmentAvailable": false,
    "minimallyInvasiveEquipmentAvailable": true,
    "postOpRecoveryRoomAvailable": true,
    "endoscopyAvailable": true,
    "laparoscopyUnitsAvailable": true
  }
}
```

**Minimum valid body** (required fields only): `hospitalName`, `registrationNumber`, `ownershipType`, `hospitalType`, `address.{addressLine, city, state, country, pincode}`, `contactDetails.phone`. Every `true/false` field left out is saved as `false`.

---
w
## 6. Complete sample: Create Doctor

```json
{
  "name": "Dr. Anitha Raman",
  "age": 41,
  "sex": "FEMALE",
  "tnmcNumber": "TNMC-2011-004521",
  "qualifications": ["MBBS", "MD"],
  "specialties": ["CAR001", "CAR002"],
  "emergencyConditionsHandled": ["EMG001", "EMG004"],
  "yearsOfExperience": 15,
  "consultationFee": 800,
  "availability": { "type": "CUSTOM_TIMINGS", "customTimings": "Mon-Fri 10am-1pm" },
  "engagementType": "REGULAR",
  "doctorCategory": "PRIMARY",
  "eSignDocumentUrl": "https://files.uyir.in/esign/anitha-raman.pdf",
  "contactDetails": { "phone": "+91-9876543212", "email": "anitha.raman@uyirhospital.in" },
  "hospitalAssociations": [
    { "hospitalId": "66f1a2b3c4d5e6f7a8b9c0d1", "department": "Cardiology", "consultationFee": 800, "active": true }
  ]
}
```

**Minimum valid body:** `name`, `sex`, `tnmcNumber`, `engagementType`, `contactDetails.phone`.

Notes:
- `hospitalAssociations[].hospitalId` is the `id` returned by Create Hospital. The UI should pick it from the logged-in hospital rather than let the user type it, because the server doesn't check that it exists.
- `currentHospitalId` / `checkedInAt` are set by check-in/check-out, never on create.
- `eSignDocumentUrl` is optional on create, but needed in practice for teleconsult prescriptions.
