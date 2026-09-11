# Amplitude Customer Verification Sandbox Runbook

## Required environment

```bash
export SPRING_PROFILES_ACTIVE=amplitude-sandbox
export CORE_BANKING_BASE_URL=https://<sandbox-host>
export CORE_BANKING_CUSTOMER_VERIFICATION_PATH=/v1/accounts/verify
export CORE_BANKING_OAUTH2_TOKEN_URI=https://<identity-host>/oauth2/token
export CORE_BANKING_OAUTH2_CLIENT_ID=<client-id>
export CORE_BANKING_OAUTH2_CLIENT_SECRET=<secret>
export CORE_BANKING_OAUTH2_SCOPE=customer.verify
export CORE_BANKING_KEYSTORE_LOCATION=file:/secure/amplitude-client.p12
export CORE_BANKING_KEYSTORE_PASSWORD=<secret>
export CORE_BANKING_TRUSTSTORE_LOCATION=file:/secure/amplitude-truststore.p12
export CORE_BANKING_TRUSTSTORE_PASSWORD=<secret>
```

## Preconditions

- sandbox DNS and network route available;
- source IP allowlisted;
- client certificate accepted;
- trust chain imported;
- OAuth2 client credentials enabled;
- test account and NIU supplied by the bank.

## Certification scenarios

1. successful verification;
2. customer not found;
3. account not found;
4. account blocked;
5. account opposed;
6. 401 invalid client;
7. 403 insufficient scope;
8. 404 wrong endpoint;
9. 409 provider conflict;
10. 429 with `Retry-After`;
11. timeout;
12. 500/503;
13. empty 200 response;
14. malformed JSON response;
15. unknown functional code.

## Evidence to retain

- timestamp;
- correlation ID;
- HTTP status;
- provider code;
- retry attempts;
- masked account reference;
- token and certificate identifiers, never secret values;
- metrics and trace screenshots.

## Completion condition

The lot is sandbox-certified only after the real provider endpoint, code table,
OAuth2 claims and mTLS chain have been validated. MockWebServer tests alone are
not sandbox certification.
