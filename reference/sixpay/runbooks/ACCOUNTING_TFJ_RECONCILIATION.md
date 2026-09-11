# Accounting TFJ reconciliation and finality runbook

## Purpose

Operate the Accounting end-of-day / TFJ confirmation flow without bypassing
the approved contracts, Accounting ownership or Payment finality rules.

Canonical physical contract:

`documentation/contracts/amplitude/amplitude-end-of-day-confirmation-api-v1.yaml`

Amplitude is the TFJ system of record. The runbook does not redefine that
contract.

## Normal outcomes

### PENDING

Persist the confirmation. Do not update Payment finality. Wait for a later
authoritative callback or controlled lookup result.

### INTEGRATED + unique match

The confirmation must already be durably persisted and uniquely matched on:

- financial institution code;
- business date;
- Payment reference;
- bank posting reference.

Accounting publishes the provider-neutral finality event only after commit.
Payment applies its existing reconciliation policy and may reach
`TREASURY_INTEGRATED`.

### FAILED + unique match

Accounting forwards the authoritative failure evidence and recovery action.
Payment remains the owner of its lifecycle. Do not force a Payment state from
Accounting or with manual SQL.

## Quarantine

### UNMATCHED

Do not modify Payment.

Verify the four matching facts against the Accounting batch and the
authoritative Amplitude result. Do not edit the stored TFJ row, re-key a
Payment or manufacture a match.

The current baseline does not automatically rematch a previously persisted
unmatched confirmation. Escalate for controlled reconciliation if the
authoritative facts cannot be matched.

### AMBIGUOUS

Do not select one candidate manually and do not update Payment.

Investigate duplicate/inconsistent Accounting evidence and the four matching
facts. Preserve the original confirmation and correlation identifiers for
traceability.

## Identical replay

An identical replay is a no-op. No second Payment finality effect is expected.

## Conflicting replay

The same confirmation identity or idempotency key with a different logical
payload is a conflict. Preserve the first durable evidence, return/observe the
contractual conflict and investigate the provider/system integration.

Never overwrite the stored evidence to make the replay succeed.

## Finality publication failure

A matched terminal confirmation remains recoverable while
`finalityPublishedAt` is null.

Operational signals:

- `sixpay.accounting.tfj.finality.pending`;
- `sixpay.accounting.tfj.finality.publication{outcome=...}`;
- structured log `TFJ finality publication failed`.

Do not insert a Payment state manually and do not replay a banking financial
command. Use only the application-level pending-finality recovery capability
(`TfjFinalityPublicationService.publishPending`) from approved operational
tooling. No public or internal HTTP recovery endpoint is introduced by T1.

## Ingestion observability

Metrics are deliberately low-cardinality:

- `sixpay.accounting.tfj.ingestion{tfj_status,receipt_status}`;
- `sixpay.accounting.tfj.conflicts`;
- `sixpay.accounting.tfj.finality.publication{outcome}`;
- `sixpay.accounting.tfj.finality.pending`.

Payment reference, bank reference, confirmation ID and correlation ID are
allowed in structured operational logs where required for investigation, but
are never metric tags.

Never log OAuth tokens, client secrets, private keys, OTP values, raw banking
payloads or account credentials.

## Provider lookup

The read-only Amplitude TFJ lookup is available only when the Accounting API
provider configuration is enabled. Do not create a standalone/mock provider
client to make a runtime profile boot.

The TRESOR PAY status-query contract remains a separate `REFERENCE_MVP`
capability and is not generated while its registry approval/generation policy
forbids generation.

## Escalation evidence

Capture only:

- confirmation ID;
- correlation ID;
- financial institution code;
- business date;
- Payment reference;
- bank posting reference;
- TFJ status;
- match status;
- provider batch/item references where already part of approved evidence.

Do not attach secrets or raw provider payloads.

## Validation after remediation

Run the targeted Accounting tests first, then the repository gates:

```bash
cd backend
mvn -pl accounting,payment -am -DskipITs verify
cd ..
py scripts/verify_accounting_t1_closure.py
py scripts/verify_master_prompt_input_manifest.py
py scripts/verify_master_engineering_prompt.py
py scripts/verify_documentation_final.py
py scripts/verify_baseline.py
```

When Docker and the clean-room prerequisites are available:

```bash
py scripts/verify_clean_room.py
```

A command is evidence only when it actually finishes with exit code zero.
