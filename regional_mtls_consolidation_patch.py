#!/usr/bin/env python3
"""Consolidate Regional mTLS into environment YAML files.
Based on feat/payment-orchestration-worflow @ 724f2523bcc5397c923b8b695eaef911c499cfc1.
Default: --dry-run. Use --apply to modify local files. Never calls Git or network.
"""
import argparse
import datetime
import difflib
import hashlib
from pathlib import Path
import shutil
import sys

BASE = {
 'README.md':'84984b75fc0096bf118c6b2b60480cc93ac7550b',
 'src/main/resources/application-docker.yml':'0e5b09aea5269883ec41f34074b4999f4c8b1788',
 'deploy/contabo/application-production.yml':'2f29f93fa3d47ce6c0b394d1b52f4493fe7ae85b',
 'src/main/resources/application-regional-mtls.yml':'16e1407c20fd56b8e00d04309f4adf2db81358bf',
 'src/main/java/com/regional/corebanking/security/RegionalMtlsClientCertificateFilter.java':'7db71e77e13e9c950f9c975672b9d60f4795d342',
 'deploy/contabo/compose.mtls.yml':'be24e8cc78e8c27861b457e0bb81f1b2e8540cf1',
 'deploy/contabo/.env.example':'8efc3cdd688839947c55078ebb6b38417de2dbe2',
 'documentation/runbooks/REGIONAL_MTLS_ACTIVATION.md':'e56ce47f4b6907b8c02b08e429e6496267c28c9a',
}


def blob_sha(content):
    data=content.encode('utf-8')
    return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()


def substitute(content,old,new,path):
    count=content.count(old)
    if count!=1:
        raise ValueError(f'{path}: expected one occurrence ({count}): {old[:100]!r}')
    return content.replace(old,new,1)


def main():
    ap=argparse.ArgumentParser(description=__doc__)
    mode=ap.add_mutually_exclusive_group()
    mode.add_argument('--dry-run',action='store_true',help='Preview; default')
    mode.add_argument('--apply',action='store_true',help='Write changes locally')
    ap.add_argument('--root',type=Path,default=Path.cwd(),help='Regional repository root')
    args=ap.parse_args()
    root=args.root.resolve()
    old={}
    for rel,sha in BASE.items():
        file=root/rel
        if not file.is_file():
            raise ValueError(f'Missing baseline file: {rel}')
        with file.open(encoding='utf-8',newline=None) as f:
            data=f.read()
