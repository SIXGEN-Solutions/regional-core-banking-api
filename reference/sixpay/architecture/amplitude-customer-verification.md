# Customer Verification ↔ Amplitude

## Scope

This integration verifies customer, institution and account evidence before a
Payment decision. It remains owned by the Customer module.

The domain and application ports do not depend on Amplitude DTOs.

## Runtime path

```text
CustomerVerificationService
  -> BankingCustomerVerificationPort
  -> RetryingBankingCustomerVerificationAdapter
  -> AmplitudeCustomerVerificationAdapter
  -> AmplitudeResponseValidator
  -> AmplitudeCustomerVerificationClient
  -> OAuth2 client credentials
  -> HTTPS/mTLS Amplitude endpoint
```

## Contract status

The current contract is a sandbox-ready provisional baseline. It is not an
authoritative Amplitude specification until the provider approves:

- endpoint;
- request and response fields;
- functional codes;
- OAuth2 audience and scope;
- certificate chain;
- rate limits.

All provider-sensitive values are isolated in configuration or infrastructure
mappers.

## Error policy

- 401 and 403: authentication/authorization, no retry;
- 404 and 409: protocol/conflict, no retry;
- 429: retryable unavailable, respecting `Retry-After` when present;
- 5xx: retryable unavailable;
- timeout: retryable;
- empty, malformed or semantically invalid 2xx response: invalid response,
  no retry;
- business FAIL checks: normal response, no technical retry.

## Circuit breaker decision

No circuit breaker is added in Lot 5.3.

Rationale:

- the operation is read-only;
- connection and read timeouts are bounded;
- retry attempts are bounded;
- there is not yet sandbox evidence of a persistent failure cascade;
- opening a circuit could reject valid verifications during short provider
  recovery windows.

Reassess after sandbox metrics show sustained provider failure or thread-pool
pressure.

## Temporary values

The functional codes `00` through `04`, endpoint path and scope are provisional.
They can be changed by configuration without modifying the Customer domain.
