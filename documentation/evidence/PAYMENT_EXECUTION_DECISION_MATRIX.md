# R6 — Payment Execution evidence and decision matrix

This R6.0 traceability artifact separates validated decisions, implementation evidence, contract-impact findings and unresolved items. It neither approves OpenAPI changes nor authorizes production financial execution.

| Topic | Classification | Status | Evidence / decision | Consequence |
|---|---|---|---|---|
| T0 persistence | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | T0 uses BKEVE; `/payment-events` does not post BKMVTI. | R6 adapter must not insert BKMVTI. |
| BKMVTI | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Calling-application/T1 accounting artifact. | `providerEntries` T0 assumption needs contract review. |
| Payment limits | Regional decision | CONFIRMED | Transaction/daily/other limits belong to callers. | Current limit checks need contract review. |
| Currency | Regional decision | CONFIRMED | Current Core Banking scope is XAF, managed by Core Banking. | Existing request representation needs contract review. |
| Event number authority | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Core Banking authoritative; BKOPE/SYN_BKOPE `num` by OPE. | Caller `eventNumber` needs contract review; invent no increment. |
| Accounting date | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Core Banking authoritative. | Caller `accountingDate` needs contract review. |
| Night mode | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Core Banking authoritative. | Caller `nightMode` needs contract review. |
| Debit value date | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Applicable previous-working-day logic. | Implement in Amplitude ACL. |
| Credit value date | BANK_IMPLEMENTATION_EVIDENCE + decision | CONFIRMED | Applicable next-working-day logic. | `ctrlProduit` branch excluded. |
| Account lookup | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | Lookup plus active/open filtering demonstrated. | Infrastructure/Amplitude only. |
| ACCOUNT_EXISTS vs ACTIVE | BANK_IMPLEMENTATION_EVIDENCE | CONTRACT_REVIEW_REQUIRED | Demonstrated query combines them. | Do not claim independent observations without evidence. |
| Oppositions | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | Client/account/operation checks supplied. | Final reason-code mapping remains governed. |
| Available funds | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | Core Banking formula supplied. | Implement exact approved formula. |
| BKEVE insert | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | Complete insert mapping and ordered values supplied. | R6.2 can map without inventing columns. |
| BKEVE directions | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | `SEN1=D`, `SEN2=C`. | Preserve in ACL. |
| BKEVE status | BANK_IMPLEMENTATION_EVIDENCE | PARTIAL | `ETA=VA` demonstrated. | Not yet sufficient by itself for `COMPLETED`. |
| Post-write lookup | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED | BKEVE event check query supplied. | Candidate recovery verification; exact sequence open. |
| paymentReference mapping | BANK_IMPLEMENTATION_EVIDENCE | CONFIRMED_ABSENT | No approved direct BKEVE field mapping. | Regional persistence resolves to OPE/EVE/DCO. |
| Technical persistence | Regional architecture direction | DIRECTION_CONFIRMED | Protocol idempotency/correlation, not banking authority. | Physical schema deferred; do not invent in R6.0. |
| Blind replay | REGIONAL_CANONICAL | PROHIBITED | Unknown outcome requires recovery. | Preserve UNKNOWN/recovery semantics. |
| bankReference | Missing decision | OPEN | Source/format not approved. | Resolve before production mapping. |
| Final success criterion | Missing decision | OPEN | BKEVE known; `COMPLETED` rule not approved. | Resolve before physical activation. |
| Commit uncertainty | Missing decision | OPEN | Lookup exists; exact sequence not approved. | Resolve before physical activation. |
| OPE validation | Missing evidence/decision | OPEN | BKOPE/SYN_BKOPE lookup demonstrated only. | Do not invent invalid-OPE rule. |
| Dynamic event tables | Missing evidence if dynamic | OPEN | BKEVE mapping known. | Confirm exact configured table selection. |

## Source ownership
- `REGIONAL_CANONICAL`: approved Regional-owned artifacts.
- `BANK_IMPLEMENTATION_EVIDENCE`: validated La Régionale evidence, authoritative only for demonstrated behavior.
- `SIXPAY_REFERENCE`: compatibility/reference only.
- `OPEN`: affected financial behavior remains fail-closed.

## Next lots
### R6.1 — Bank Execution Evidence & Mapping
Close remaining banking decisions and produce the Regional-to-Amplitude mapping. No unsupported SQL, field, status or recovery behavior.

### R6.2 — Physical Amplitude Payment Execution & Recovery
Implement only after R6.1 and after any contract-impacting changes have followed the required human approval workflow.