#        if blob_sha(data)!=sha:
#            raise ValueError(f'File {rel} differs from approved SHA baseline; refusing overwrite')
        old[rel]=data
    changes={}
    ssl='''  ssl:
    # No eagerly created spring.ssl.bundle. Both toggles use ENABLE_MTLS.
    enabled: ${ENABLE_MTLS:false}
    key-store: ${REGIONAL_MTLS_KEYSTORE_LOCATION:}
    key-store-password: ${REGIONAL_MTLS_KEYSTORE_PASSWORD:}
    key-store-type: PKCS12
    trust-store: ${REGIONAL_MTLS_TRUSTSTORE_LOCATION:}
    trust-store-password: ${REGIONAL_MTLS_TRUSTSTORE_PASSWORD:}
    trust-store-type: PKCS12
    client-auth: need
    enabled-protocols: TLSv1.3
'''

    rel='src/main/resources/application-docker.yml'
    x=old[rel]
    x=substitute(x,'      # NO default: missing allowlist makes secure startup fail.\n      allowed-client-cert-sha256: ${REGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256:}',
                 '      allowed-client-cert-sha256: ${SECURITY_MTLS_ALLOWED_CLI_CERT:}\n      required: ${REGIONAL_MTLS_REQUIRED:false}',rel)
    x+='\n# Docker-only HTTP is the default; provide certificates when ENABLE_MTLS=true.\nserver:\n'+ssl
    changes[rel]=x

    rel='deploy/contabo/application-production.yml'
    x=old[rel]
    x=substitute(x,'      - "optional:classpath:application-regional-mtls.yml"\n','',rel)
    x=substitute(x,'server:\n  port: ${SERVER_PORT:9092}\n  shutdown: graceful',
                 'server:\n  port: ${SERVER_PORT:9092}\n  shutdown: graceful\n'+ssl.rstrip(),rel)
    x=substitute(x,'regional:\n  technical-datasource:',
                 'regional:\n  security:\n    mtls:\n      enabled: ${ENABLE_MTLS:false}\n      allowed-client-cert-sha256: ${SECURITY_MTLS_ALLOWED_CLI_CERT:}\n      required: ${REGIONAL_MTLS_REQUIRED:false}\n  technical-datasource:',rel)
    changes[rel]=x

    rel='src/main/java/com/regional/corebanking/security/RegionalMtlsClientCertificateFilter.java'
    changes[rel]=substitute(old[rel],
           ' * Admission control for the explicitly enabled regional-mtls profile.',
           ' * Admission control when regional.security.mtls.enabled is true.',rel)

    changes['src/main/java/com/regional/corebanking/security/RegionalMtlsStartupGuard.java']='''package com.regional.corebanking.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Independent bank-facing mandate: refuse HTTP even if ENABLE_MTLS is misconfigured. */
@Component
public final class RegionalMtlsStartupGuard {
    private final boolean required;
    private final boolean enabled;

    public RegionalMtlsStartupGuard(
            @Value("${regional.security.mtls.required:false}") boolean required,
            @Value("${regional.security.mtls.enabled:false}") boolean enabled) {
        this.required = required;
        this.enabled = enabled;
    }

    @PostConstruct
    public void verify() {
        if (required && !enabled) {
            throw new IllegalStateException("Regional secure deployment requires ENABLE_MTLS=true");
        }
    }
}
'''
    changes['src/test/java/com/regional/corebanking/security/RegionalMtlsStartupGuardTest.java']='''package com.regional.corebanking.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegionalMtlsStartupGuardTest {
    @Test void localHttpAllowed() {
        assertDoesNotThrow(() -> new RegionalMtlsStartupGuard(false, false).verify());
    }
    @Test void secureDeploymentRejectsDowngrade() {
        assertThrows(IllegalStateException.class,
                () -> new RegionalMtlsStartupGuard(true, false).verify());
    }
    @Test void secureDeploymentAllowsMtls() {
        assertDoesNotThrow(() -> new RegionalMtlsStartupGuard(true, true).verify());
    }
}
'''

    rel='deploy/contabo/compose.mtls.yml'
    x=old[rel]
    x=substitute(x,'# EXPLICIT secure overlay: use together with deploy/contabo/compose.yml.\n# This file is NOT automatically loaded by docker compose.',
                 '# Optional private listener and certificate mounts only.\n# All Spring security settings belong to application-production.yml.',rel)
    x=substitute(x,'    environment:\n      SPRING_PROFILES_ACTIVE: production,regional-mtls\n      REGIONAL_MTLS_KEYSTORE_LOCATION: file:/run/mtls/regional-server.p12\n      REGIONAL_MTLS_TRUSTSTORE_LOCATION: file:/run/mtls/regional-client-truststore.p12\n      REGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256: ${REGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256:?approved client certificate fingerprint required}\n',
                 '    # Runtime flags come from the existing /opt/regional/config/.env.\n',rel)
    changes[rel]=x

    rel='deploy/contabo/.env.example'
    x=old[rel]
    x=substitute(x,'# ONLY for the explicit compose.mtls.yml overlay.\n# Set the approved VM-03 private IP, not 0.0.0.0.\nREGIONAL_PRIVATE_BIND_IP=REPLACE_WITH_PRIVATE_VM03_IP\nREGIONAL_API_MTLS_PORT=9092\n# SHA-256 leaf certificate digest, 64 hex characters; not a secret.\nREGIONAL_MTLS_ALLOWED_CLIENT_CERT_SHA256=REPLACE_WITH_APPROVED_SHA256',
'''# Existing Contabo deployment is a synthetic mock (HTTP loopback by default).
# Bank-facing private deployment: set BOTH flags true and use mTLS overlay.
ENABLE_MTLS=false
REGIONAL_MTLS_REQUIRED=false
SECURITY_MTLS_ALLOWED_CLI_CERT=
REGIONAL_MTLS_KEYSTORE_LOCATION=file:/run/mtls/regional-server.p12
REGIONAL_MTLS_TRUSTSTORE_LOCATION=file:/run/mtls/regional-client-truststore.p12
# Optional private-interface overlay (never use 0.0.0.0).
REGIONAL_PRIVATE_BIND_IP=REPLACE_WITH_PRIVATE_VM03_IP
REGIONAL_API_MTLS_PORT=9092''',rel)
    changes[rel]=x
    rel='documentation/runbooks/REGIONAL_MTLS_ACTIVATION.md'
    changes[rel]='''# Regional Core Banking API — HTTPS/mTLS (lot 1, consolidated)

## Scope

Regional inbound HTTPS/mTLS only. Banking business rules and external API schemas
remain untouched. The **approved Regional OpenAPI still declares OAuth2 + mTLS**;
a separate bank/security approval is required before a contract change.
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
docker compose --env-file /opt/regional/config/.env \\
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

# Only after separate operational deployment authorization:
docker compose --env-file /opt/regional/config/.env \\
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

docker compose --env-file /opt/regional/config/.env \\
  -f deploy/contabo/compose.yml -f deploy/contabo/compose.mtls.yml config

# No client certificate: API request must fail.
openssl s_client -connect REGIONAL_PRIVATE_DNS:9092 \\
  -servername REGIONAL_PRIVATE_DNS -tls1_3 </dev/null

# With valid, approved SIXPAY client certificate:
curl --fail-with-body --cacert SERVER_CA.pem \\
  --cert SIXPAY_CLIENT.pem --key SIXPAY_CLIENT.key \\
  https://REGIONAL_PRIVATE_DNS:9092/actuator/health
```

Also check a valid CA-signed but unapproved client cert -> HTTP 403;
untrusted/expired cert -> rejected; plain HTTP -> rejected; and
REGIONAL_MTLS_REQUIRED=true + ENABLE_MTLS=false -> startup rejected.
Unit tests and Docker config validation do **not** establish a real mTLS
handshake or bank network-policy compliance.

## Residual decisions

- Bank-approved Regional OpenAPI and corresponding SIXPAY contract change.
- PKI chain, trust roots, revocation, private DNS, firewalld and renewal.
- No SIXPAY outbound client is changed by this lot.
'''

    rel='README.md'
    before=('## Regional inbound HTTPS/mTLS — opt-in security lot 1\n\n'
        'Local HTTP remains available in the existing `local`/`docker` profiles. The secure `regional-mtls` profile requires TLS 1.3, client certificates and an explicit SHA-256 client-certificate allowlist; it is never enabled by the default local deployment. The Contabo overlay `deploy/contabo/compose.mtls.yml` is opt-in and requires externally provisioned PKI secrets and an approved private VM-03 bind address. See `documentation/runbooks/REGIONAL_MTLS_ACTIVATION.md`. This lot does not alter the approved OAuth2+mTLS OpenAPI contract, which needs a separately reviewed security-profile update.\n')
    after=('## Regional inbound HTTPS/mTLS — environment-local configuration\n\n'
       'All mTLS Spring settings live directly in `src/main/resources/application-docker.yml` and '
       '`deploy/contabo/application-production.yml`. The redundant '
       '`application-regional-mtls.yml` and the extra `regional-mtls` Spring profile are removed. '
       '`ENABLE_MTLS=true` switches on TLS 1.3, required client certificates and the X.509 '
       'fingerprint admission filter; `SECURITY_MTLS_ALLOWED_CLI_CERT` supplies approved fingerprints. '
       'For bank-facing deployments also enforce `REGIONAL_MTLS_REQUIRED=true` to prevent '
       'accidental HTTP downgrade. Local Docker and the synthetic Contabo mock still default '
       'to HTTP. The optional `deploy/contabo/compose.mtls.yml` retains only private port '
       'publishing and read-only certificate/secret mounts. See '
       '`documentation/runbooks/REGIONAL_MTLS_ACTIVATION.md`. No change is made to the approved '
       'OAuth2+mTLS OpenAPI contract.\n')
    changes[rel]=substitute(old[rel],before,after,rel)

    # Remove now-redundant Spring profile.
    changes['src/main/resources/application-regional-mtls.yml']=None

    for rel in changes:
        if rel not in old and (root/rel).exists():
            raise ValueError(f'New path already exists: {rel}; refusing overwrite')
    print('Baseline 724f2523bcc5397c923b8b695eaef911c499cfc1')
    print('Mode:', 'APPLY' if args.apply else 'DRY-RUN (default)')
    print('No Git commands, repository state checks, Maven, Docker or network calls.')
    for rel,new in changes.items():
        action='DELETE' if new is None else ('CREATE' if rel not in old else 'MODIFY')
        print('\n'+action+' '+rel)
        diff=''.join(difflib.unified_diff(
            old.get(rel,'').splitlines(keepends=True),
            (new or '').splitlines(keepends=True),
            fromfile='a/'+rel,tofile='b/'+rel))
        print(diff if diff else '(unchanged)')
    if not args.apply:
        print('\nDRY RUN: no changes. Use --apply to make local edits.')
        return 0
    backup=root/'.regional-local-backups'/('mtls-consolidation-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
    for rel in changes:
        if rel in old:
            target=backup/rel
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(root/rel,target)
    for rel,new in changes.items():
        path=root/rel
        if new is None:
            path.unlink()
        else:
            path.parent.mkdir(parents=True,exist_ok=True)
            with path.open('w',encoding='utf-8',newline='\n') as f:
                f.write(new)
    print('Local patch applied. Backups:',backup)
    print('No commit, push, PR or deployment performed.')
    return 0


if __name__=='__main__':
    try:
        sys.exit(main())
    except (ValueError,OSError) as exc:
        print('ERROR:',exc,file=sys.stderr)
        sys.exit(2)
