# Regional Core Banking API — HTTPS/mTLS (lot 1)

## Scope

This lot secures the **Regional inbound API** only. It does not modify banking
operations, implement SIXPAY outbound mTLS, authorize production banking, or
change the approved OpenAPI contract. Regional OpenAPI V1 still describes
OAuth2 + mTLS until its separate security-contract approval/evolution.

## Profiles

- `local` or `docker`, without `regional-mtls`: current HTTP development mode.
  **Never expose these profiles as a trusted private banking endpoint.**
- `regional-mtls`: TLS 1.3, server key material and truststore, required client
  certificate, plus application allowlist of SHA-256 certificate fingerprints.
- Deployment overlay `compose.mtls.yml` explicitly opts into the secure profile;
  the base `compose.yml` remains unchanged and retains its loopback port.

A trusted CA alone is insufficient: the application also requires an explicitly
approved end-entity client certificate fingerprint. For certificate rotation,
add both old/new SHA-256 fingerprints to the comma-separated allowlist during
an approved overlap window, then retire the old fingerprint.

## Bank PKI prerequisites

Provision externally (NEVER commit):

- `/opt/regional/mtls/regional-server.p12`: PKCS12 server private key and chain;
  server certificate SAN must match the private hostname used by SIXPAY.
- `/opt/regional/mtls/regional-client-truststore.p12`: PKCS12 trust anchors for
  allowed bank-issued client certificates.
- `/opt/regional/secrets/REGIONAL_MTLS_KEYSTORE_PASSWORD`: keystore password.
- `/opt/regional/secrets/REGIONAL_MTLS_TRUSTSTORE_PASSWORD`: truststore password.
- `REGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256`: SHA-256 of the approved leaf SIXPAY
  certificate (or comma-separated values for controlled rotation).

The host paths and SELinux labels must permit **read-only** container access.
Certificate passwords are Spring configtree secrets, not committed YAML values.
The host operator owns permissions, issuance, revocation and renewal controls.
Revocation-status checking beyond the default TLS trust validation requires a
separate bank policy and implementation assessment.

## Local developer mode (no mTLS)

Keep using the existing local profile or base local Docker Compose. Do not add
`regional-mtls` to `SPRING_PROFILES_ACTIVE`; HTTPS credentials are not required.

## Secure Contabo mode (NOT executed by this lot)

Review `deploy/contabo/.env.example` and prepare the host env file with approved
`REGIONAL_PRIVATE_BIND_IP` and `REGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256`.
Use an immutable published API image containing this implementation.

```bash
docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml up -d
```

The production configuration explicitly imports the secure profile document
when `regional-mtls` is active, because `SPRING_CONFIG_LOCATION` replaces the
packaged application configuration. Never activate the overlay before PKI,
network/SELinux, host mounts and image are approved. Ensure VM-03 firewalld
allows the private service port **only from VM-01**, with no public forwarding.

The overlay adds a private-interface mapping while retaining the base loopback
mapping, which becomes HTTPS in this mode. Health probes and Swagger on that
listener must still pass the client certificate admission filter.

## Validation commands for the operator (NOT executed by this lot)

```bash
# Focused filter test
./mvnw -Dtest=RegionalMtlsClientCertificateFilterTest test

# Full project gates
./mvnw verify

# Offline configuration review (no container deployment)
docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

# Negative: no client certificate must not access the secured API
openssl s_client -connect REGIONAL_PRIVATE_HOST:9092 \
  -servername REGIONAL_PRIVATE_DNS -tls1_3 </dev/null

# Positive: supply bank-issued client certificate/key and trusted server CA
curl --fail-with-body --cacert SERVER_CA.pem \
  --cert SIXPAY_CLIENT.pem --key SIXPAY_CLIENT.key \
  https://REGIONAL_PRIVATE_DNS:9092/actuator/health
```

TLS handshake success, response codes and service availability are distinct
checks. A valid client certificate signed by the trusted CA but **not** on the
approved fingerprint allowlist must get HTTP 403. A missing certificate must
be rejected at handshake with `server.ssl.client-auth: need`.

## Residual risks and decisions

- Regional V1 OpenAPI security and SIXPAY consumers are not updated in this lot.
- Public/private address, CA inventory, naming, revocation and certificate
  rotation require bank operational approval.
- The current Contabo stack is a synthetic banking mock, not live-bank approval.
- No production security assertion is made until TLS, application authorization,
  firewall and negative acceptance tests have been executed successfully.
