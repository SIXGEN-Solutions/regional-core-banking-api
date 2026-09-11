# Amplitude Posting Status and Reconciliation

Required scenarios:

1. lookup by idempotency key returns completed;
2. lookup by idempotency key returns rejected;
3. idempotency lookup returns 404 and bank-reference fallback succeeds;
4. both lookups return 404;
5. debit confirmed and CUT credit pending;
6. reversal required;
7. unknown outcome requiring another query;
8. unknown outcome requiring manual reconciliation;
9. malformed 200 response;
10. 401, 403, 429 and 5xx.

Operational rules:

- never replay a financial command from the reconciliation service;
- preserve the original banking idempotency key;
- record correlation ID and lookup channel;
- use bounded scheduler cadence outside the domain;
- escalate after the operational time window is exceeded;
- never log raw account values, OAuth tokens or certificates.
