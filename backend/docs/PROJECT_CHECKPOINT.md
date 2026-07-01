# Supply Chain Integration Hub — Project Checkpoint
_Last updated: 2026-06-19. Deadline: 2026-06-30. Covers backend (integration-hub), frontend, and SAP CPI iFlows._

---

## 0. The project in one paragraph
Two systems trade purchase orders through **SAP CPI** as the only bridge. **System 1** (the graded build — React frontend + Spring Boot backend; roles vendor/procurement/admin) is **human-operated**. **System 2** is a **robot simulator** running in the same backend, isolated by `systemId`, that injects vendor-side demand and auto-approves System 1's procurement POs so the demo shows live two-way traffic. Goal: an industry-grade, fully-functional two-company system by June 30.

---

## 1. Status at a glance

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Contracts + secrets | ✅ Done |
| 1 | Backend ↔ CPI bridge (iFlow 1 outbound, inbound PO, approval endpoints) | ✅ Done & verified |
| 1+ | Bridge hardening (secret, audit, validation, retry, idempotency, PO cap) | ✅ Done |
| 1++ | Multi-format outbound (JSON/XML/CSV) | ✅ Done |
| 2 | iFlow 2 (approval callback) via ngrok → backend | ✅ Done — full round trip |
| 3 | Two-system isolation (`systemId`, role/tenant-scoped reads, dashboards) | ✅ Done (orders + dashboards) |
| 4 | System 2 users + in-process simulator | ✅ Done |
| 4.x | Pagination (orders page + admin vendor/procurement views) | ✅ Done |
| FE | Frontend fixes (login role gate, shared-axios token, admin order mapping) | ✅ Done |
| 5 | Multi-line items, inbound multi-format, frontend env/cleanup | ⏳ Partial |
| 6 | iFlow 4 alerts, iFlow 5 ML risk, iFlow 6 EOD report | ⏳ Not started |
| 7 | Harden, test, deploy (Render), rehearse | ⏳ Not started |

---

## 2. Architecture & key conventions
- **Two lanes via CPI:** Lane A — System 2 procurement → System 1 vendor (S1 human approves). Lane B — System 1 procurement → System 2 vendor (robot approves).
- **`systemId` isolation:** every order/user carries `SystemId` (SYSTEM1/SYSTEM2). Reads are tenant-scoped from the JWT-backed `UserPrincipal` via `Tenant.of(auth)`.
- **`PoStatus` contract:** `DRAFT→SENT→RECEIVED→APPROVED|REJECTED→FULFILLED→SHIPPED` (+`FAILED`). Separate from internal `OrderStatus` (REQUESTED…DELIVERED) which the `OrderLifecycleScheduler` advances (30/60/90s).
- **correlationId:** CPI's `EnrichPO` builds `"<source>-<poNumber>"`; backend stamps the same so approval callbacks reconcile.

---

## 3. DONE — detail

### Phase 0 — Contracts & secrets
Secrets externalized to env (`MONGODB_URI`, `APP_JWT_SECRET`) + rotated; `.env` gitignored; `PoStatus` + integration fields on `Order`; canonical contract in `docs/PO_CONTRACT.md`.

### Phase 1 — CPI bridge
`CpiClient` (OAuth2 client-credentials, token cache) → iFlow 1; inbound `POST /api/cpi/inbound/po` (dedup on idempotencyKey) and `/approval` (match on correlationId); `POST /api/orders/{id}/send` outbound trigger.

### Phase 1 hardening (6, verified)
X-CPI-Secret inbound gate; `IntegrationLog` audit on every message; bean validation → 400; timeouts + retry/backoff + `FAILED` status; outbound idempotency guard; open-PO cap (429).

### Multi-format outbound
`OrderService.buildPayload` emits JSON `{"order":{…}}`, XML `<orders><order>…</order></orders>`, or CSV by `order.format`; `#meta … format=` set; verified all three return the canonical.

### Phase 2 — iFlow 2 (approval callback)
Final iFlow: Sender `/approval-callback` → ParseControlParams → ValidateApproval → SetBackendHeaders (secret from **Security Material**) → HTTP receiver to the backend via **ngrok**. Verified: simulated approval flips the order to APPROVED end to end.

### Phase 3 — Two-system isolation
`SystemId` enum, `UserPrincipal`, `Tenant` helper. Order reads (`getAllOrders/active/past/pending`), `createOrder`, `sendOrderToCpi`, inbound `receivePo`, and all three dashboards are tenant-scoped — no hardcoded `SYSTEM1` in those paths. Fixed: `getSystemId` NPE (null-safe), and the login "No accessor" (a stale build — `clean` resolved it).

### Phase 4 — System 2 users + simulator
- **Users:** `register` accepts `systemId`; created `vendor1/proc1` (SYSTEM1) and `vendor2/proc2` (SYSTEM2).
- **`System2Simulator`** (`@Scheduled`, every **10 min**): raises `PO-S2-…` onto System 1's vendor queue, and approves/occasionally-rejects System 1 procurement's POs (System 1 is human-operated, so the simulator does **not** raise procurement POs). Admin controls: `GET /api/simulator/status`, `POST /api/simulator/start|stop|fire`.
- The `OrderLifecycleScheduler` (shipping status, 30/60/90s) is intentionally **unchanged**.

