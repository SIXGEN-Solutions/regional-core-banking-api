# Lot 5.1 — Transverse Integration Foundation

## Scope

The `integration` module contains only provider-neutral components:

- standard HTTPS `RestClient` construction;
- correlation, request and trace-context propagation;
- canonical technical failure classification;
- configurable timeout and retry primitives;
- low-cardinality metrics and observations;
- sensitive-value sanitization;
- JSON serialization;
- Kafka naming, headers, routing and transport;
- generic DLQ publication;
- consumer idempotency contracts.

Provider payloads, domain ports and provider-specific mappings remain in their
owning business modules.

## Canonical HTTP headers

- `X-Correlation-ID`
- `X-Request-ID`
- `Idempotency-Key`
- `traceparent`
- `tracestate`

The correlation identifier is preserved end to end. A new request identifier
is generated for each outbound call.

## Default resilience values

- connection timeout: 2 seconds;
- read timeout: 5 seconds;
- maximum attempts: 3;
- initial backoff: 250 milliseconds;
- maximum backoff: 2 seconds;
- jitter: 20 percent.

These values are defaults, not provider SLAs. Financial commands never receive
blind retry after an unknown outcome.

## Kafka conventions

Topic format:

```text
sixpay.<environment>.<domain>.<event-family>.v<major>
```

Retry and DLQ formats:

```text
<topic>.retry.<level>
<topic>.dlq
```

Payment events use `paymentId` as the partition key. Delivery is at least once;
consumers deduplicate using the immutable event ID.

## Security

The foundation contains no credentials, API keys, certificates or private keys.
Provider modules continue to reference platform-managed OAuth registrations,
SSL bundles and signing-key identifiers.

## Deferred implementation

Later lots still own:

- persistent `ProcessedMessageStore` adapters;
- final Kafka topology, ACLs and retention;
- schema registry;
- provider-specific error classification;
- provider OAuth2/mTLS configuration;
- operational DLQ replay endpoints and runbooks.
