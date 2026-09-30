# R6 — Payment Execution / Recovery baseline

## Status and purpose
R6.0 consolidates Regional decisions and validated La Régionale legacy implementation evidence before contract evolution or physical Amplitude execution. This lot is documentation-only: no OpenAPI generation, endpoint/schema addition, Java implementation change, or production financial activation. Any canonical OpenAPI change remains subject to explicit human approval.

## Scope
R6 concerns the existing operations:
- `POST /api/v1/payment-events`;
- `GET /api/v1/payment-events/{paymentReference}`;
- `GET /api/v1/payment-events/idempotency/{idempotencyKey}`.

An operation with an uncertain financial outcome must never be blindly replayed.

## Evidence classification
### REGIONAL_CANONICAL
Regional governance, architecture and canonical OpenAPI. This baseline does not approve a contract change.

### BANK_IMPLEMENTATION_EVIDENCE
Validated La Régionale legacy extracts supplied for R6: complete BKEVE mapping/check query; account and balance queries; opposition controls; accounting date; day/night mode; BKOPE/SYN_BKOPE event-number lookup; debit/credit value-date calculations; JDBC execution behavior; and legacy event construction. They are authoritative only for the behavior they actually demonstrate and do not authorize invented SQL, columns, procedures, transaction semantics or rules.

### SIXPAY_REFERENCE
SIXPAY contracts/code remain consumer compatibility evidence only, never the Regional server source model.

## Validated R6 decisions

### T0 = BKEVE; BKMVTI = T1
R6 T0 Core Banking execution is based on BKEVE. BKMVTI entries are not persisted into Core Banking by `POST /api/v1/payment-events`; the reviewed evidence places them in calling-application/T1 accounting processing.

Therefore R6 must not insert BKMVTI. The current exactly-two-`providerEntries` T0 assumption requires separately governed contract review.

### Limits
Per-transaction, daily and other application payment limits are caller business rules, not R6 Core Banking constraints. Existing limit checks therefore require contract review; R6.0 does not change OpenAPI.

### Currency
The current Core Banking currency for this scope is `XAF`, managed by Core Banking. Any change to the existing request `currency` field requires separate contract approval.

### Core-Banking-authoritative context
`eventNumber`, `accountingDate` and `nightMode` are Core-Banking-authoritative values. They must not be treated as caller-authoritative banking facts. Because the current OpenAPI carries them in `PaymentProviderEvent`, its wire shape requires explicit contract review.

No historical SIXPAY context endpoint is reintroduced.

### Event number
The validated legacy method `lastNumEveOpe(String ope)` / `getLastNumeEveFromBkeve(String ope)` reads:
- night mode: `select num from bkope where ope = ?`;
- day mode: `select num from syn_bkope where ope = ?`.

The Core Banking value is authoritative. The evidence demonstrates reading `num`; it does not demonstrate application-side `num + 1`, an update, sequence or reservation algorithm. R6 must not invent one.

### Value dates
Debit and credit value dates are Core Banking calculations based on accounting date, weekends and holidays. Debit keeps the applicable previous-working-day behavior; credit keeps the applicable next-working-day behavior. The `ctrlProduit(pdr)` conditional branch is explicitly out of scope and must not be carried into Regional.

### Account/funds controls
Validated evidence supports account lookup with active/open filtering, applicable client/account/operation opposition checks, and the demonstrated available-funds formula. Because the account query combines existence and active/open filtering, R6.0 does not claim `ACCOUNT_EXISTS` and `ACCOUNT_ACTIVE` are independently observable checks; their final representation requires contract review.

### BKEVE
The complete reviewed BKEVE implementation supplies the physical insert mapping, ordered values and post-write event check query. It demonstrates `ETA = VA`, `SEN1 = D` and `SEN2 = C`. These are implementation evidence, but R6.0 does not declare `ETA = VA` alone to be the complete Regional `COMPLETED` criterion.

## Regional technical persistence and recovery
No approved BKEVE field directly maps Regional `paymentReference`. The validated direction is:

`paymentReference -> Regional technical persistence -> OPE / EVE / DCO -> Core Banking`

Regional technical persistence also owns protocol correlation/idempotency metadata such as the request fingerprint, while Amplitude/Informix remains authoritative for banking facts/outcomes. The exact R6 PostgreSQL table/migration is a later Regional architecture decision and is not invented here.

## Financial safety
- never blindly replay an unknown financial outcome;
- same idempotency key + same request must not duplicate execution;
- same key + different request must conflict;
- recovery never invokes execute as a fallback;
- uncertain execution must be authoritatively recovered before retry.

## Remaining evidence/decisions before physical production execution
1. Final `COMPLETED` criterion after BKEVE execution, including whether committed `ETA = VA` is sufficient.
2. Regional `bankReference` source/format.
3. Exact recovery procedure for connection/JDBC failure around commit, including BKEVE/historical lookup timing.
4. Whether absence of an OPE row in the demonstrated BKOPE/SYN_BKOPE lookup is the authoritative invalid-operation signal or another validation applies.
5. Exact configured physical event table selection if day/night BKEVE tables are dynamic.

## Contract-impact findings for the next governed lot
R6.0 records, but does not approve, review needs for:
- caller-provided `eventNumber`;
- caller-provided `accountingDate`;
- caller-provided `nightMode`;
- `providerEntries` / exactly-two-entry T0 assumption;
- transaction/daily/other limit checks;
- currency representation for current XAF scope;
- representation of account validity checks.

SIXPAY remains reference/compatibility evidence only. Any later wire change must include consumer compatibility verification.

## R6.0 closure state
R6.0 removes obsolete assumptions from the prior baseline. Physical production execution remains fail-closed until remaining banking decisions and any required contract changes receive the applicable approval.

Status: `INCOMPLETE_CONTEXT` for physical production execution.
