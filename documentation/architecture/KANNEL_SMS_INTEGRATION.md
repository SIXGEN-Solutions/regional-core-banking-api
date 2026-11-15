# R5.2.5 — Kannel SMS OTP adapter

## Status

`R5.2.5.1 — PATCH_PREPARED / VALIDATION_PENDING`

## Confirmed bank transport

La Régionale confirmed the OTP SMS transport:

```text
Regional Core Banking API
  -> HTTP /cgi-bin/sendsms
  -> Kannel
  -> SMPP
  -> M-Target
  -> operator
  -> customer
```

Amplitude and BKSMS/BKMSMS are not used for OTP SMS.

The application remains responsible for OTP generation, secure verifier persistence,
expiration and verification.

Required Kannel request parameters:

- `user`
- `password`
- `to`
- `text`
- `from`

The confirmed sender is `LAREGIONALE`.

Phone numbers are sent as `237` followed by the national number, without `+`.

`charset` and `coding` are only required when the text falls outside the GSM character set.
The initial OTP message stays within the GSM character set.

## Delivery semantics

`0: Accepted for delivery` means Kannel accepted the SMS for downstream delivery.
It does not prove handset reception. No delivery receipt is returned to this API.

Regional maps this result to `ConfirmationDeliveryPort.Outcome.ACCEPTED`.

If the HTTP request leaves the application but no response is received because of timeout
or connection loss, the result is `UNKNOWN`. The application must not automatically replay
the request because Kannel may already have accepted it.

An explicit Kannel rejection maps to `CONFIRMED_FAILURE`.

## Configuration

Production values remain external:

- `REGIONAL_OTP_SMS_ENABLED`
- `REGIONAL_OTP_SMS_BASE_URL`
- `REGIONAL_OTP_SMS_USERNAME`
- `REGIONAL_OTP_SMS_PASSWORD`
- `REGIONAL_OTP_SMS_SENDER`

Host, port and credentials are intentionally absent from source control.

La Régionale will open the internal HTTP flow between the dedicated application server and
Kannel. There is no externally exposed Kannel endpoint and no separate Kannel test service.

## Security

The bank-defined Kannel interface places credentials in HTTP query parameters. Therefore
the application must never log the full request URI/query string, OTP, password or
unmasked destination phone number.

No OpenAPI endpoint or schema change is introduced by this lot.

## R5.2.5.1 — HTTP hardening and local validation

The Kannel client is explicitly configured with connection/read timeouts and follows
no redirects. Defaults are implementation values of 5000 ms (each); La Régionale
did not impose timeout values. Override using
`REGIONAL_OTP_SMS_CONNECT_TIMEOUT_MS` and `REGIONAL_OTP_SMS_READ_TIMEOUT_MS`.

An exact `0: Accepted for delivery` over a successful HTTP exchange means
`ACCEPTED`. Explicit non-acceptance text returned by Kannel over HTTP 2xx means
`CONFIRMED_FAILURE`. Ambiguous HTTP errors, empty results, socket errors and
timeouts mean `UNKNOWN`, with **no automatic retry**. This deliberately avoids
claiming definitive rejection when the message may already have been accepted.

The SMS HTTP test server is in-process and never connects to La Régionale Kannel.
It asserts the bank request parameters, acceptance, explicit rejection,
HTTP-error uncertainty, timeout uncertainty and single submission.

For simultaneous EMAIL + SMS delivery, the internal aggregate result is
conservatively `UNKNOWN` if at least one channel is unknown. This is an
implementation safety policy pending a separately approved representation of
per-channel results. The approved OpenAPI continues exposing only the singular
`deliveryChannel` and remains unchanged.

Security note: the Kannel interface transmits password and OTP in an HTTP URL.
Do not enable full URL/request logging on HTTP clients, reverse proxies, gateways
or observability agents. The bank's internal network-only constraint remains.
Full network/infrastructure assurance requires an operational deployment review.
