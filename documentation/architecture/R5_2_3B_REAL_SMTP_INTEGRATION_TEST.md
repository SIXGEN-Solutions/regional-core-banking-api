# R5.2.3b — Real SMTP integration test

Status: TEST INFRASTRUCTURE ONLY.

This profile exercises the real Spring `JavaMailSender` and
`EmailConfirmationDeliveryAdapter` against an externally configured SMTP server.
It does not mock SMTP and it does not establish any La Régionale production mail
infrastructure decision.

## Safety boundary

The `smtp-test` profile resolves the recipient from
`REGIONAL_OTP_EMAIL_TEST_RECIPIENT`. This is deliberately test-only and must not
be used as the production banking recipient source.

The normal runtime profile remains unchanged and continues to use the existing
delivery wiring.

No SMTP credential, HMAC key, OTP or recipient address may be committed.

## Required configuration

Configure the existing technical PostgreSQL and HMAC environment variables, then:

- `REGIONAL_OTP_EMAIL_HOST`
- `REGIONAL_OTP_EMAIL_PORT`
- `REGIONAL_OTP_EMAIL_USERNAME`
- `REGIONAL_OTP_EMAIL_PASSWORD`
- `REGIONAL_OTP_EMAIL_TLS`
- `REGIONAL_OTP_EMAIL_SENDER`
- `REGIONAL_OTP_EMAIL_TEST_RECIPIENT`

Start with Spring profile `smtp-test`.

Example:

`mvn -Pr3-no-openapi-generation spring-boot:run -Dspring-boot.run.profiles=smtp-test`

Then create a Payment Confirmation challenge through the approved HTTP endpoint.
The generated OTP must be delivered through the configured external SMTP server.
Use the received OTP to call the approved verification endpoint.

This test proves the external SMTP path only. It does not prove the future
authoritative Amplitude customer-email lookup.
