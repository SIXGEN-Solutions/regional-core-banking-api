# REGIONAL CORE BANKING API — Target Architecture

## Product position
`regional-core-banking-api` is a Regional banking integration product, not a SIXPAY module and not a SIXPAY-specific façade.

Consumers:
- SIXPAY CONNECT
- future Regional internal applications
- other explicitly authorized systems

System of record:
- La Régionale / Amplitude / Informix for banking facts and execution outcomes.

## Repository shape
```text
regional-core-banking-api/
├─ .github/workflows/
├─ contracts/
│  ├─ openapi/regional-core-banking-api-v1.yaml
│  └─ reference/sixpay/
├─ documentation/
│  ├─ architecture/
│  ├─ adr/
│  └─ runbooks/
├─ reference/sixpay/
├─ src/main/java/com/regional/corebanking/
│  ├─ customer/
│  ├─ confirmation/
│  ├─ payment/
│  ├─ accounting/
│  ├─ tfj/
│  ├─ security/
│  ├─ audit/
│  ├─ common/
│  └─ configuration/
├─ src/test/
├─ Dockerfile
├─ compose.yaml
├─ pom.xml
├─ mvnw
├─ mvnw.cmd
└─ README.md
```

## Capability package rule
Each capability uses:
```text
<capability>/
├─ api/
├─ application/
│  ├─ port/in/
│  ├─ port/out/
│  └─ service/
├─ domain/
└─ infrastructure/
   └─ amplitude/
```

Dependency direction:
`api -> application -> domain <- infrastructure`.

## Model separation
Four models remain separate:
1. Regional OpenAPI transport model.
2. Regional application/domain model.
3. Amplitude anti-corruption model.
4. Informix/JDBC representation.

SIXPAY Java models are a fifth, external consumer-side representation and must not be imported into the Regional server.

## OpenAPI ownership
The Regional repository owns the canonical OpenAPI. SIXPAY reference contracts help bootstrap and test compatibility but are not copied as the canonical Regional contract unchanged.

## Compatibility
For retained V1 operations, validate the Regional OpenAPI against the actual SIXPAY client wire behavior:
- method/path,
- headers,
- request JSON,
- response JSON,
- nullability/formats,
- business/status codes,
- HTTP statuses,
- idempotency/recovery,
- authentication expectations.

A Regional internal Java name may differ freely from SIXPAY if the wire contract remains compatible.

## Runtime
One Spring Boot application on the Core Banking API VM. It accesses Informix only through dedicated infrastructure adapters. No controller or domain object accesses JDBC directly.

## Persistence
Amplitude/Informix remains authoritative for banking facts and banking execution
outcomes, but R5.2 Payment Confirmation technical state is separated from Amplitude.

The validated R5.2 architecture uses a dedicated PostgreSQL technical persistence
store owned by Regional Core Banking API for durable challenge state,
idempotency/recovery and delivery technical state. Plaintext OTP, HMAC secret keys
and SMTP credentials must never be persisted there.

Multi-instance correctness relies primarily on PostgreSQL transactions and database
uniqueness/concurrency guarantees, not JVM-local locks as production source of truth.
Physical schema/migrations are defined by R5.2.1.

## Payment Confirmation delivery
Regional Core Banking API is authoritative for OTP generation, challenge lifecycle,
verification and delivery orchestration. Delivery channels are selected by Regional
deployment configuration, never by consumers; SMS, EMAIL or both may be enabled.

EMAIL uses a Regional-owned outbound mail adapter with environment-provided
SMTP/relay and secret configuration. SMS uses the confirmed bank chain
`Amplitude -> SMS database -> Kannel -> M-Target (SMPP) -> operators -> customer`;
Regional does not implement direct SMPP. Its physical database adapter awaits exact
La Régionale mapping/write/status evidence.

The approved OpenAPI currently exposes singular `deliveryChannel`; simultaneous
SMS+EMAIL is a known contract divergence. No wire change occurs until an explicitly
approved Regional contract evolution defines its representation and compatibility.

## Observability
Log correlation, caller/client identity, operation, result, duration and timestamp. Never log OTP, credentials, tokens, secrets or unmasked sensitive banking data.
