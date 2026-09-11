# Amplitude Payment Posting Sandbox

Required scenarios:

1. completed debit and CUT credit;
2. business rejection with no financial effect;
3. debit completed and CUT credit pending;
4. reversal required;
5. duplicate same idempotency key and same payload;
6. duplicate key with different payload;
7. timeout after commit;
8. 429 after request emission;
9. 500/503 after request emission;
10. malformed or empty success response;
11. lookup by idempotency key;
12. lookup by bank posting reference.

Evidence must retain the payment ID, idempotency key, correlation ID, principal
bank reference, leg statuses, business date and next action. Do not log raw
accounts, OAuth tokens, certificates or secrets.
