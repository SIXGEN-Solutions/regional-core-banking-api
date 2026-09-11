# R4 — Customer / Account Verification baseline

## Repository and revision

- Repository: `SIXGEN-Solutions/regional-core-banking-api`
- Starting branch: `feat/customer-account-verification`
- Starting SHA: `c79d748c8078528d2074fa31911f345c79d65f09`
- SIXPAY compatibility baseline: `main @ b6da7db33432cb81997cc293b21080dd46fdcc14`
- Execution mode: local patch script; no GitHub mutation by ChatGPT

## Contract

The canonical Regional V1 contract is approved and already contains:

- `GET /api/v1/customers`
- `GET /api/v1/customers/{customerReference}`
- `GET /api/v1/customers/{customerReference}/accounts`
- `POST /api/v1/customer-verifications`

R4 does not add or modify endpoints or schemas and does not execute OpenAPI generation.

## Implementation boundary

R4 adds the Regional capability model and application orchestration behind the
approved transport contract:

`api -> application -> domain <- infrastructure`

The production banking access is deliberately fail-closed until La Régionale
provides approved evidence for the actual Amplitude/Informix access mechanism.

No Informix table, column, SQL query, stored procedure, transaction boundary,
Amplitude code or provider-native mapping is invented by this lot.

## Compatibility

The implementation keeps the customer/account verification semantics needed by
the current SIXPAY compatibility contract:

- customer search by NIU/customer number;
- canonical banking customer identity and KYC facts;
- customer account lookup;
- composite verification outcomes `VERIFIED`, `REJECTED`, `INDETERMINATE`;
- checks for customer/institution/NIU/identity/account/ownership/status/
  restrictions/KYC facts;
- negative business verification remains a completed verification outcome.

SIXPAY DTOs/classes are not imported into Regional production code.

## Blocking bank evidence

Before the infrastructure adapter can become functional against the real bank
system, La Régionale must supply or confirm:

1. access mode: direct JDBC/Informix vs existing internal service/procedure;
2. approved tables/views/procedures and transaction boundaries;
3. field mappings for customer reference, customer number, NIU, legal name,
   phone, email and KYC state;
4. field mappings for account reference, RIB/IBAN, currency, type, status and
   restrictions/oppositions;
5. exact semantics for "active", "blocked", "opposed" and KYC verified/present;
6. sandbox connectivity and credentials as external configuration.

Until then, integration testing against Informix is blocked by missing bank evidence.
