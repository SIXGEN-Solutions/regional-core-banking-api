# Lot 5.4.1 — Payment Account, Opposition and Funds

## Boundary

The Payment application retains its existing ports:

- `VerificationGateway`;
- `FundsGateway`.

The existing provider adapters remain in Payment infrastructure. They now
depend on the narrow `AmplitudeAccountFundsClient`, not on the generic
`AmplitudeBankingClient`.

This prevents Lot 5.4.1 from pretending that posting, lookup or reversal are
implemented.

## Nominal path

```text
Payment orchestration
  -> VerificationGateway / FundsGateway
  -> AmplitudeVerificationAdapter / AmplitudeFundsAdapter
  -> RestAmplitudeAccountFundsClient
  -> OAuth2 client credentials
  -> HTTPS/mTLS
  -> Amplitude sandbox or production endpoint
```

## Evidence

Provider responses are validated and mapped to existing immutable Payment
snapshots:

- `BankingVerificationSnapshot`;
- `FundsControlSnapshot`.

Account opposition is represented through
`BankingVerificationCheckType.ACCOUNT_NOT_OPPOSED`.

Funds sufficiency is represented through
`FundsControlCheckType.AVAILABLE_FUNDS_SUFFICIENT`.

## Retry

Both operations are read-only and use the shared read-only retry executor.

Retryable:

- connection failure or timeout;
- HTTP 429;
- HTTP 5xx.

Non-retryable:

- 401/403;
- 404/409 and other contract failures;
- empty or malformed response;
- unknown provider functional code;
- valid business rejection such as opposed account or insufficient funds.

## Deferred

The following remain outside 5.4.1:

- posting;
- posting lookup;
- unknown posting outcome;
- reversal;
- compensation.