### Pagination (4.x)
- Backend `GET /api/orders/paged?tab=&status=&q=&sort=&page=&size=20&direction=` — tenant/role/tab/status/search/sort done server-side via MongoTemplate; returns `{content, page, size, totalElements, totalPages}`.
- Frontend: `Orders.jsx` (vendor/procurement/admin), `AdminVendorView`, `AdminProcurementView` all load one page (20) with Prev/Next; admin stat cards use server-side totals so they stay accurate.

### Frontend fixes
Login now blocks role↔tab mismatch (Admin tab accepts ADMIN+MANAGER); 7 pages switched to the shared axios instance so the JWT rides along (killed the "failed to load" 403s); admin views map real `Order` fields (`orderId`, `items[]`, `totalAmount`); inventory endpoint opened to PROCUREMENT. Dashboard bugs fixed: null-`createdAt` alert sort + `@EnableMongoAuditing`; stale `PENDING` order to be deleted in Atlas.

---

## 4. LEFT TO BUILD

### Phase 5 — depth + frontend polish
Nested **line items** (iFlow 1 + `buildPayload` currently send the first item only); inbound multi-format normalization; **`systemId` on Shipment/Alert/Supplier/Inventory** so their dashboard counts are per-tenant (currently global); frontend `VITE_API_URL` instead of hardcoded `localhost`; delete the duplicate `frontend/frontend/` scaffold; paginate shipments/inventory tables if they grow.

### Phase 6 — value features
iFlow 4 alerts (Mail/Ethereal), iFlow 5 ML risk (Flask Random Forest + Gradient Boosting), iFlow 6 end-of-day report email, admin demo control panel (wire the simulator start/stop/fire into the UI).

### Phase 7 — harden, test, deploy
CORS lockdown (currently `*`+credentials); broaden `@RestControllerAdvice`; unit/slice tests; remove temp `CpiTestController`; package refactor; **deploy backend to Render** and repoint iFlow receivers off ngrok; externalize the iFlow receiver URL; consider mTLS for inbound; README documenting the isolation story.

---

## 5. HOW IT CAN BE IMPROVED
- Dead-letter + scheduled retry for `FAILED` outbound POs.
- Observability: correlationId in logging MDC, Actuator health/metrics, a status page.
- httpOnly cookie for the JWT (vs localStorage); optimistic locking (`@Version`) on `Order`.
- Master-data seeder (inventory/suppliers) so non-order tabs aren't empty.
- Containerize (Docker compose) + CI (GitHub Actions); keep-alive ping if on Render free (avoids spin-down).

---

## 6. HARD-WON FACTS (don't relearn)
- iFlow 1 payloads: JSON `{"order":{…}}` (no wrapper), XML `<orders><order>…</order></orders>` (wrapper), CSV header+row. `#meta source=… target=… [format=…]` first line; tenant strips headers/query.
- CPI auth = OAuth2 client-credentials (not Basic). Receiver "Request Headers" must explicitly list forwarded headers. An unresolved externalized-parameter pill → 500 with no logged message. Log level `None` hides all messages — use `Info`. CSRF off on senders.
- `.env` paste can carry NBSP (`c2 a0`) → silent `invalid_client`; load `.env` splitting on first `=` only.
- After `SecurityConfig`/properties changes, **full `clean` restart** — devtools partial reloads run stale classes/props (caused the login "No accessor" and the simulator-interval confusion).
- ngrok free URL changes per restart → update the iFlow 2 receiver address + redeploy.

---

## 7. ENVIRONMENT & RUN
```bash
# backend (env loaded)
while IFS= read -r line || [ -n "$line" ]; do case "$line" in ''|\#*) continue;; esac; export "${line%%=*}=${line#*=}"; done < .env
./mvnw clean spring-boot:run         # use clean after config changes
# ngrok (for iFlow 2 callback)
ngrok http 8080
```
Demo users: admin `debarchana.sen28@gmail.com`; `vendor1@sys1.com` / `proc1@sys1.com` (SYSTEM1); `vendor2@sys2.com` / `proc2@sys2.com` (SYSTEM2) — all `Pass@123` except admin.
Key config: `simulator.interval-ms=600000` (10 min), `lifecycle.*`=30/60/90s, `cpi.max-open-pos=50`, `simulator.max-open-pos=20`.

---

## 8. >>> NEXT ACTION <<<
1. `clean` restart so the simulator runs at the 10-min interval; confirm dashboards/orders paginate (20/page).
2. Then **Phase 5** — start with `systemId` on the remaining collections (so dashboard counts are truly per-tenant) and nested line items, then frontend `VITE_API_URL` + remove the duplicate `frontend/frontend/`.
