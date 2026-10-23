# UI Form Dropdowns

Every dropdown value the frontend needs for the **Create Hospital** form, the **Create Doctor** form and the **patient Emergency (SOS)** screen, in one place.

> Values are taken directly from the server's source (Java enums, `src/main/resources/specialties.json`, `src/main/resources/emergency-conditions.json`) and match exactly what the API accepts. If those files change, update this page too. Request/response samples and form rules are in [`UI-Form-Payloads.md`](UI-Form-Payloads.md).

## How to use these values

| Rule | Example |
|---|---|
| **Show the label, send the value** | Show *Nursing Home*, send `"NURSING_HOME"` |
| Fixed values (enums) are **case-sensitive** | `"nursing_home"` → `400` |
| Specialties and emergency conditions: **send the code** | Send `"CAR001"`, the server saves *Cardiology* |
| Responses return the **name**, not the code | `"specialties": ["Cardiology"]`; match on `name` to pre-select an edit form |
| Anything not in a fixed list → **`400`** | The error message names every bad value |

Two lists can also be loaded live (public, no login). Prefer these over hard-coding, so a new specialty shows up without a frontend release:

| Endpoint | Returns |
|---|---|
| `GET /api/v1/hospital/specialties` | 30 categories, 131 specialties: `[{ category, specialties: [{ code, name }] }]` |
| `GET /api/v1/hospital/emergency-conditions` | 16 conditions: `[{ code, name, icon }]` |

---

## 1. Which dropdown goes where

### Create Hospital (`POST /api/v1/hospital`)

