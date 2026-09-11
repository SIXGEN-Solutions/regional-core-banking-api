# Amplitude Payment Account/Funds Sandbox

## Activation

```bash
export SPRING_PROFILES_ACTIVE=amplitude-payment-sandbox
export AMPLITUDE_PAYMENT_BASE_URL=https://<sandbox-host>
export AMPLITUDE_PAYMENT_OAUTH2_TOKEN_URI=https://<identity-host>/oauth2/token
export AMPLITUDE_PAYMENT_OAUTH2_CLIENT_ID=<client-id>
export AMPLITUDE_PAYMENT_OAUTH2_CLIENT_SECRET=<secret>
export AMPLITUDE_PAYMENT_OAUTH2_SCOPE=account.verify,funds.read,payment.post
export AMPLITUDE_PAYMENT_KEYSTORE_LOCATION=file:/secure/amplitude-payment.p12
export AMPLITUDE_PAYMENT_KEYSTORE_PASSWORD=<secret>
export AMPLITUDE_PAYMENT_TRUSTSTORE_LOCATION=file:/secure/amplitude-trust.p12
export AMPLITUDE_PAYMENT_TRUSTSTORE_PASSWORD=<secret>
```

## Required scenarios

- active account without opposition;
- account not found;
- blocked account;
- opposed account;
- sufficient XAF balance;
- insufficient funds;
- unsupported currency;
- exceeded transaction limit;
- 401, 403, 404, 409, 429;
- timeout and 5xx;
- empty and malformed 2xx response.

## Exit evidence

Retain correlation ID, provider code, attempt count, masked account reference,
duration and final Payment evidence outcome. Never retain OAuth tokens, full
accounts, certificates or raw secrets.
