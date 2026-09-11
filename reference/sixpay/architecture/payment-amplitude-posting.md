# Lot 5.4.3 — Payment Confirmation and Posting

## Existing model reused

The Payment module already owns:

- `PaymentEventExecutionPort`;
- `BankingIdempotencyKey`;
- `PaymentEventOutcomeSnapshot`;
- posting lookup ports;
- explicit `UNKNOWN` and reconciliation actions.

## Nominal path

```text
Payment orchestration
  -> PostingGateway
  -> AmplitudePostingAdapter
  -> RestAmplitudePaymentEventClient
  -> OAuth2 client credentials
  -> HTTPS/mTLS
  -> Amplitude posting endpoint
```

## Safety

Posting is a financial side effect and is never retried automatically.

Timeout, broken connection, 429 or 5xx after request emission produce an
unknown outcome. The caller must preserve the same banking idempotency key and
resolve the outcome through lookup before any replay.

## Confirmation

A posting is confirmed only when the returned snapshot satisfies the existing
Payment invariants:

- debit leg succeeded;
- CUT credit leg succeeded;
- bank posting reference present;
- next action is `NONE`.

A valid business rejection must prove no financial effect.
