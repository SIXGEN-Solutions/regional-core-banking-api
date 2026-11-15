# Regional Core Banking API — Contabo AlmaLinux MVP runbook

**State:** PREPARATION ONLY / NO VPS EXECUTION / MOCK ONLY. **Reference:** Regional `feat/payment-orchestration-worflow @ bdb928486ce086a1b0a9e158fdd55c74b783f7f9`. SIXPAY master runbook 2026-11-12 is `SIXPAY_REFERENCE / REFERENCE_ONLY`, not Regional deployment authority.

## 0. Change-control gates

Do not run these installation steps without VPS operator authorization, approved runtime image digest, validated VPS resources, assigned Regional DNS/TLS route, agreed credentials and assessment of existing SIXPAY containers/network. A successful mock payment is NOT real banking readiness. OAuth2 Client Credentials + mTLS enforcement is a separate security approval prerequisite before exposing Regional financial routes to other systems; the current Maven dependencies do not demonstrate an enforcing resource server.

## 1. Read-only inventory: AlmaLinux VPS

Connect with the existing `sixpayadmin` identity (SSH key), no new branch or deployment user assumed. Record output without secrets:

```bash
hostnamectl; uptime; getenforce; free -h; df -hT; lsblk -f
sudo systemctl is-active docker caddy firewalld
sudo firewall-cmd --list-all
sudo ss -lntup
sudo docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
sudo docker network ls
sudo docker compose version
id sixpayadmin
sudo df -i /var/lib /opt
```

Sizing **provisional** until `free`, CPU, disk I/O, existing SIXPAY loads checked. For a shared VPS with Oracle Free + PostgreSQL + Spring + SIXPAY, target at least ~12 GiB available RAM (16+ GiB total preferred) and ~80-120 GiB free SSD initially; Oracle and actual workloads determine capacity. Stop if unavailable. Do not claim this is a bank-approved capacity plan.

## 2. Directory and access plan (operator commands, not executed)

Use separate paths and Compose project; do not reuse `/opt/sixpay` or `/var/lib/sixpay`. The host operator keeps `/opt/regional` root-owned; `sixpayadmin` uses sudo only for approved operations. Docker access is effectively root-equivalent. Restrict secret *contents* to root, do not add users to `docker` group.

```bash
sudo install -d -o root -g root -m 0750 /opt/regional /opt/regional/compose /opt/regional/config /opt/regional/bootstrap /opt/regional/bootstrap/oracle /opt/regional/bootstrap/postgres
sudo install -d -o root -g root -m 0700 /opt/regional/secrets
sudo install -d -o root -g root -m 0750 /var/lib/regional /var/lib/regional/postgres /var/lib/regional/oracle
sudo install -d -o 999 -g 999 -m 0700 /var/lib/regional/postgres/data
# Oracle volume UID/GID must be confirmed from the pinned image before chown.
sudo install -d -o root -g root -m 0750 /var/lib/regional/oracle/data
sudo ls -ldZ /opt/regional /opt/regional/secrets /var/lib/regional/postgres/data /var/lib/regional/oracle/data
```

**Warning:** Oracle container UID/GID, SELinux MCS/file contexts and `:Z` behavior must be checked on this host before starting; do not recursively chown or relabel SIXPAY directories. SELinux remains Enforcing. Resolve any AVC without `setenforce 0`.

## 3. Stage immutable inputs

On the operator's local clone **at the verified revision**, apply the Python script, review `git diff`, then publish only through a separately approved image workflow. Transfer the three deployment files and the two *existing* Oracle SQL fixtures and the existing PostgreSQL bootstrap SQL using controlled file transfer, for example:

```bash
sudo install -o root -g root -m 0644 compose.yml /opt/regional/compose/compose.yml
sudo install -o root -g root -m 0644 application-production.yml /opt/regional/config/application-production.yml
sudo install -o root -g root -m 0644 .env.example /opt/regional/config/.env.example
# Copy repository docker/customer-oracle/{001_customer_schema.sql,002_payment_schema.sql}
# to /opt/regional/bootstrap/oracle/ with root:root 0644
# Copy repository docker/postgres/001_create_core_banking_schema.sql
# to /opt/regional/bootstrap/postgres/ with root:root 0644
```

Copy `.env.example` to `/opt/regional/config/.env` using an approved editor, set `0600` or `0640` root-owned; choose VERIFIED immutable image digests for API, PostgreSQL and Oracle (example tags in the template are not immutable). Never use an unverified `latest`. No configuration change requires image rebuild; restart/recreate only affected service to load new environment or config.

## 4. Secrets (host only; never commit)

Create files under `/opt/regional/secrets` root:root 0600, with no trailing newline when relevant. **Do not use `echo` with a credential, do not paste actual values in GitHub/chat/history.** Generate/store credentials via a controlled vault or non-echoing privileged editor. Required file basenames:

```text
REGIONAL_TECHNICAL_DB_PASSWORD
ORACLE_APP_PASSWORD
REGIONAL_OTP_HMAC_KEYS
REGIONAL_OTP_EMAIL_USERNAME
REGIONAL_OTP_EMAIL_PASSWORD
REGIONAL_OTP_SMS_USERNAME
REGIONAL_OTP_SMS_PASSWORD
```

For disabled SMS/EMAIL, create empty protected files for their credentials. HMAC format is `v1:<base64-of-at-least-32-random-bytes>`; align active version with `.env`. Keep earlier HMAC versions until replay/verification retention requirements are resolved. Mounting secrets via Compose file references avoids placing contents in `.env` or ordinary `docker inspect` environment output; protection from host root/Docker administrators is not possible. Because the current API image runs Java without a nonroot `USER`, hardening UID is a separate tested image change, not asserted here.

**Configuration precedence:** `SPRING_CONFIG_LOCATION` loads only `/etc/regional/application-production.yml` rather than packaged `application.yml` and `application-docker.yml`; `spring.config.import=configtree:/run/secrets/` resolves secret filenames as Spring configuration keys. Validate this bootstrap locally before VPS GO. One credential cannot safely be rotated by changing just a file if it is also used by a database user; coordinate database-side rotation.

## 5. Deployment and checks — only after approval

Before running Compose, confirm `.env`, SQL files, all seven secret paths exist; Oracle mock must have its volume ownership validated. Check image provenance and free memory/disk. `docker compose config` may expose interpolated variables; do not share raw output.

```bash
sudo docker compose --project-directory /opt/regional/compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --project-directory /opt/regional/compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml pull
sudo docker compose --project-directory /opt/regional/compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml up -d
sudo docker compose --project-directory /opt/regional/compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps
curl --noproxy '*' -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9093/actuator/health
sudo docker compose --project-directory /opt/regional/compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=60 regional-core-banking-api
```

**Check `POSTGRES_PASSWORD_FILE`, `APP_USER_PASSWORD_FILE` secret readable by each entrypoint.** Initial Oracle SQL files execute only on fresh volume initialization; they do not migrate existing databases. PostgreSQL schema bootstrap is one-time; Flyway will apply committed migration scripts at startup. Review migrations before pointing at non-disposable data. No `down -v`, `flyway clean`, bulk SQL recreation or unauthorized rollback of payment state.

## 6. HTTPS, Caddy and connectivity

API bind is **127.0.0.1:9093** only. No public PostgreSQL/Oracle port. Caddy on the host may proxy a **separately approved** Regional hostname to this loopback, with explicit method/path allowlist, OAuth2 + mTLS security design, request size limits, access logging redaction, TLS and firewall review. Do **not** automatically change `/etc/caddy/Caddyfile` nor reuse SIXPAY `api.sixgensol.com`; no hostname, Regional JWT issuer/audience/scopes or certificate identity has been approved for this deployment. Avoid opening banking routes to the Internet pending authorization design. Validate `caddy validate`, SELinux `httpd_can_network_connect`, DNS/TLS, then remote access only when approved.

## 7. Mock E2E / failure / rollback

Perform a controlled payment **only with synthetic Oracle fixtures and a test client** according to the existing `documentation/runbooks/LOCAL_DOCKER_COMPOSE_RUNBOOK.md` and `PAYMENT_EXECUTION_CONTROLLED_ACTIVATION.md`. Before calling the Regional HTTP payment event operation, verify authentication policy, beneficiary/debtor fixture, exact approved request shape, unique idempotency key, and safe lookup of an UNKNOWN result. Check Oracle BKEVE and balances through approved tests; do not infer bank posting from API `200` or PostgreSQL alone. No automatic retry on unknown outcome.

After changing `.env` or application config, use a targeted `up -d --no-deps --force-recreate regional-core-banking-api` (with the full Compose flags above) after a change review; avoid rolling back technical DB or Oracle data. Restore a prior API image digest/config only under approved change, preserving database volumes and authoritative results. If a mock payment fails, freeze retries and inspect authoritative lookup before any next action.

## 8. Proof checklist / unresolved approvals

Record: exact Git SHA + OCI digest, image signatures/digests, `docker compose config --quiet`, containers health, loopback API health 200, PostgreSQL migration versions, Oracle synthetic-fixture presence, mock payment reference + lookup outcome (without PII/OTP), relevant audit references, restore rehearsal. **No tests or VPS commands have been run by this runbook.** Pending: VPS capacity and Oracle UID/SELinux, Regional external DNS/TLS, Regional OAuth2/mTLS enforcement, network exposure approval, database backups/restore RPO/RTO, SMTP/SMS secret and routes, all three images' verified digests and a controlled mock payment E2E.