| Field (JSON path) | Control | Required | Values |
|---|---|---|---|
| `ownershipType` | Single select | **Yes** | [Ownership type](#ownership-type) |
| `hospitalType` | Single select | **Yes** | [Hospital type](#hospital-type) |
| `address.district` | Single select | No | [Districts](#districts) |
| `address.state` | Single select | **Yes** | [State](#state--country) |
| `address.country` | Fixed `India` | **Yes** | [Country](#state--country) |
| `facilities.specialtyDepartments` | Multi select, grouped | No | [Specialties](#specialties) (appointments) |
| `emergencyServices.specialtyEmergencyConditionsHandled` | Multi select | No | [Emergency conditions](#emergency-conditions) (emergencies). Show only when *Handles emergencies* is on |
| `emergencyServices.ambulanceTypes` | Multi select | No | [Ambulance type](#ambulance-type). Show only when *Ambulance available* is on |
| `operations.empanelledInsuranceCompanies` | Multi select + "Other" | No | [Insurance](#3-suggested-lists-free-text-allowed) (suggested) |
| `operations.scansAvailable.other` | Multi select + "Other" | No | [Other scans](#3-suggested-lists-free-text-allowed) (suggested) |
| `facilities.labDetails.facilities` | Multi select + "Other" | No | [Lab facilities](#3-suggested-lists-free-text-allowed) (suggested) |
| `staffDetails.udhsStaff[].designation` | Single select + "Other" | No | [Designation](#3-suggested-lists-free-text-allowed) (suggested) |

### Create Doctor (`POST /api/v1/hospital/doctors`)

| Field (JSON path) | Control | Required | Values |
|---|---|---|---|
| `sex` | Single select | **Yes** | [Sex](#sex) |
| `engagementType` | Single select | **Yes** | [Engagement type](#engagement-type) |
| `doctorCategory` | Single select | No | [Doctor category](#doctor-category) |
| `availability.type` | Single select | No | [Availability type](#availability-type). If `CUSTOM_TIMINGS`, also show a free-text `availability.customTimings` |
| `specialties` | Multi select, grouped | No | [Specialties](#specialties) (appointments & search) |
| `emergencyConditionsHandled` | Multi select | No, **but needed for SOS** | [Emergency conditions](#emergency-conditions) (emergencies) |
| `hospitalAssociations[].department` | Single select + "Other" | No | [Specialties](#specialties) names (suggested) |
| `qualifications` | Multi select + "Other" | No | [Qualifications](#3-suggested-lists-free-text-allowed) (suggested) |

### Patient Emergency (SOS) screen

| Field | Control | Required | Values |
|---|---|---|---|
| `emergencyType` (`GET /emergency-sos?emergencyType=…`, `POST /emergency-bookings`) | Big icon buttons, pick one | **Yes** | [Emergency conditions](#emergency-conditions): show `icon` + `name`, send `code` |

> **Specialties ≠ emergency conditions.** Specialties/departments are for **appointment booking** and doctor search. The 16 emergency conditions are for **emergency booking** only. They are separate lists: picking one never requires or limits the other.

---

## 2. Fixed values (the server rejects anything else)

### Ownership type

Used by: Hospital `ownershipType`

| Send (`value`) | Show (`label`) |
|---|---|
| `PRIVATE` | Private |
| `TRUST` | Trust |

### Hospital type

Used by: Hospital `hospitalType`

| Send (`value`) | Show (`label`) |
|---|---|
| `CLINIC` | Clinic |
| `NURSING_HOME` | Nursing Home |
| `HOSPITAL` | Hospital |
| `SUPER_SPECIALITY` | Super Speciality |
| `MEDICAL_COLLEGE` | Medical College |

### Ambulance type

Used by: Hospital `emergencyServices.ambulanceTypes` (multi select)

| Send (`value`) | Show (`label`) |
|---|---|
| `NORMAL` | Normal |
| `BLS` | BLS (Basic Life Support) |
| `VENTILATOR` | Ventilator |

### Sex

Used by: Doctor `sex`

| Send (`value`) | Show (`label`) |
|---|---|
| `MALE` | Male |
| `FEMALE` | Female |
| `OTHER` | Other |

### Engagement type

Used by: Doctor `engagementType`

| Send (`value`) | Show (`label`) |
|---|---|
| `REGULAR` | Regular |
| `ON_CALL` | On Call |

### Doctor category

Used by: Doctor `doctorCategory`

| Send (`value`) | Show (`label`) |
|---|---|
| `PRIMARY` | Primary |
| `FREELANCER` | Freelancer |

### Availability type

Used by: Doctor `availability.type`

| Send (`value`) | Show (`label`) |
|---|---|
| `CUSTOM_TIMINGS` | Custom Timings |
| `WHATSAPP_BUSINESS_HOURS` | WhatsApp Business Hours |

### Emergency conditions

Used by: patient `emergencyType` (SOS + emergency booking), hospital `emergencyServices.specialtyEmergencyConditionsHandled`, doctor `emergencyConditionsHandled`. Show in this order.

| Send (`code`) | Icon | Show (`name`) |
|---|---|---|
| `EMG001` | ❤️ | Chest Pain / Sweating / Palpitation |
| `EMG002` | 🧠 | Stroke / Paralysis / Sudden Weakness |
| `EMG003` | ⚡ | Fits / Seizures / Fainting |
| `EMG004` | 🚨 | Unconscious / Unresponsive |
| `EMG005` | 🫁 | Breathing Problem |
| `EMG006` | 🤰 | Pregnancy Related Emergency |
| `EMG007` | 👶 | Child Emergency |
| `EMG008` | 🔥 | Burns / Electric Shock |
| `EMG009` | 🩸 | Severe Bleeding / Trauma |
| `EMG010` | 🦴 | Fall / Fracture |
| `EMG011` | 🤕 | Abdominal Pain |
| `EMG012` | 🚗 | Road Traffic Accident |
| `EMG013` | 🌼 | Allergy / Anaphylaxis |
| `EMG014` | ☠️ | Poison Intake |
| `EMG015` | 🐍 | Snake Bite / Animal Bite |
| `EMG016` | ❗ | Other Emergency |

A hospital appears in SOS for a condition only when **(1)** it handles emergencies, **(2)** it chose the condition, **(3)** a doctor also chose it, and **(4)** that doctor is on duty there now (checked in, holding a seat). So both forms matter: a hospital with no matching doctor on duty never shows up.

### Specialties

Used by: doctor `specialties`, hospital `facilities.specialtyDepartments` (appointment booking & doctor search). 30 categories, 131 specialties. Show as a searchable multi-select grouped by category.

| # | Category | Specialties |
|---|---|---|
| 1 | [General Medicine](#general-medicine) | 4 |
| 2 | [Women's Health](#womens-health) | 5 |
| 3 | [Child Health](#child-health) | 6 |
| 4 | [Skin & Hair](#skin--hair) | 5 |
| 5 | [Mental Health](#mental-health) | 5 |
| 6 | [Heart & Vascular](#heart--vascular) | 5 |
| 7 | [Brain & Nervous System](#brain--nervous-system) | 5 |
| 8 | [Bone & Joint](#bone--joint) | 6 |
| 9 | [ENT](#ent) | 5 |
| 10 | [Eye Care](#eye-care) | 6 |
| 11 | [Dental Care](#dental-care) | 8 |
| 12 | [Digestive System](#digestive-system) | 4 |
| 13 | [Kidney & Urinary](#kidney--urinary) | 4 |
| 14 | [Lung & Respiratory](#lung--respiratory) | 4 |
| 15 | [Endocrine & Diabetes](#endocrine--diabetes) | 4 |
| 16 | [Cancer Care](#cancer-care) | 5 |
| 17 | [Surgery](#surgery) | 5 |
| 18 | [Emergency & Critical Care](#emergency--critical-care) | 4 |
| 19 | [Physical Medicine](#physical-medicine) | 4 |
| 20 | [Nutrition](#nutrition) | 4 |
| 21 | [Blood Disorders](#blood-disorders) | 3 |
| 22 | [Infectious Diseases](#infectious-diseases) | 3 |
| 23 | [Rheumatology & Autoimmune](#rheumatology--autoimmune) | 2 |
| 24 | [Anaesthesia](#anaesthesia) | 3 |
| 25 | [Radiology & Imaging](#radiology--imaging) | 3 |
| 26 | [Pathology & Laboratory](#pathology--laboratory) | 4 |
| 27 | [Alternative Medicine](#alternative-medicine) | 6 |
| 28 | [Sexual Health](#sexual-health) | 3 |
| 29 | [Genetics](#genetics) | 2 |
| 30 | [Transplant Medicine](#transplant-medicine) | 4 |

#### General Medicine

| Send (`code`) | Show (`name`) |
|---|---|
| `GEN001` | General Physician |
| `GEN002` | Family Medicine |
| `GEN003` | Internal Medicine |
| `GEN004` | Geriatric Medicine |

#### Women's Health

| Send (`code`) | Show (`name`) |
|---|---|
| `GYN001` | Obstetrics & Gynaecology |
| `GYN002` | Infertility Specialist |
| `GYN003` | Reproductive Medicine |
| `GYN004` | Maternal-Fetal Medicine |
| `GYN005` | Menopause Specialist |

#### Child Health

| Send (`code`) | Show (`name`) |
|---|---|
| `PED001` | Pediatrics |
| `PED002` | Neonatology |
| `PED003` | Pediatric Cardiology |
| `PED004` | Pediatric Neurology |
| `PED005` | Pediatric Surgery |
| `PED006` | Pediatric Orthopedics |

#### Skin & Hair

| Send (`code`) | Show (`name`) |
|---|---|
| `DER001` | Dermatology |
| `DER002` | Cosmetology |
| `DER003` | Trichology (Hair Specialist) |
| `DER004` | Venereology |
| `DER005` | Aesthetic Medicine |

#### Mental Health

| Send (`code`) | Show (`name`) |
|---|---|
| `PSY001` | Psychiatry |
| `PSY002` | Clinical Psychology |
| `PSY003` | Neuropsychiatry |
| `PSY004` | Addiction Psychiatry |
| `PSY005` | Counselling Psychology |

#### Heart & Vascular

| Send (`code`) | Show (`name`) |
|---|---|
| `CAR001` | Cardiology |
| `CAR002` | Interventional Cardiology |
| `CAR003` | Electrophysiology |
| `CAR004` | Cardiothoracic Surgery |
| `CAR005` | Vascular Surgery |

#### Brain & Nervous System

| Send (`code`) | Show (`name`) |
|---|---|
| `NEU001` | Neurology |
| `NEU002` | Neurosurgery |
| `NEU003` | Stroke Specialist |
| `NEU004` | Epileptology |
| `NEU005` | Neuro Rehabilitation |

#### Bone & Joint

| Send (`code`) | Show (`name`) |
|---|---|
| `ORT001` | Orthopedics |
| `ORT002` | Joint Replacement |
| `ORT003` | Sports Medicine |
| `ORT004` | Spine Surgery |
| `ORT005` | Hand Surgery |
| `ORT006` | Trauma Specialist |

#### ENT

| Send (`code`) | Show (`name`) |
|---|---|
| `ENT001` | ENT Specialist |
| `ENT002` | Otology |
| `ENT003` | Rhinology |
| `ENT004` | Laryngology |
| `ENT005` | Head & Neck Surgery |

#### Eye Care

| Send (`code`) | Show (`name`) |
|---|---|
| `OPH001` | Ophthalmology |
| `OPH002` | Retina Specialist |
| `OPH003` | Cornea Specialist |
| `OPH004` | Glaucoma Specialist |
| `OPH005` | Pediatric Ophthalmology |
| `OPH006` | Oculoplasty |

#### Dental Care

| Send (`code`) | Show (`name`) |
|---|---|
| `DEN001` | General Dentistry |
| `DEN002` | Orthodontics |
| `DEN003` | Prosthodontics |
| `DEN004` | Endodontics |
| `DEN005` | Periodontics |
| `DEN006` | Oral Surgery |
| `DEN007` | Pedodontics |
| `DEN008` | Implantology |

#### Digestive System

| Send (`code`) | Show (`name`) |
|---|---|
| `GAS001` | Gastroenterology |
| `GAS002` | Hepatology |
| `GAS003` | GI Surgery |
| `GAS004` | Colorectal Surgery |

#### Kidney & Urinary

| Send (`code`) | Show (`name`) |
|---|---|
| `NEP001` | Nephrology |
| `NEP002` | Urology |
| `NEP003` | Renal Transplant Specialist |
| `NEP004` | Uro-Oncology |

#### Lung & Respiratory

| Send (`code`) | Show (`name`) |
|---|---|
| `PUL001` | Pulmonology |
| `PUL002` | Sleep Medicine |
| `PUL003` | Critical Care Medicine |
| `PUL004` | Respiratory Medicine |

#### Endocrine & Diabetes

| Send (`code`) | Show (`name`) |
|---|---|
| `END001` | Endocrinology |
| `END002` | Diabetology |
| `END003` | Thyroid Specialist |
| `END004` | Metabolic Medicine |

#### Cancer Care

| Send (`code`) | Show (`name`) |
|---|---|
| `ONC001` | Medical Oncology |
| `ONC002` | Surgical Oncology |
| `ONC003` | Radiation Oncology |
| `ONC004` | Pediatric Oncology |
| `ONC005` | Hemato-Oncology |

#### Surgery

| Send (`code`) | Show (`name`) |
|---|---|
| `SUR001` | General Surgery |
| `SUR002` | Laparoscopic Surgery |
| `SUR003` | Bariatric Surgery |
| `SUR004` | Trauma Surgery |
| `SUR005` | Robotic Surgery |

#### Emergency & Critical Care

| Send (`code`) | Show (`name`) |
|---|---|
| `EMC001` | Emergency Medicine |
| `EMC002` | Critical Care |
| `EMC003` | Trauma Medicine |
| `EMC004` | Disaster Medicine |

#### Physical Medicine

| Send (`code`) | Show (`name`) |
|---|---|
| `PMR001` | Physiotherapy |
| `PMR002` | Physical Medicine & Rehabilitation |
| `PMR003` | Sports Rehabilitation |
| `PMR004` | Occupational Therapy |

#### Nutrition

| Send (`code`) | Show (`name`) |
|---|---|
| `NUT001` | Dietitian |
| `NUT002` | Nutritionist |
| `NUT003` | Sports Nutritionist |
| `NUT004` | Clinical Nutritionist |

#### Blood Disorders

| Send (`code`) | Show (`name`) |
|---|---|
| `HEM001` | Hematology |
| `HEM002` | Transfusion Medicine |
| `HEM003` | Hemato-Oncology |

#### Infectious Diseases

| Send (`code`) | Show (`name`) |
|---|---|
| `INF001` | Infectious Disease Specialist |
| `INF002` | HIV Medicine |
| `INF003` | Tropical Medicine |

#### Rheumatology & Autoimmune

| Send (`code`) | Show (`name`) |
|---|---|
| `RHE001` | Rheumatology |
| `RHE002` | Clinical Immunology |

#### Anaesthesia

| Send (`code`) | Show (`name`) |
|---|---|
| `ANA001` | Anaesthesiology |
| `ANA002` | Pain Medicine |
| `ANA003` | Critical Care Anaesthesia |

#### Radiology & Imaging

| Send (`code`) | Show (`name`) |
|---|---|
| `RAD001` | Radiology |
| `RAD002` | Interventional Radiology |
| `RAD003` | Nuclear Medicine |

#### Pathology & Laboratory

| Send (`code`) | Show (`name`) |
|---|---|
| `PAT001` | Pathology |
| `PAT002` | Microbiology |
| `PAT003` | Biochemistry |
| `PAT004` | Laboratory Medicine |

#### Alternative Medicine

| Send (`code`) | Show (`name`) |
|---|---|
| `ALT001` | Ayurveda |
| `ALT002` | Siddha |
| `ALT003` | Homeopathy |
| `ALT004` | Naturopathy |
| `ALT005` | Unani |
| `ALT006` | Yoga Therapy |

#### Sexual Health

| Send (`code`) | Show (`name`) |
|---|---|
| `SEX001` | Sexology |
| `SEX002` | Andrology |
| `SEX003` | Reproductive Health |

#### Genetics

| Send (`code`) | Show (`name`) |
|---|---|
| `GENE001` | Medical Genetics |
| `GENE002` | Genetic Counselling |

#### Transplant Medicine

| Send (`code`) | Show (`name`) |
|---|---|
| `TRN001` | Organ Transplant Specialist |
| `TRN002` | Liver Transplant |
| `TRN003` | Kidney Transplant |
| `TRN004` | Heart Transplant |

> **Hemato-Oncology** is listed under both *Cancer Care* (`ONC005`) and *Blood Disorders* (`HEM003`). Either code saves the same specialty, so show it once if the doctor ticks both.

### Districts

Used by: hospital `address.district`. Tamil Nadu's 38 districts. Send exactly this spelling: district reports and `GET /district/{district}` match on it.

| | | | |
|---|---|---|---|
| Ariyalur | Chengalpattu | Chennai | Coimbatore |
| Cuddalore | Dharmapuri | Dindigul | Erode |
| Kallakurichi | Kancheepuram | Kanniyakumari | Karur |
| Krishnagiri | Madurai | Mayiladuthurai | Nagapattinam |
| Namakkal | Nilgiris | Perambalur | Pudukkottai |
| Ramanathapuram | Ranipet | Salem | Sivaganga |
| Tenkasi | Thanjavur | Theni | Thoothukudi |
| Tiruchirappalli | Tirunelveli | Tirupathur | Tiruppur |
| Tiruvallur | Tiruvannamalai | Tiruvarur | Vellore |
| Viluppuram | Virudhunagar |  |  |

### State & country

| Field | Values |
|---|---|
| `address.state` | `Tamil Nadu`, `Puducherry` |
| `address.country` | `India` (fixed) |

---

## 3. Suggested lists (free text allowed)

The server accepts any text for these. Offer the list plus an **"Other"** option that opens a text box.

| List | Used by | Values |
|---|---|---|
| Qualifications | Doctor `qualifications` | MBBS, MD, MS, DNB, DM, MCh, Diploma (PG), FRCS, MRCP, BDS, MDS, BSMS (Siddha), BAMS (Ayurveda), BHMS (Homeopathy), BUMS (Unani), BNYS |
| Insurance companies | Hospital `operations.empanelledInsuranceCompanies` | CMCHIS (Chief Minister's Comprehensive Health Insurance Scheme), PM-JAY (Ayushman Bharat), New India Assurance, United India Insurance, Oriental Insurance, National Insurance, Star Health, Niva Bupa, Care Health, HDFC ERGO, ICICI Lombard, Tata AIG, Aditya Birla Health, ManipalCigna, SBI General |
| Other scans | Hospital `operations.scansAvailable.other` | PET-CT, Mammography, DEXA, Echo, ECG, TMT, EEG, Doppler |
| Lab facilities | Hospital `facilities.labDetails.facilities` | Haematology, Biochemistry, Microbiology, Pathology, Histopathology, Serology, Clinical Pathology, Blood Bank |
| UDHS staff designation | Hospital `staffDetails.udhsStaff[].designation` | Medical Officer, Duty Doctor, Staff Nurse, Nursing Superintendent, Hospital Administrator, Front Office, Ambulance Coordinator |

---

## 4. Copy-paste JSON

Everything above as one object, ready to drop into the frontend as constants. Specialties and emergency conditions are identical to what the two `GET` endpoints return.

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
    "district": ["Ariyalur", "Chengalpattu", "Chennai", "Coimbatore", "Cuddalore", "Dharmapuri", "Dindigul", "Erode", "Kallakurichi", "Kancheepuram", "Kanniyakumari", "Karur", "Krishnagiri", "Madurai", "Mayiladuthurai", "Nagapattinam", "Namakkal", "Nilgiris", "Perambalur", "Pudukkottai", "Ramanathapuram", "Ranipet", "Salem", "Sivaganga", "Tenkasi", "Thanjavur", "Theni", "Thoothukudi", "Tiruchirappalli", "Tirunelveli", "Tirupathur", "Tiruppur", "Tiruvallur", "Tiruvannamalai", "Tiruvarur", "Vellore", "Viluppuram", "Virudhunagar"],
    "state": ["Tamil Nadu", "Puducherry"],
    "country": ["India"]
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
    ]
  },
  "emergencyConditions": [
    { "code": "EMG001", "name": "Chest Pain / Sweating / Palpitation", "icon": "❤️" },
    { "code": "EMG002", "name": "Stroke / Paralysis / Sudden Weakness", "icon": "🧠" },
    { "code": "EMG003", "name": "Fits / Seizures / Fainting", "icon": "⚡" },
    { "code": "EMG004", "name": "Unconscious / Unresponsive", "icon": "🚨" },
    { "code": "EMG005", "name": "Breathing Problem", "icon": "🫁" },
    { "code": "EMG006", "name": "Pregnancy Related Emergency", "icon": "🤰" },
    { "code": "EMG007", "name": "Child Emergency", "icon": "👶" },
    { "code": "EMG008", "name": "Burns / Electric Shock", "icon": "🔥" },
    { "code": "EMG009", "name": "Severe Bleeding / Trauma", "icon": "🩸" },
    { "code": "EMG010", "name": "Fall / Fracture", "icon": "🦴" },
    { "code": "EMG011", "name": "Abdominal Pain", "icon": "🤕" },
    { "code": "EMG012", "name": "Road Traffic Accident", "icon": "🚗" },
    { "code": "EMG013", "name": "Allergy / Anaphylaxis", "icon": "🌼" },
    { "code": "EMG014", "name": "Poison Intake", "icon": "☠️" },
    { "code": "EMG015", "name": "Snake Bite / Animal Bite", "icon": "🐍" },
    { "code": "EMG016", "name": "Other Emergency", "icon": "❗" }
  ],
  "specialties": [
    {
      "category": "General Medicine",
      "specialties": [
        { "code": "GEN001", "name": "General Physician" },
        { "code": "GEN002", "name": "Family Medicine" },
        { "code": "GEN003", "name": "Internal Medicine" },
        { "code": "GEN004", "name": "Geriatric Medicine" }
      ]
    },
    {
      "category": "Women's Health",
      "specialties": [
        { "code": "GYN001", "name": "Obstetrics & Gynaecology" },
        { "code": "GYN002", "name": "Infertility Specialist" },
        { "code": "GYN003", "name": "Reproductive Medicine" },
        { "code": "GYN004", "name": "Maternal-Fetal Medicine" },
        { "code": "GYN005", "name": "Menopause Specialist" }
      ]
    },
    {
      "category": "Child Health",
      "specialties": [
        { "code": "PED001", "name": "Pediatrics" },
        { "code": "PED002", "name": "Neonatology" },
        { "code": "PED003", "name": "Pediatric Cardiology" },
        { "code": "PED004", "name": "Pediatric Neurology" },
        { "code": "PED005", "name": "Pediatric Surgery" },
        { "code": "PED006", "name": "Pediatric Orthopedics" }
      ]
    },
    {
      "category": "Skin & Hair",
      "specialties": [
        { "code": "DER001", "name": "Dermatology" },
        { "code": "DER002", "name": "Cosmetology" },
        { "code": "DER003", "name": "Trichology (Hair Specialist)" },
        { "code": "DER004", "name": "Venereology" },
        { "code": "DER005", "name": "Aesthetic Medicine" }
      ]
    },
    {
      "category": "Mental Health",
      "specialties": [
        { "code": "PSY001", "name": "Psychiatry" },
        { "code": "PSY002", "name": "Clinical Psychology" },
        { "code": "PSY003", "name": "Neuropsychiatry" },
        { "code": "PSY004", "name": "Addiction Psychiatry" },
        { "code": "PSY005", "name": "Counselling Psychology" }
      ]
    },
    {
      "category": "Heart & Vascular",
      "specialties": [
        { "code": "CAR001", "name": "Cardiology" },
        { "code": "CAR002", "name": "Interventional Cardiology" },
        { "code": "CAR003", "name": "Electrophysiology" },
        { "code": "CAR004", "name": "Cardiothoracic Surgery" },
        { "code": "CAR005", "name": "Vascular Surgery" }
      ]
    },
    {
      "category": "Brain & Nervous System",
      "specialties": [
        { "code": "NEU001", "name": "Neurology" },
        { "code": "NEU002", "name": "Neurosurgery" },
        { "code": "NEU003", "name": "Stroke Specialist" },
        { "code": "NEU004", "name": "Epileptology" },
        { "code": "NEU005", "name": "Neuro Rehabilitation" }
      ]
    },
    {
      "category": "Bone & Joint",
      "specialties": [
        { "code": "ORT001", "name": "Orthopedics" },
        { "code": "ORT002", "name": "Joint Replacement" },
        { "code": "ORT003", "name": "Sports Medicine" },
        { "code": "ORT004", "name": "Spine Surgery" },
        { "code": "ORT005", "name": "Hand Surgery" },
        { "code": "ORT006", "name": "Trauma Specialist" }
      ]
    },
    {
      "category": "ENT",
      "specialties": [
        { "code": "ENT001", "name": "ENT Specialist" },
        { "code": "ENT002", "name": "Otology" },
        { "code": "ENT003", "name": "Rhinology" },
        { "code": "ENT004", "name": "Laryngology" },
        { "code": "ENT005", "name": "Head & Neck Surgery" }
      ]
    },
    {
      "category": "Eye Care",
      "specialties": [
        { "code": "OPH001", "name": "Ophthalmology" },
        { "code": "OPH002", "name": "Retina Specialist" },
        { "code": "OPH003", "name": "Cornea Specialist" },
        { "code": "OPH004", "name": "Glaucoma Specialist" },
        { "code": "OPH005", "name": "Pediatric Ophthalmology" },
        { "code": "OPH006", "name": "Oculoplasty" }
      ]
    },
    {
      "category": "Dental Care",
      "specialties": [
        { "code": "DEN001", "name": "General Dentistry" },
        { "code": "DEN002", "name": "Orthodontics" },
        { "code": "DEN003", "name": "Prosthodontics" },
        { "code": "DEN004", "name": "Endodontics" },
        { "code": "DEN005", "name": "Periodontics" },
        { "code": "DEN006", "name": "Oral Surgery" },
        { "code": "DEN007", "name": "Pedodontics" },
        { "code": "DEN008", "name": "Implantology" }
      ]
    },
    {
      "category": "Digestive System",
      "specialties": [
        { "code": "GAS001", "name": "Gastroenterology" },
        { "code": "GAS002", "name": "Hepatology" },
        { "code": "GAS003", "name": "GI Surgery" },
        { "code": "GAS004", "name": "Colorectal Surgery" }
      ]
    },
    {
      "category": "Kidney & Urinary",
      "specialties": [
        { "code": "NEP001", "name": "Nephrology" },
        { "code": "NEP002", "name": "Urology" },
        { "code": "NEP003", "name": "Renal Transplant Specialist" },
        { "code": "NEP004", "name": "Uro-Oncology" }
      ]
    },
    {
      "category": "Lung & Respiratory",
      "specialties": [
        { "code": "PUL001", "name": "Pulmonology" },
        { "code": "PUL002", "name": "Sleep Medicine" },
        { "code": "PUL003", "name": "Critical Care Medicine" },
        { "code": "PUL004", "name": "Respiratory Medicine" }
      ]
    },
    {
      "category": "Endocrine & Diabetes",
      "specialties": [
        { "code": "END001", "name": "Endocrinology" },
        { "code": "END002", "name": "Diabetology" },
        { "code": "END003", "name": "Thyroid Specialist" },
        { "code": "END004", "name": "Metabolic Medicine" }
      ]
    },
    {
      "category": "Cancer Care",
      "specialties": [
        { "code": "ONC001", "name": "Medical Oncology" },
        { "code": "ONC002", "name": "Surgical Oncology" },
        { "code": "ONC003", "name": "Radiation Oncology" },
        { "code": "ONC004", "name": "Pediatric Oncology" },
        { "code": "ONC005", "name": "Hemato-Oncology" }
      ]
    },
    {
      "category": "Surgery",
      "specialties": [
        { "code": "SUR001", "name": "General Surgery" },
        { "code": "SUR002", "name": "Laparoscopic Surgery" },
        { "code": "SUR003", "name": "Bariatric Surgery" },
        { "code": "SUR004", "name": "Trauma Surgery" },
        { "code": "SUR005", "name": "Robotic Surgery" }
      ]
    },
    {
      "category": "Emergency & Critical Care",
      "specialties": [
        { "code": "EMC001", "name": "Emergency Medicine" },
        { "code": "EMC002", "name": "Critical Care" },
        { "code": "EMC003", "name": "Trauma Medicine" },
        { "code": "EMC004", "name": "Disaster Medicine" }
      ]
    },
    {
      "category": "Physical Medicine",
      "specialties": [
        { "code": "PMR001", "name": "Physiotherapy" },
        { "code": "PMR002", "name": "Physical Medicine & Rehabilitation" },
        { "code": "PMR003", "name": "Sports Rehabilitation" },
        { "code": "PMR004", "name": "Occupational Therapy" }
      ]
    },
    {
      "category": "Nutrition",
      "specialties": [
        { "code": "NUT001", "name": "Dietitian" },
        { "code": "NUT002", "name": "Nutritionist" },
        { "code": "NUT003", "name": "Sports Nutritionist" },
        { "code": "NUT004", "name": "Clinical Nutritionist" }
      ]
    },
    {
      "category": "Blood Disorders",
      "specialties": [
        { "code": "HEM001", "name": "Hematology" },
        { "code": "HEM002", "name": "Transfusion Medicine" },
        { "code": "HEM003", "name": "Hemato-Oncology" }
      ]
    },
    {
      "category": "Infectious Diseases",
      "specialties": [
        { "code": "INF001", "name": "Infectious Disease Specialist" },
        { "code": "INF002", "name": "HIV Medicine" },
        { "code": "INF003", "name": "Tropical Medicine" }
      ]
    },
    {
      "category": "Rheumatology & Autoimmune",
      "specialties": [
        { "code": "RHE001", "name": "Rheumatology" },
        { "code": "RHE002", "name": "Clinical Immunology" }
      ]
    },
    {
      "category": "Anaesthesia",
      "specialties": [
        { "code": "ANA001", "name": "Anaesthesiology" },
        { "code": "ANA002", "name": "Pain Medicine" },
        { "code": "ANA003", "name": "Critical Care Anaesthesia" }
      ]
    },
    {
      "category": "Radiology & Imaging",
      "specialties": [
        { "code": "RAD001", "name": "Radiology" },
        { "code": "RAD002", "name": "Interventional Radiology" },
        { "code": "RAD003", "name": "Nuclear Medicine" }
      ]
    },
    {
      "category": "Pathology & Laboratory",
      "specialties": [
        { "code": "PAT001", "name": "Pathology" },
        { "code": "PAT002", "name": "Microbiology" },
        { "code": "PAT003", "name": "Biochemistry" },
        { "code": "PAT004", "name": "Laboratory Medicine" }
      ]
    },
    {
      "category": "Alternative Medicine",
      "specialties": [
        { "code": "ALT001", "name": "Ayurveda" },
        { "code": "ALT002", "name": "Siddha" },
        { "code": "ALT003", "name": "Homeopathy" },
        { "code": "ALT004", "name": "Naturopathy" },
        { "code": "ALT005", "name": "Unani" },
        { "code": "ALT006", "name": "Yoga Therapy" }
      ]
    },
    {
      "category": "Sexual Health",
      "specialties": [
        { "code": "SEX001", "name": "Sexology" },
        { "code": "SEX002", "name": "Andrology" },
        { "code": "SEX003", "name": "Reproductive Health" }
      ]
    },
    {
      "category": "Genetics",
      "specialties": [
        { "code": "GENE001", "name": "Medical Genetics" },
        { "code": "GENE002", "name": "Genetic Counselling" }
      ]
    },
    {
      "category": "Transplant Medicine",
      "specialties": [
        { "code": "TRN001", "name": "Organ Transplant Specialist" },
        { "code": "TRN002", "name": "Liver Transplant" },
        { "code": "TRN003", "name": "Kidney Transplant" },
        { "code": "TRN004", "name": "Heart Transplant" }
      ]
    }
  ],
  "suggested": {
    "qualifications": ["MBBS", "MD", "MS", "DNB", "DM", "MCh", "Diploma (PG)", "FRCS", "MRCP", "BDS", "MDS", "BSMS (Siddha)", "BAMS (Ayurveda)", "BHMS (Homeopathy)", "BUMS (Unani)", "BNYS"],
    "empanelledInsuranceCompanies": ["CMCHIS (Chief Minister's Comprehensive Health Insurance Scheme)", "PM-JAY (Ayushman Bharat)", "New India Assurance", "United India Insurance", "Oriental Insurance", "National Insurance", "Star Health", "Niva Bupa", "Care Health", "HDFC ERGO", "ICICI Lombard", "Tata AIG", "Aditya Birla Health", "ManipalCigna", "SBI General"],
    "scansAvailableOther": ["PET-CT", "Mammography", "DEXA", "Echo", "ECG", "TMT", "EEG", "Doppler"],
    "labFacilities": ["Haematology", "Biochemistry", "Microbiology", "Pathology", "Histopathology", "Serology", "Clinical Pathology", "Blood Bank"],
    "udhsStaffDesignation": ["Medical Officer", "Duty Doctor", "Staff Nurse", "Nursing Superintendent", "Hospital Administrator", "Front Office", "Ambulance Coordinator"]
  }
}
```

---

## 5. Errors the forms should handle

| Status | When | Example message |
|---|---|---|
| `400` | Enum value not in the list (wrong case counts) | Request body is missing or malformed |
| `400` | Specialty not in the master list | `specialties: unknown specialty [Heart Doctor]. Choose from GET /api/v1/hospital/specialties …` |
| `400` | Department not in the master list | `facilities.specialtyDepartments: unknown specialty [Heart Ward] …` |
| `400` | Emergency condition not one of the 16 | `emergencyConditionsHandled: unknown emergency condition [Cardiac Arrest]. Choose from GET /api/v1/hospital/emergency-conditions …` |
| `400` | Patient's `emergencyType` not one of the 16 | `emergencyType: unknown emergency condition 'Cardiac Arrest' …` |
| `409` | Emergency booking: no doctor handling that condition is on duty at the chosen hospital | `No doctor on duty at hospital … right now - choose another hospital` (refresh SOS, let the patient pick again) |
