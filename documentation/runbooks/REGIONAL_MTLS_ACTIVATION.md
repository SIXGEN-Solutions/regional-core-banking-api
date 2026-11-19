# Regional Core Banking API — HTTPS/mTLS (lot 1, consolidated)

## Scope

Regional inbound HTTPS/mTLS only. Banking business rules and external API schemas
remain untouched. The Regional OpenAPI now describes **mTLS without OAuth2 Bearer** for
SIXPAY-to-Regional calls. This security amendment requires bank/security
ratification before its approval status can be considered renewed.
Current Contabo is an Oracle SYNTHETIC mock, not approval for live banking.

## Single application YAML per environment

- Docker: `src/main/resources/application-docker.yml`.
- Contabo: `deploy/contabo/application-production.yml` (external file).
- No `application-regional-mtls.yml` and no `regional-mtls` Spring profile.
- No `spring.ssl.bundle`: `server.ssl.*` configures the server directly.

The SAME `ENABLE_MTLS` controls both `server.ssl.enabled` and the
`RegionalMtlsClientCertificateFilter`. A separately configured
`REGIONAL_MTLS_REQUIRED=true` blocks startup if `ENABLE_MTLS=false`.

| Environment | ENABLE_MTLS | REGIONAL_MTLS_REQUIRED | Transport |
| --- | --- | --- | --- |
| Local Docker | false | false | HTTP |
| Existing Contabo Oracle mock (loopback only) | false | false | HTTP |
| Private secured bank-facing installation | true | true | HTTPS/TLS 1.3 and client certificate |

Do not expose HTTP local/mock to untrusted networks. An environment requiring
banking-grade security must enforce `REGIONAL_MTLS_REQUIRED=true` in its
protected deployment policy (not merely in a developer-editable file).

## Configuration values

- `SECURITY_MTLS_ALLOWED_CLI_CERT`: approved SHA-256 fingerprint(s) of SIXPAY's
  **leaf** certificate, 64 hexadecimal characters each, separated by commas.
- `REGIONAL_MTLS_KEYSTORE_LOCATION`: server PKCS12 resource, for example
  `file:/run/mtls/regional-server.p12`.
- `REGIONAL_MTLS_TRUSTSTORE_LOCATION`: client trust roots PKCS12 resource, for
  example `file:/run/mtls/regional-client-truststore.p12`.
- `REGIONAL_MTLS_KEYSTORE_PASSWORD`, `REGIONAL_MTLS_TRUSTSTORE_PASSWORD`:
  external passwords loaded from existing Compose secrets/configtree.

Never commit real keys, truststores or passwords. The server certificate SAN
must match the private hostname used by SIXPAY. TLS establishes certificate
chain trust; the filter additionally checks the authorized leaf fingerprint.
Approved rotation may temporarily allow more than one fingerprint.

## Contabo private deployment (not executed by this patch)

With bank PKI, network and release approval, set in `/opt/regional/config/.env`:

```dotenv
ENABLE_MTLS=true
REGIONAL_MTLS_REQUIRED=true
SECURITY_MTLS_ALLOWED_CLI_CERT=<approved-leaf-SHA256>
REGIONAL_MTLS_KEYSTORE_LOCATION=file:/run/mtls/regional-server.p12
REGIONAL_MTLS_TRUSTSTORE_LOCATION=file:/run/mtls/regional-client-truststore.p12
REGIONAL_PRIVATE_BIND_IP=<private-VM03-IP>
```

Provision read-only PKCS12 files under `/opt/regional/mtls/` and password
secrets under `/opt/regional/secrets/`. Use the optional
`deploy/contabo/compose.mtls.yml` overlay **only** for Docker port, mounts and
secrets: the overlay no longer selects a Spring mTLS profile. The base Compose
publishes only loopback; the overlay adds a private interface. Restrict the
private port to VM-01 using firewalld. Confirm SELinux labels, permissions and
certificate issuance/revocation/rotation under Regional bank policy.

```bash
# Configuration rendering only (does not deploy)
docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

# Only after separate operational deployment authorization:
docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml up -d
```

For local testing keep both flags false and do not use the private overlay.
If ENABLE_MTLS=true without required certificates, startup must fail; no
fallback to HTTP is acceptable. If REGIONAL_MTLS_REQUIRED=true but ENABLE_MTLS
is false, the startup guard also fails closed.

## Operator-run validation (not executed by patch)

```bash
./mvnw -Dtest=RegionalMtlsClientCertificateFilterTest,RegionalMtlsStartupGuardTest test
./mvnw verify

docker compose --env-file /opt/regional/config/.env \
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

# No client certificate: API request must fail.
openssl s_client -connect REGIONAL_PRIVATE_DNS:9092 \
  -servername REGIONAL_PRIVATE_DNS -tls1_3 </dev/null

# With valid, approved SIXPAY client certificate:
curl --fail-with-body --cacert SERVER_CA.pem \
  --cert SIXPAY_CLIENT.pem --key SIXPAY_CLIENT.key \
  https://REGIONAL_PRIVATE_DNS:9092/actuator/health
```

Also check a valid CA-signed but unapproved client cert -> HTTP 403;
untrusted/expired cert -> rejected; plain HTTP -> rejected; and
REGIONAL_MTLS_REQUIRED=true + ENABLE_MTLS=false -> startup rejected.
Unit tests and Docker config validation do **not** establish a real mTLS
handshake or bank network-policy compliance.

## Residual decisions

- Bank/security ratification of the aligned Regional OpenAPI and SIXPAY contracts.
- PKI chain, trust roots, revocation, private DNS, firewalld and renewal.
- No SIXPAY outbound client is changed by this lot.
