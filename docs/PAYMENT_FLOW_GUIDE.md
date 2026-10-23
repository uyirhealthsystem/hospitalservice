# Payment Flow Guide: Hospital Service ↔ Payment Service ↔ Razorpay

> **Who this is for:** someone new to payments and microservices.
> **What you'll learn:** how a hospital buys extra **Doctor Seats**, which API does what, how
> `hospitalservice` talks to `payment-service`, how `payment-service` talks to **Razorpay**, and the
> technologies that make it safe.
>
> Everything here is taken from the real code. File paths are given so you can open and read along.

---

## Table of Contents

1. [The Big Picture (a shop analogy)](#1-the-big-picture-a-shop-analogy)
2. [The Three Players](#2-the-three-players)
3. [Technologies You Need to Know](#3-technologies-you-need-to-know)
4. [Every API, and What It Does](#4-every-api-and-what-it-does)
5. [Scenario 1: The Happy Path (step by step, with real JSON)](#5-scenario-1-the-happy-path)
6. [Scenario 2: The Hospital Closes the Payment Page](#6-scenario-2-the-hospital-closes-the-payment-page)
7. [Scenario 3: Double-Click / Network Retry (Idempotency)](#7-scenario-3-double-click--network-retry-idempotency)
8. [Scenario 4: Razorpay's Webhook Gets Lost (Reconciliation)](#8-scenario-4-razorpays-webhook-gets-lost-reconciliation)
9. [Scenario 5: Hospital Service Is Down When Payment Completes (Outbox + Retries)](#9-scenario-5-hospital-service-is-down-when-payment-completes)
10. [Scenario 6: A Hacker Sends a Fake "PAID" Message (HMAC Signatures)](#10-scenario-6-a-hacker-sends-a-fake-paid-message)
11. [Scenario 7: Someone Replays an Old Message](#11-scenario-7-someone-replays-an-old-message)
12. [Scenario 8: Paid Less Than the Price (Amount Checks)](#12-scenario-8-paid-less-than-the-price)
13. [Scenario 9: The User Pays Twice](#13-scenario-9-the-user-pays-twice)
14. [Scenario 10: Admin Refunds a Payment](#14-scenario-10-admin-refunds-a-payment)
15. [Scenario 11: Testing Locally Without Real Money (Mock Mode)](#15-scenario-11-testing-locally-without-real-money)
16. [Status Lifecycles (State Machines)](#16-status-lifecycles-state-machines)
17. [Configuration and Secrets](#17-configuration-and-secrets)
18. [Try It Yourself](#18-try-it-yourself)
19. [Where to Read the Code](#19-where-to-read-the-code)
20. [Glossary](#20-glossary)

---

## 1. The Big Picture (a shop analogy)

Imagine a **hospital** wants to list more doctors on the Uyir app. Each hospital gets **1 free seat**.
Each extra seat costs **₹10,000, paid once**. Only a doctor sitting in a seat shows up for
appointment booking and Emergency SOS.

Think of it like a shop:

| Real world | In our system |
|---|---|
| The **shop counter** that knows what you're buying | `hospitalservice` (owns seats and purchases) |
| The **cashier** who handles all money for every counter in the mall | `payment-service` (owns payments) |
| The **bank card machine** | **Razorpay** (actually moves the money) |
| The **receipt** the cashier sends back to the counter | the **webhook callback** |

The key idea: **`hospitalservice` never touches money or card details.** It only says *"this
hospital owes ₹X for purchase Y"* and later hears back *"purchase Y is PAID"*.

```
 Hospital admin (browser/app)
        │  1. "I want 2 seats"
        ▼
 ┌────────────────────┐   2. "Hospital H owes ₹20,000      ┌───────────────────┐   3. "Create a payment   ┌──────────┐
 │  hospitalservice   │ ─────for purchase P" ────────────► │  payment-service  │ ─────link for ₹20,000"─►│ Razorpay │
 │  (MongoDB)         │ ◄──── checkoutUrl ──────────────── │  (PostgreSQL)     │ ◄──── short_url ─────── │          │
 └────────────────────┘                                    └───────────────────┘                         └──────────┘
        │  4. checkoutUrl returned to the hospital admin
        ▼
 Hospital admin opens the Razorpay page and pays with UPI / card / netbanking  ───────────────────────────────► Razorpay
                                                                                                                │
 ┌────────────────────┐   6. signed callback "P is PAID"   ┌───────────────────┐   5. signed webhook          │
 │  hospitalservice   │ ◄───────────────────────────────── │  payment-service  │ ◄── "payment_link.paid" ─────┘
 │  creates 2 seats ✅ │                                    │  marks PAID       │
 └────────────────────┘                                    └───────────────────┘
```

---

## 2. The Three Players

### 2.1 `hospitalservice` (this repo)

- **Language/stack:** Java, Spring Boot 4, **MongoDB**
- **Owns:** hospitals, doctors, appointments, **seats** (`hospital_seats` collection) and
  **seat purchases** (`seat_purchases` collection)
- **Payment job:** decide the **price**, ask payment-service for a checkout link, and create seats
  when it hears **PAID**
- **Port:** 8078 (`server.port`), every endpoint under `/api/v1/hospital`
c
### 2.2 `payment-service` (`B:\uyir\payment-service`)

- **Language/stack:** Java, Spring Boot 4.1, **PostgreSQL** (JPA + Flyway)
- **Shared by every module:** ambulance, doctor appointment, lab scan, home care, hospital seats.
  Each module is identified by a **`referenceType`**: `AMBULANCE_BOOKING`, `DOCTOR_APPOINTMENT`,
  `LAB_SCAN_BOOKING`, `HOME_CARE_BOOKING`, **`HOSPITAL_SEAT`**
- **Owns:** payments, checkout sessions (one per payment link), refunds, and a queue of callbacks
  to send to modules
- **Port:** 8083, endpoints under `/api/payments`

### 2.3 Razorpay (external, `https://api.razorpay.com/v1`)

- An Indian **payment gateway**. It shows the payment page, takes UPI/card/netbanking, and moves
  the money into the Uyir bank account.
- We use its **Payment Links** product: Razorpay hosts the whole payment page, so our apps never
  see card numbers. (This keeps us out of most PCI-DSS card-security rules.)

### Why split it into three?

| Reason | Explanation |
|---|---|
| **Single responsibility** | Seat rules live in hospitalservice. Money rules live in payment-service. |
| **Reuse** | Ambulance, lab scan and others all use the same payment-service. Razorpay code is written once. |
| **Swap providers** | To move from Razorpay to Stripe later, only `payment-service/gateway/` changes. hospitalservice never knows. |
| **Security** | Only payment-service holds the Razorpay API keys. |

---

## 3. Technologies You Need to Know

Read this section slowly. Every scenario below uses these ideas.

### 3.1 REST API + JSON

Services talk over **HTTP**. A **REST API** is a URL plus a method (`GET` to read, `POST` to
create, `PUT` to replace, `DELETE` to remove). The data is **JSON**:

```json
{ "quantity": 2 }
```

### 3.2 Spring Boot + `RestClient`

- **Spring Boot** is the Java framework both services use. `@RestController` turns a class into
  API endpoints. `@Service` holds business logic. `@Component` is any managed object.
- **`RestClient`** is Spring's HTTP client, which is how one service calls another. In
  `HttpPaymentClient.java`:

  ```java
  http.post()
      .uri("/api/payments/intents")
      .header("Authorization", "Bearer " + bearerToken)
      .header("Idempotency-Key", "hospital-seat-purchase-" + purchase.getId())
      .body(body)
      .retrieve()
      .body(Intent.class);
  ```

- **Timeouts** (`connect-timeout=3s`, `read-timeout=10s`) make sure that if payment-service hangs,
  hospitalservice doesn't hang forever too.

### 3.3 JWT (JSON Web Token) and Bearer tokens

When a user logs in, **AuthService** gives them a **JWT**. It's a signed string that says
*"this is user X with role Y"*. The user's app sends it on every request:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

- hospitalservice **passes the hospital user's JWT along** to payment-service.
- payment-service **verifies it** (with AuthService's public key, the JWKS) and checks that
  `payerId == token subject`. That way nobody can create a payment pretending to be someone else.

### 3.4 Idempotency and the `Idempotency-Key`

**Idempotent** means *"doing it twice has the same effect as doing it once."*

Networks are unreliable. If hospitalservice sends "create payment" and the response gets lost, it
retries. Without protection, that would create **two payments**. So every create request carries
a key:

```
Idempotency-Key: hospital-seat-purchase-6710a1f2c3...
```

payment-service stores the key with a **UNIQUE** database constraint. Same key means it returns
the **same payment** instead of a new one. (See [Scenario 3](#7-scenario-3-double-click--network-retry-idempotency).)

### 3.5 Webhooks (server-to-server callbacks)

Normally **you** call an API. A **webhook** is the reverse: **the other side calls you** when
something happens.

- Razorpay → payment-service: *"payment link plink_X was paid"*
- payment-service → hospitalservice: *"payment for purchase P is PAID"*

Why not just keep asking *"is it paid yet?"* (polling)? Because the payment might take
5 seconds or 15 minutes. A webhook gives you the answer the moment it happens.

### 3.6 HMAC-SHA256 signatures

A webhook URL is **public**, so anyone could POST `{"status":"PAID"}` to it. How do we know the
message really came from payment-service?

Both sides share a **secret** (a long random string) that is **never sent over the network**. The
sender computes:

```
signature = hex( HMAC-SHA256( secret, timestamp + "." + rawBody ) )
```

and sends it in a header. The receiver computes the same thing with **its** copy of the secret and
compares. If even one byte of the body changed, or the sender doesn't know the secret, the
signatures won't match and the request is rejected.

- **HMAC** = Hash-based Message Authentication Code
- **SHA-256** = the hash function inside it
- The comparison uses `MessageDigest.isEqual(...)`, a **constant-time** compare, so an attacker
  can't guess the signature byte by byte by measuring response times.

There are **two different signature schemes** in this system:

| Link | Header(s) | What's signed | Secret |
|---|---|---|---|
| Razorpay → payment-service | `X-Razorpay-Signature` | `rawBody` | `RAZORPAY_WEBHOOK_SECRET` |
| payment-service → hospitalservice | `X-Payment-Timestamp`, `X-Payment-Signature` | `timestamp + "." + rawBody` | `HOSPITAL_SEAT_PAYMENT_WEBHOOK_SECRET` (payment side) = `PAYMENT_WEBHOOK_SECRET` (hospital side) |

### 3.7 Razorpay Payment Links

A Payment Link is a Razorpay-hosted page like `https://rzp.io/i/AbC123`. We create one with an
API call, give the URL to the user, and Razorpay handles the rest.

- **Amounts are in paise.** ₹20,000.00 is sent as `2000000`.
  (`RazorpayPaymentGateway.toPaise()` does `amount.movePointRight(2)`.)
- **Authentication** to Razorpay uses **HTTP Basic auth**:
  `Authorization: Basic base64(keyId:keySecret)`
- **Expiry:** our links live **20 minutes** (`payment.razorpay.link-expiry=20m`).
- **`notes`**: free-form data we attach. We store our own `payment_id` there, so every webhook can
  be matched back to our payment.
- A failed card attempt **does not end the link**. The user can try again on the same page. That's
  why Razorpay never reports a link as "failed", only `paid`, `expired` or `cancelled`.

### 3.8 Databases: MongoDB vs PostgreSQL

| | hospitalservice | payment-service |
|---|---|---|
| Database | **MongoDB** (documents) | **PostgreSQL** (tables) |
| Why | Flexible hospital/doctor data | Money needs strict **ACID transactions** and **row locks** |
| Schema | Java classes + `MongoIndexConfig` | **Flyway** migrations (`db/migration/*.sql`); Hibernate only *validates* |

**ACID transaction**: a group of DB changes that either *all* happen or *none* happen. That's
essential for money.

### 3.9 Database locks (`PESSIMISTIC_WRITE`, `FOR UPDATE SKIP LOCKED`)

- `PaymentRepository.lockById()` uses `SELECT ... FOR UPDATE`, which means *"I'm editing this
  payment row, everyone else wait."* That stops two webhooks from marking the same payment PAID at
  the same time.
- `WebhookDeliveryRepository.lockDue()` uses `FOR UPDATE SKIP LOCKED`, which means *"give me rows
  nobody else is working on."* That lets several payment-service instances send callbacks in
  parallel without sending the same one twice.

### 3.10 Transactional Outbox pattern

Problem: payment-service must (a) mark the payment PAID in its DB **and** (b) tell
hospitalservice. If it does (a), then crashes before (b), hospitalservice never finds out.

Solution: in the **same DB transaction** as (a), insert a row into a `webhook_delivery` table (the
**outbox**). A background job (`WebhookDispatcher`) reads the outbox and sends the callbacks.
So **the callback exists if and only if the PAID status was saved.** Nothing gets lost.

### 3.11 Background jobs (`@Scheduled`)

| Job | Runs every | Does |
|---|---|---|
| `WebhookDispatcher.dispatchDue()` | 3 s | Sends due callbacks from the outbox to modules |
| `CheckoutReconciler.reconcileStale()` | 60 s | Asks Razorpay about links open > 3 min (catches lost webhooks) |

### 3.12 Exponential backoff

If hospitalservice is down, payment-service retries the callback after **10s, 20s, 40s, 80s ...**
(doubling), capped at **1 hour**, for up to **12 attempts (about 5 hours)**. Doubling avoids
hammering a service that's already struggling.

### 3.13 Deterministic IDs for idempotent writes

When hospitalservice creates seats after a payment, the seat IDs are **predictable**:
`<purchaseId>-1`, `<purchaseId>-2`, and so on. If the same PAID callback arrives twice, inserting
`<purchaseId>-1` again hits a **duplicate key** and is ignored. So you can never get extra seats.

### 3.14 `@ConditionalOnProperty` (swappable implementations)

Both services have a **real** and a **mock** implementation, chosen by config:

| Service | Property | `real` | `mock` |
|---|---|---|---|
| hospitalservice | `hospital.seat.payment.provider` | `http` → `HttpPaymentClient` | `mock` → `MockPaymentClient` (instantly PAID) |
| payment-service | `payment.provider` | `razorpay` → `RazorpayPaymentGateway` | `mock` → `MockPaymentGateway` |

Both are coded against an **interface** (`PaymentClient`, `PaymentGateway`). This is the
**Strategy pattern**. Business code doesn't care which implementation is plugged in.

---

## 4. Every API, and What It Does

### 4.1 hospitalservice: Seat APIs (called by the hospital admin's app)

All require role **HOSPITAL**. The caller's `X-User-Id` (set by the API gateway) is its AuthService user id; the service looks up the hospital that user created (`Hospital.userId`). That same user id is sent to payment-service as `payerId`.

| Method & Path | What it does |
|---|---|
| `GET /api/v1/hospital/seats` | Lists all seats (free and paid), who sits in each, total/occupied/empty counts, and the price per seat. Creates the free seat on first call. |
| `PUT /api/v1/hospital/seats/{seatId}/doctor` | Puts a doctor in a seat (body `{"doctorId": "..."}`). The doctor must be active and associated with the hospital. **Swapping is refused** if the outgoing doctor has upcoming appointments. |
| `DELETE /api/v1/hospital/seats/{seatId}/doctor` | Empties the seat (same upcoming-appointment rule). |
| `POST /api/v1/hospital/seats/purchases` | **Starts buying seats.** Body `{"quantity": 1..50}`. Returns a `checkoutUrl` to pay at. |
| `POST /api/v1/hospital/seats/purchases/{purchaseId}/payment` | **Get a new payment link** for an unpaid purchase (for example, the 20-minute link expired). |
| `GET /api/v1/hospital/seats/purchases` | Purchase history, newest first. |

### 4.2 hospitalservice: Webhook API (called by payment-service only)

| Method & Path | What it does |
|---|---|
| `POST /api/v1/hospital/seats/payments/webhook` | Receives *"payment for purchase P is PAID / FAILED"*. **No JWT.** Authenticated by an HMAC signature. On PAID, creates the seats. Returns `{"ok": true}`. |

### 4.3 payment-service APIs

| Method & Path | Who calls it | What it does |
|---|---|---|
| `POST /api/payments/intents` | modules (hospitalservice, ambulance, ...) | Creates (or returns the existing) payment and a Razorpay checkout URL. Needs `Idempotency-Key`. |
| `GET /api/payments/{id}` | the payer or an admin | Reads one payment. |
| `GET /api/payments?referenceType=HOSPITAL_SEAT&referenceId=P` | the payer or an admin | All payments for one purchase/booking. |
| `POST /api/payments/{id}/refresh` | the app, after returning from the payment page | Asks Razorpay **right now** instead of waiting for the webhook. |
| `POST /api/payments/{id}/refunds` | **ADMIN / SUPER_ADMIN** only | Refunds all or part of a PAID payment. |
| `POST /api/payments/webhooks/razorpay` | **Razorpay** only | Receives Razorpay events. No JWT; authenticated by `X-Razorpay-Signature`. |

### 4.4 Razorpay APIs (called by payment-service only)

| Method & Path | What it does |
|---|---|
| `POST /v1/payment_links` | Create a hosted payment page. Returns `id` (`plink_...`) and `short_url`. |
| `GET /v1/payment_links/{id}` | Check a link's status (used for reconciliation). |
| `POST /v1/payments/{pay_id}/refund` | Refund a captured payment. |

**Razorpay webhook events we use:** `payment_link.paid`, `payment_link.expired`,
`payment_link.cancelled`, `refund.processed`, `refund.failed`.

---

## 5. Scenario 1: The Happy Path

> **Story:** *City Care Hospital* (`hospitalId = h-101`, created by AuthService user `u-101`) has 1 free seat, already used by Dr. Meena.
> They hired Dr. Arun and Dr. Priya and want to list both. They need **2 extra seats = ₹20,000**.

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Hospital Admin
    participant H as hospitalservice
    participant P as payment-service
    participant R as Razorpay

    Admin->>H: POST /seats/purchases {quantity: 2}  (JWT)
    H->>H: save SeatPurchase P (PENDING, ₹20,000)
    H->>P: POST /api/payments/intents  (JWT, Idempotency-Key)
    P->>P: save Payment (PENDING)
    P->>R: POST /v1/payment_links (2000000 paise)
    R-->>P: {id: plink_X, short_url}
    P->>P: save CheckoutSession (OPEN)
    P-->>H: {id, status: PENDING, checkoutUrl}
    H-->>Admin: 201 {status: PENDING, checkoutUrl}
    Admin->>R: opens checkoutUrl, pays with UPI
    R->>P: webhook payment_link.paid (X-Razorpay-Signature)
    P->>P: verify signature, mark PAID, insert outbox row (1 transaction)
    P-->>R: 200 OK
    P->>H: (≤3s later) POST /seats/payments/webhook (X-Payment-Signature)
    H->>H: verify signature, create seats P-1 & P-2, purchase PAID
    H-->>P: 200 {ok: true}
    Admin->>H: GET /seats  → 3 seats (2 empty)
    Admin->>H: PUT /seats/P-1/doctor {doctorId: Arun}
```

### Step 1: The admin asks to buy 2 seats

```http
POST /api/v1/hospital/seats/purchases
Authorization: Bearer eyJ...            (the hospital user's JWT)
X-User-Id: u-101                        (added by the API gateway)
X-User-Role: HOSPITAL
Content-Type: application/json

{ "quantity": 2 }
```

**Inside hospitalservice** (`HospitalSeatServiceImpl.startPurchase`):

1. Checks the hospital exists.
2. Computes `amount = 10000.00 × 2 = 20000.00`. **The price comes from server config**
   (`hospital.seat.price-per-seat`), never from the client, so the app can't change the price.
3. Saves a `SeatPurchase` in MongoDB with status `PENDING` and gets an id, say `6710a1f2`.

### Step 2: hospitalservice → payment-service

`HttpPaymentClient.createIntent` sends:

```http
POST http://payment-service:8083/api/payments/intents
Authorization: Bearer eyJ...                       (same JWT, forwarded)
Idempotency-Key: hospital-seat-purchase-6710a1f2
Content-Type: application/json

{
  "referenceType": "HOSPITAL_SEAT",
  "referenceId":   "6710a1f2",
  "payerId":       "u-101",
  "amount":        20000.00,
  "currency":      "INR",
  "description":   "2 doctor seat(s)"
}
```

**Inside payment-service** (`PaymentService.createIntent`) the checks happen in this order:

1. Is the `Idempotency-Key` valid (8–128 safe characters)?
2. Is `HOSPITAL_SEAT` a known consumer, with a webhook URL configured? (With real Razorpay, it
   **refuses to take money** for a module that wouldn't be told the result.)
3. Is the currency `INR`?
4. Does `payerId` equal the JWT's subject (or is the caller an admin)?
5. Is there already a payment with this key? If not, create one (`PENDING`).
6. Is there already an open checkout link with enough time left? If yes, reuse it. If not, open a
   new one.

### Step 3: payment-service → Razorpay

`RazorpayPaymentGateway.createSession` sends:

```http
POST https://api.razorpay.com/v1/payment_links
Authorization: Basic base64(rzp_live_xxx:secret)
Content-Type: application/json

{
  "amount": 2000000,
  "currency": "INR",
  "description": "2 doctor seat(s)",
  "expire_by": 1760512800,
  "notes": {
    "payment_id": "9b2e...-our-uuid",
    "reference_type": "HOSPITAL_SEAT",
    "reference_id": "6710a1f2"
  },
  "notify": { "sms": false, "email": false },
  "reminder_enable": false,
  "callback_url": "https://app.uyir.../payment-done",
  "callback_method": "get"
}
```

Razorpay replies:

```json
{ "id": "plink_PqR123", "short_url": "https://rzp.io/i/AbC123", "expire_by": 1760512800, ... }
```

payment-service saves a `CheckoutSession(providerSessionId = plink_PqR123, status = OPEN)`.

> 💡 **No DB transaction is held open while Razorpay is being called.** A slow Razorpay call
> must not block database rows for other requests.

### Step 4: The answer travels back

payment-service → hospitalservice:

```json
{ "id": "9b2e...", "status": "PENDING", "checkoutUrl": "https://rzp.io/i/AbC123", "checkoutExpiresAt": "...", ... }
```

hospitalservice stores `paymentId` and `checkoutUrl` on the purchase, then replies to the admin:

```json
HTTP 201 Created
{
  "id": "6710a1f2",
  "hospitalId": "h-101",
  "quantity": 2,
  "pricePerSeat": 10000.00,
  "amount": 20000.00,
  "currency": "INR",
  "status": "PENDING",
  "paymentId": "9b2e...",
  "checkoutUrl": "https://rzp.io/i/AbC123"
}
```

### Step 5: The admin pays on Razorpay's page

The app opens `checkoutUrl`. The admin pays ₹20,000 by UPI. Razorpay then redirects the browser to
`callback_url`. **The redirect is NOT proof of payment** (anyone can type a URL). The real proof
is the next step.

### Step 6: Razorpay → payment-service (webhook)

```http
POST /api/payments/webhooks/razorpay
X-Razorpay-Signature: 5f2a...
X-Razorpay-Event-Id: evt_789

{
  "event": "payment_link.paid",
  "payload": {
    "payment_link": { "entity": { "id": "plink_PqR123", "amount_paid": 2000000, "currency": "INR",
                                  "notes": { "payment_id": "9b2e..." } } },
    "payment":      { "entity": { "id": "pay_Zz999", "status": "captured" } }
  }
}
```

**Inside payment-service:**

1. `RazorpayWebhookController`: verify `HMAC-SHA256(RAZORPAY_WEBHOOK_SECRET, rawBody)`. Wrong
   signature → **401**.
2. `RazorpayWebhookHandler`: has event `evt_789` been processed already? If yes, do nothing.
3. Find our payment through the link id `plink_PqR123` (or `notes.payment_id` as a fallback).
4. `PaymentSettlement.paid()`, in **one transaction**:
   - lock the payment row
   - check `amount_paid (20000.00) >= amount owed (20000.00)` and the currency matches
   - set `status = PAID`, `providerPaymentId = pay_Zz999`, `paidAt = now`
   - **insert an outbox row** (`webhook_delivery`) for HOSPITAL_SEAT
   - record event `evt_789` as processed
5. Reply `200` to Razorpay.

### Step 7: payment-service → hospitalservice (callback)

Within about 3 seconds, `WebhookDispatcher` picks up the outbox row and sends:

```http
POST http://hospitalservice:8078/api/v1/hospital/seats/payments/webhook
X-Payment-Timestamp: 1760511650
X-Payment-Signature: hex(HMAC-SHA256(secret, "1760511650." + body))
Content-Type: application/json

{
  "paymentId": "9b2e...",
  "referenceType": "HOSPITAL_SEAT",
  "referenceId": "6710a1f2",
  "bookingId": "6710a1f2",
  "payerId": "u-101",
  "status": "PAID",
  "amount": 20000.00,
  "currency": "INR",
  "refundedAmount": 0,
  "failureReason": null,
  "occurredAt": "2026-10-15T06:20:50Z"
}
```

**Inside hospitalservice** (`SeatPaymentWebhookController` → `HospitalSeatServiceImpl.onPaymentEvent`):

1. Verify the timestamp is within **5 minutes** of now, and verify the signature.
2. Parse and validate the JSON.
3. `referenceType` must be `HOSPITAL_SEAT`. Find purchase `6710a1f2`.
4. `confirmPaid`:
   - `paymentId` matches the one stored on the purchase
   - currency matches, amount ≥ price
   - insert seats `6710a1f2-1` and `6710a1f2-2` (`seatType = PAID`)
   - purchase → `PAID`, `paidAt = now`, `checkoutUrl = null`
5. Reply `200 {"ok": true}`. payment-service marks the delivery `DELIVERED`.

### Step 8: The hospital uses the seats

```http
GET /api/v1/hospital/seats
→ totalSeats: 3, occupiedSeats: 1, emptySeats: 2

PUT /api/v1/hospital/seats/6710a1f2-1/doctor   { "doctorId": "doc-arun" }
PUT /api/v1/hospital/seats/6710a1f2-2/doctor   { "doctorId": "doc-priya" }
```

Dr. Arun and Dr. Priya now appear in appointment booking, Emergency SOS, and doctor search for
City Care Hospital. 🎉

---

## 6. Scenario 2: The Hospital Closes the Payment Page

> **Story:** The admin opens the link, gets a phone call, and comes back 30 minutes later. The
> 20-minute link has **expired**.

1. Razorpay sends `payment_link.expired` → payment-service marks the **CheckoutSession** `EXPIRED`.
   The **Payment stays `PENDING`**: the money is still owed. Nothing is sent to hospitalservice
   (`PaymentSettlement.expired` doesn't notify anyone).
2. The admin calls:
   ```http
   POST /api/v1/hospital/seats/purchases/6710a1f2/payment
   ```
3. hospitalservice calls `POST /api/payments/intents` again **with the same Idempotency-Key**
   `hospital-seat-purchase-6710a1f2`.
4. payment-service finds the **same Payment**, sees no usable open link, and creates a **new
   Razorpay link** (`plink_NEW`). That's a second `CheckoutSession` for the same Payment.
5. The admin gets a fresh `checkoutUrl` and pays. From here it's the happy path.

**Why a retry endpoint instead of a new purchase?** One purchase means one payment, however many
links it takes. That avoids leftover half-finished purchases and accidental double payments.

---

## 7. Scenario 3: Double-Click / Network Retry (Idempotency)

### 7a. Network blip between hospitalservice and payment-service

`HttpPaymentClient` tries **twice** on a network error (`ResourceAccessException`):

```
attempt 1 → request reaches payment-service, payment created, but the response is lost
attempt 2 → same Idempotency-Key → payment-service returns the SAME payment
```

Result: **one payment**, not two. If both attempts fail, the admin gets **503 "Payment service
unavailable, please retry"**, and the purchase stays `PENDING` so it can be retried with
`/purchases/{id}/payment`.

### 7b. Two requests at the exact same moment

Both reach payment-service and both try `INSERT payment (idempotency_key=...)`. The **UNIQUE
constraint** lets only one win. The loser catches `DataIntegrityViolationException` and **reads the
winner's payment** (`PaymentService.findOrCreate`).

### 7c. Same key, different amount

If someone reuses a key with a different amount, payee or reference, payment-service returns
**409 Conflict: "Idempotency-Key was already used for a different payment"**. A key is tied to
exactly one request.

---

## 8. Scenario 4: Razorpay's Webhook Gets Lost (Reconciliation)

> **Story:** The admin paid, but Razorpay's webhook never arrived (a network glitch, or our
> server was restarting).

Two safety nets catch this:

**Net 1: on demand.** When the user comes back from the payment page, the app can call
`POST /api/payments/{id}/refresh`. payment-service then asks Razorpay
`GET /v1/payment_links/plink_PqR123` and, if a payment there is `captured`, marks it PAID.

**Net 2: automatic.** Every **60 seconds**, `CheckoutReconciler` finds checkouts that have been
`OPEN` for more than **3 minutes** and asks Razorpay about each one:

| Razorpay says | payment-service does |
|---|---|
| a payment is `captured` | `settlement.paid(...)` → PAID → callback to hospitalservice |
| link `expired` / `cancelled` | mark the checkout EXPIRED |
| still open | wait for the next round |
| open for more than **24 hours** | give up and mark it EXPIRED |

If the late webhook **also** arrives afterwards, it's harmless. `paid()` sees the payment is
already PAID and does nothing. That's idempotency again.

---

## 9. Scenario 5: Hospital Service Is Down When Payment Completes

> **Story:** The admin pays at 10:00. hospitalservice is being redeployed from 10:00 to 10:03.

1. payment-service has already saved `PAID` **and** the outbox row together (transactional outbox).
2. `WebhookDispatcher` tries the callback → connection refused → retry at **+10 s**.
3. Fails again → **+20 s**, then **+40 s**, then **+80 s** ...
4. At 10:03 hospitalservice is back. The next attempt gets `200` → the seats are created.

Retry schedule: `10s, 20s, 40s, 80s, 160s, ...` capped at `1h`, **12 attempts in total**. After
that the delivery is marked `DEAD` and an error is logged. The payment is still PAID in
payment-service and can be read with `GET /api/payments/{id}`, so an operator can fix it by hand.

**What if hospitalservice returns an error (4xx/5xx)?** The same thing: any non-2xx is retried.
That's why hospitalservice's handler **must be idempotent**. It may get the same PAID callback
more than once, and the deterministic seat ids make that safe.

---

## 10. Scenario 6: A Hacker Sends a Fake "PAID" Message

> **Story:** An attacker finds the URL `/api/v1/hospital/seats/payments/webhook` and POSTs
> `{"status":"PAID", "referenceId":"6710a1f2", ...}` hoping to get free seats.

```
X-Payment-Signature: (missing, or made up)
```

`WebhookSignature.isValid()` computes the expected signature with the shared secret. The attacker
doesn't know the secret, so the signatures don't match → **rejected** (`WebhookRejectedException`).
No seats are created.

The same protection exists one level up: a fake call to `/api/payments/webhooks/razorpay` fails
because the attacker doesn't know `RAZORPAY_WEBHOOK_SECRET`.

> 🔐 **Rule:** never trust a webhook because of *where it says it came from*. Trust only the
> signature.

And if `PAYMENT_WEBHOOK_SECRET` isn't configured on hospitalservice at all, the webhook refuses
**every** request ("Seat payment webhook is not configured") instead of accepting unsigned ones.
That's called **failing closed**.

---

## 11. Scenario 7: Someone Replays an Old Message

> **Story:** An attacker records a *real*, correctly signed callback and sends it again a week later.

- **hospitalservice:** the signature covers `X-Payment-Timestamp`. A timestamp older than
  **5 minutes** (`hospital.seat.payment.webhook-tolerance=5m`) is rejected. Changing the timestamp
  breaks the signature. A replay **inside** the 5-minute window is harmless, because confirming a
  purchase is idempotent (the seats already exist).
- **payment-service:** Razorpay doesn't sign a timestamp, so payment-service remembers every
  `X-Razorpay-Event-Id` in the `provider_event` table. A second delivery of `evt_789` is a no-op.

---

## 12. Scenario 8: Paid Less Than the Price

Amount checks happen in **two places** (defense in depth):

1. **payment-service** (`PaymentSettlement.paid`): if Razorpay reports `amount_paid` less than the
   amount owed, or a different currency, it logs an `amount_mismatch` anomaly and **does NOT mark
   it PAID**.
2. **hospitalservice** (`confirmPaid`): if the callback's amount is less than the purchase amount
   → **400 error**, no seats. Since that isn't a 2xx, payment-service will retry and eventually
   mark the delivery `DEAD`, which flags it for a human to look at.

Why check twice? The `amount` in the intent was sent by the module. payment-service's own comment
in `PaymentEvent.java` says: *"Modules must check that amount covers what they charge."* Each
service protects its own rules.

---

## 13. Scenario 9: The User Pays Twice

> **Story:** The admin opened link #1, then requested link #2 (Scenario 2), and somehow paid on
> **both**.

The second `payment_link.paid` reaches `PaymentSettlement.paid()`. The payment is already PAID
with a **different** `pay_...` id, so payment-service logs:

```
DOUBLE PAYMENT: payment 9b2e... is already paid by pay_Zz999, and pay_Yy888 was paid too.
Refund one via POST /api/payments/9b2e.../refunds ...
```

It also records a `double_payment` metric. hospitalservice is **not** told again, so no extra seats
are created. An admin refunds the extra money (Scenario 10).

---

## 14. Scenario 10: Admin Refunds a Payment

```http
POST /api/payments/9b2e.../refunds          (ADMIN or SUPER_ADMIN JWT)
{ "amount": 20000.00, "reason": "...", "note": "Duplicate payment" }
```

`RefundService` works in **two steps**, so two admins clicking at once can't refund more than was
paid:

1. **Reserve** (inside a transaction, payment row locked): check the payment is PAID and that
   `amount ≤ paid − already refunded or pending`. Save the Refund as `PENDING`.
2. **Call Razorpay** (outside the transaction):
   `POST /v1/payments/pay_Zz999/refund` with `receipt = refundId`. Razorpay refuses a duplicate
   receipt, so a retried refund is never paid out twice.
3. Razorpay usually answers `pending`. Later the `refund.processed` (or `refund.failed`) webhook
   sets the Refund to `SUCCEEDED` (or `FAILED`) and updates `refundedAmount`.

⚠️ **Important for hospitalservice:** refunds are **not** sent to modules as callbacks. Refunding
a seat payment does **not** remove the seats automatically. If that's ever needed, it has to be
built.

---

## 15. Scenario 11: Testing Locally Without Real Money

**Option A: mock in hospitalservice only** (no payment-service needed):

```properties
# application.properties or env
SEAT_PAYMENT_PROVIDER=mock
```

`MockPaymentClient` returns `status = PAID` straight away, so `POST /seats/purchases` creates the
seats immediately. ⚠️ **Never enable this in production.** It logs a loud warning on startup.

**Option B: the real chain with payment-service in mock mode:**

```properties
# payment-service
PAYMENT_PROVIDER=mock
```

hospitalservice → payment-service runs for real, but `MockPaymentGateway` marks the payment paid
instantly, so you can test the HTTP calls and signed callbacks without Razorpay.

**Option C: the real chain with Razorpay Test Mode:** use `rzp_test_...` keys from the Razorpay
dashboard. You get real payment pages with test cards/UPI and no real money. To receive Razorpay
webhooks on your laptop, expose payment-service with a tunnel (for example ngrok) and register
`https://<tunnel>/api/payments/webhooks/razorpay` in **Razorpay Dashboard → Settings → Webhooks**.

---

## 16. Status Lifecycles (State Machines)

### SeatPurchase (hospitalservice, MongoDB)

```
            createIntent ok
 (new) ──────────────────────► PENDING ──── PAID callback ────► PAID  (final, seats created)
                                 │  ▲
                FAILED callback  │  │  retryPayment (new link)
                                 ▼  │
                               FAILED
```

### Payment (payment-service, PostgreSQL)

```
 PENDING ──paid──► PAID          (final; refunds tracked separately in refundedAmount)
 PENDING ──failed──► FAILED ──new checkout──► PENDING
```

> With Razorpay Payment Links, **FAILED never actually happens**: a failed attempt leaves the link
> open. The FAILED path exists so other providers can use it.

### CheckoutSession (one per Razorpay link)

```
 OPEN ──► SUCCEEDED   (final)
 OPEN ──► EXPIRED     (can still become SUCCEEDED if a late "paid" arrives)
```

### Webhook delivery (outbox row)

```
 PENDING ──2xx──► DELIVERED
 PENDING ──error──► PENDING (retry with backoff) ... ──12th failure──► DEAD
```

### Seat (hospitalservice)

```
 FREE seat: id "<hospitalId>-free", created automatically, 1 per hospital
 PAID seat: id "<purchaseId>-<n>", created only by a PAID callback
 Either kind: doctorId = null (empty) ⇄ doctorId = "doc-..." (occupied)
```

---

## 17. Configuration and Secrets

### hospitalservice (`src/main/resources/application.properties`)

| Property | Env var | Meaning |
|---|---|---|
| `hospital.seat.price-per-seat` | (none) | `10000.00` |
| `hospital.seat.currency` | (none) | `INR` |
| `hospital.seat.payment.provider` | `SEAT_PAYMENT_PROVIDER` | `http` (real) or `mock` |
| `hospital.seat.payment.base-url` | `PAYMENT_SERVICE_URL` | where payment-service lives (default `http://localhost:8083`) |
| `hospital.seat.payment.webhook-secret` | `PAYMENT_WEBHOOK_SECRET` | shared HMAC secret |
| `hospital.seat.payment.webhook-tolerance` | (none) | `5m` replay window |
| `hospital.seat.payment.connect-timeout` / `read-timeout` | (none) | `3s` / `10s` |

### payment-service (`B:\uyir\payment-service\src\main\resources\application.properties`)

| Property | Env var | Meaning |
|---|---|---|
| `payment.provider` | `PAYMENT_PROVIDER` | `razorpay` or `mock` |
| `payment.razorpay.key-id` / `key-secret` | `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | API keys from the Razorpay dashboard |
| `payment.razorpay.webhook-secret` | `RAZORPAY_WEBHOOK_SECRET` | set in Razorpay's webhook settings |
| `payment.razorpay.link-expiry` | (none) | `20m` |
| `payment.checkout.success-url` | `CHECKOUT_SUCCESS_URL` | where the browser lands after paying |
| `payment.consumers[HOSPITAL_SEAT].webhook-url` | `HOSPITAL_SEAT_PAYMENT_WEBHOOK_URL` | e.g. `http://hospitalservice:8078/api/v1/hospital/seats/payments/webhook` |
| `payment.consumers[HOSPITAL_SEAT].webhook-secret` | `HOSPITAL_SEAT_PAYMENT_WEBHOOK_SECRET` | **must equal** hospitalservice's `PAYMENT_WEBHOOK_SECRET` |
| `payment.security.jwks-uri` | `AUTH_JWKS_URL` | AuthService's public keys, used to verify the forwarded JWT |

### The secret pairs (the most common setup mistake)

```
hospitalservice  PAYMENT_WEBHOOK_SECRET               ═══ must be identical ═══  payment-service  HOSPITAL_SEAT_PAYMENT_WEBHOOK_SECRET
payment-service  RAZORPAY_WEBHOOK_SECRET              ═══ must be identical ═══  Razorpay Dashboard → Webhooks → Secret
```

Generate a strong secret (at least 32 random characters):

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

> 🔒 Keep secrets in environment variables or a git-ignored `.env`, **never** in committed files.

---

## 18. Try It Yourself

### 18.1 Mock mode, end to end inside hospitalservice

```powershell
$env:SEAT_PAYMENT_PROVIDER = "mock"
./mvnw spring-boot:run
```

```bash
# 1. See your seats (1 free)
curl http://localhost:8078/api/v1/hospital/seats \
  -H "X-User-Id: u-101" -H "X-User-Role: HOSPITAL"

# 2. Buy 2 seats (mock = instantly PAID)
curl -X POST http://localhost:8078/api/v1/hospital/seats/purchases \
  -H "X-User-Id: u-101" -H "X-User-Role: HOSPITAL" \
  -H "Authorization: Bearer anything" \
  -H "Content-Type: application/json" -d '{"quantity": 2}'

# 3. Now 3 seats
curl http://localhost:8078/api/v1/hospital/seats -H "X-User-Id: u-101" -H "X-User-Role: HOSPITAL"
```

### 18.2 Simulate payment-service's signed callback yourself

This is a great way to understand HMAC. Run hospitalservice with
`PAYMENT_WEBHOOK_SECRET=my-test-secret-that-is-long-enough-123` and a real `http` provider purchase
in `PENDING`, then (PowerShell):

```powershell
$secret = "my-test-secret-that-is-long-enough-123"
$ts     = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$body   = '{"paymentId":"<paymentId from purchase>","referenceType":"HOSPITAL_SEAT","referenceId":"<purchaseId>","status":"PAID","amount":20000.00,"currency":"INR"}'

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [Text.Encoding]::UTF8.GetBytes($secret)
$sig = -join ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$ts.$body")) | ForEach-Object { $_.ToString("x2") })

Invoke-RestMethod -Method Post -Uri http://localhost:8078/api/v1/hospital/seats/payments/webhook `
  -Headers @{ "X-Payment-Timestamp" = $ts; "X-Payment-Signature" = $sig } `
  -ContentType "application/json" -Body $body
```

Then try these and watch them get **rejected**:
- change one character of `$body` after signing
- use a timestamp from 10 minutes ago
- use a wrong secret

---

## 19. Where to Read the Code

**hospitalservice** (`src/main/java/com/uyir/hospital/`)

| File | Role |
|---|---|
| `controller/HospitalSeatController.java` | Seat and purchase APIs |
| `controller/SeatPaymentWebhookController.java` | Receives payment-service callbacks, checks the signature |
| `service/impl/HospitalSeatServiceImpl.java` | All seat logic: `startPurchase`, `retryPayment`, `onPaymentEvent`, `confirmPaid` |
| `payment/PaymentClient.java` | Interface: "create a payment intent" |
| `payment/HttpPaymentClient.java` | Real HTTP call to payment-service (retry plus idempotency key) |
| `payment/MockPaymentClient.java` | Local testing: instantly PAID |
| `payment/WebhookSignature.java` | HMAC verification plus timestamp window |
| `payment/PaymentWebhookEvent.java` | Callback JSON shape |
| `payment/SeatProperties.java` | `hospital.seat.*` config |
| `model/SeatPurchase.java`, `model/HospitalSeat.java` | MongoDB documents |

**payment-service** (`B:\uyir\payment-service\src\main\java\com\uyirhealthapp\payment_service\`)

| File | Role |
|---|---|
| `payment/PaymentController.java` | `/api/payments` APIs |
| `payment/PaymentService.java` | `createIntent`, idempotency, opening checkouts |
| `payment/PaymentSettlement.java` | **The only place** a payment becomes PAID/FAILED |
| `payment/CheckoutReconciler.java` | Safety net that asks Razorpay about stale links |
| `gateway/RazorpayPaymentGateway.java` | Razorpay HTTP calls (links, status, refunds) |
| `webhook/RazorpayWebhookController.java` | Receives Razorpay webhooks, checks the signature |
| `webhook/RazorpayWebhookHandler.java` | Applies an event once (dedup by event id) |
| `notify/ConsumerNotifier.java` | Writes the outbox row |
| `notify/WebhookDispatcher.java` | Sends signed callbacks with backoff |
| `refund/RefundService.java` | Two-step safe refunds |
| `common/Hmac.java` | Signing helpers |

---

## 20. Glossary

| Term | Meaning |
|---|---|
| **Payment gateway** | A company (Razorpay, Stripe) that actually collects money from cards/UPI. |
| **Payment intent** | "User X owes ₹Y for thing Z": our payment-service record, created before any money moves. |
| **Checkout session / Payment link** | One payable page with an expiry. One payment can have several. |
| **referenceType / referenceId** | Which module (`HOSPITAL_SEAT`) and which of its records (purchase id) a payment is for. |
| **payerId** | The user paying. Must match the JWT's subject. |
| **Idempotency-Key** | A unique key per logical request, so retries don't duplicate it. |
| **Webhook** | An HTTP call *to* you when something happens elsewhere. |
| **HMAC signature** | Proof a message came from someone who knows the shared secret and wasn't changed on the way. |
| **Replay attack** | Re-sending a captured valid message. Stopped by timestamps or event-id dedup. |
| **Transactional outbox** | Saving "messages to send" in the same DB transaction as the data change. |
| **Exponential backoff** | Retry waits that double each time (10s, 20s, 40s, ...). |
| **Reconciliation** | Periodically asking the provider for the truth, in case a webhook was missed. |
| **Paise** | 1/100 of a rupee. Razorpay amounts are in paise. |
| **Captured** | Razorpay's word for "the money is really taken". |
| **Fail closed** | When unsure or misconfigured, reject rather than allow. |
| **Defense in depth** | Checking the same rule in more than one layer. |
| **Strategy pattern** | One interface with swappable implementations (real vs mock). |

---

### TL;DR

1. **hospitalservice** decides *what* is bought and *how much*, then asks **payment-service** for
   a link (forwarding the hospital's JWT, with an **Idempotency-Key**).
2. **payment-service** creates a **Razorpay Payment Link** and returns its URL.
3. The hospital pays on **Razorpay's** page.
4. **Razorpay → payment-service** webhook (signed) → payment marked **PAID** and an outbox row is
   saved in the same transaction.
5. **payment-service → hospitalservice** callback (signed, timestamped, retried with backoff) →
   seats are created with **deterministic ids**, so duplicates are harmless.
6. Safety nets: **idempotency keys**, **HMAC signatures**, **replay protection**, **reconciliation**,
   **amount checks on both sides**, **row locks** and the **outbox**.
