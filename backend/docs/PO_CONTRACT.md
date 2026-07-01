# Purchase-Order Integration Contract (v1)

This is the **single source of truth** for purchase orders crossing the SAP CPI bridge.
Both System 1 and System 2 obey it. CPI's canonical schema = this canonical JSON.
Lock this before building endpoints — everything downstream depends on it.

## 1. The `#meta` transport line
Every body POSTed to CPI starts with a control line (the trial tenant strips headers/query):
```
#meta source=system1 target=system2 correlationId=PO-20260618-0001 idempotencyKey=PO-20260618-0001 format=json
<payload in json | xml | csv>
```
- `source` / `target`: `system1` | `system2`
- `correlationId`: immutable, survives all transforms; the callback MUST echo it
- `idempotencyKey`: write-once dedup key (often == correlationId for a PO; new value per message)
- `format`: `json` | `xml` | `csv`

## 2. Canonical PO (the internal representation inside CPI)
```json
{
  "correlationId": "PO-20260618-0001",
  "poNumber": "PO-20260618-0001",
  "originSystem": "system1",
  "sourceSystem": "system1",
  "targetSystem": "system2",
  "direction": "OUTBOUND",
  "counterpartyId": "S2-VENDOR-01",
  "counterpartyName": "Acme Components Ltd",
  "format": "json",
  "status": "SENT",
  "riskScore": 12.5,
  "currency": "INR",
  "totalAmount": 145000.00,
  "expectedDeliveryDate": "2026-07-05",
  "lineItems": [
    { "sku": "SKU-100", "description": "M8 bolt", "quantity": 500, "unitPrice": 12.0, "lineTotal": 6000.0 }
  ],
  "createdAt": "2026-06-18T10:00:00"
}
```

## 3. Approval callback (iFlow 2 body)
```json
{
  "correlationId": "PO-20260618-0001",
  "poNumber": "PO-20260618-0001",
  "decision": "APPROVED",
  "decidedBy": "S2-VENDOR-01",
  "reason": null,
  "decidedAt": "2026-06-18T10:02:00"
}
```
`decision`: `APPROVED` | `REJECTED`. CPI matches `correlationId` to route back to the origin.

## 4. Shared status vocabulary (PoStatus)
`DRAFT → SENT → RECEIVED → APPROVED|REJECTED → FULFILLED → SHIPPED`
Internal `OrderStatus` (REQUESTED…DELIVERED) drives the fulfilment scheduler and is mapped from PoStatus after APPROVED.

## 5. Inbound endpoints (each backend exposes two)
- `POST /api/sys{1,2}/cpi/inbound/po`        — receive a PO (dedup on idempotencyKey)
- `POST /api/sys{1,2}/cpi/inbound/approval`  — receive an approval/rejection (match on correlationId)

Outbound: backend → `POST {CPI_BASE}/http/po-outbound` with the `#meta` body from §1.
