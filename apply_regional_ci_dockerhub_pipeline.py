#!/usr/bin/env python3
"""
Apply the GitHub Actions + Docker Hub delivery patch for:
SIXGEN-Solutions/regional-core-banking-api

Expected base:
feat/payment-orchestration-worflow @ 52d2a7671aefe12c0ee57bd0e4c6534adbaeaabd

This script:
- does NOT inspect worktree cleanliness;
- does NOT run tests/gates;
- does NOT commit/push/create a PR;
- verifies only the current HEAD before writing;
- updates CI to verify/build and push to Docker Hub on push events;
- makes local Compose pull the API image instead of building it.
"""

from pathlib import Path
import subprocess
import sys

EXPECTED_HEAD = "52d2a7671aefe12c0ee57bd0e4c6534adbaeaabd"
DOCKER_IMAGE = "d22002/regional-core-banking-api"

ROOT = Path.cwd()

def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)

def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        fail(f"Required file not found: {path}")
    return p.read_text(encoding="utf-8")

def write(path: str, content: str) -> None:
    p = ROOT / path
    p.write_text(content, encoding="utf-8", newline="\n")
    print(f"updated: {path}")

def replace_once(content: str, old: str, new: str, path: str) -> str:
    count = content.count(old)
    if count != 1:
        fail(f"{path}: expected exactly one matching block, found {count}")
    return content.replace(old, new, 1)

try:
    head = subprocess.check_output(
        ["git", "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
        stderr=subprocess.STDOUT,
    ).strip()
except Exception as exc:
    fail(f"Unable to read Git HEAD: {exc}")

if head != EXPECTED_HEAD:
    fail(
        "HEAD mismatch. "
        f"Expected {EXPECTED_HEAD}, observed {head}. "
        "No file has been modified."
    )

print(f"HEAD verified: {head}")

# ---------------------------------------------------------------------------
# 1. GitHub Actions CI
# ---------------------------------------------------------------------------
ci_path = ".github/workflows/ci.yml"
ci = read(ci_path)

ci = replace_once(
    ci,
    """permissions:
  contents: read

jobs:
""",
    """permissions:
  contents: read

env:
  DOCKER_IMAGE: d22002/regional-core-banking-api

jobs:
""",
    ci_path,
)

ci = replace_once(
    ci,
    """      - name: Docker build
        run: docker build -t regional-core-banking-api:ci .

      - name: Docker Compose config
        run: docker compose config
""",
    """      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Docker metadata
        id: docker-meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.DOCKER_IMAGE }}
          tags: |
            type=sha,format=long
            type=ref,event=branch
            type=raw,value=latest,enable=${{ github.ref == 'refs/heads/main' }}

      - name: Log in to Docker Hub
        if: github.event_name == 'push'
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and optionally push Docker image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: ${{ github.event_name == 'push' }}
          tags: ${{ steps.docker-meta.outputs.tags }}
          labels: ${{ steps.docker-meta.outputs.labels }}

      - name: Docker Compose config
        env:
          REGIONAL_OTP_EMAIL_HOST: smtp.example.invalid
          REGIONAL_OTP_EMAIL_USERNAME: ci-placeholder
          REGIONAL_OTP_EMAIL_PASSWORD: ci-placeholder
        run: docker compose config
""",
    ci_path,
)
write(ci_path, ci)

# ---------------------------------------------------------------------------
# 2. Compose: pull published API image; never build it locally
# ---------------------------------------------------------------------------
compose_path = "compose.yaml"
compose = read(compose_path)
compose = replace_once(
    compose,
    """  regional-core-banking-api:
    build:
      context: .
      args:
        APP_JAR: ${REGIONAL_APP_JAR:-target/regional-core-banking-api-1.0.0-SNAPSHOT.jar}

    image: regional-core-banking-api:local
    container_name: regional-core-banking-api
""",
    """  regional-core-banking-api:
    image: ${REGIONAL_API_IMAGE:-d22002/regional-core-banking-api:latest}
    pull_policy: always
    container_name: regional-core-banking-api
""",
    compose_path,
)
write(compose_path, compose)

# ---------------------------------------------------------------------------
# 3. .env example: expose image selection instead of local JAR selection
# ---------------------------------------------------------------------------
env_path = ".env.example"
env_text = read(env_path)
env_text = replace_once(
    env_text,
    """REGIONAL_API_PORT=8080
REGIONAL_APP_JAR=target/regional-core-banking-api-1.0.0-SNAPSHOT.jar
""",
    """REGIONAL_API_PORT=8080
# Published API image. Override with an immutable sha-* tag when needed.
REGIONAL_API_IMAGE=d22002/regional-core-banking-api:latest
""",
    env_path,
)
write(env_path, env_text)

# ---------------------------------------------------------------------------
# 4. README: align local container instructions with Docker Hub delivery
# ---------------------------------------------------------------------------
readme_path = "README.md"
readme = read(readme_path)

readme = replace_once(
    readme,
    """## Container

```bash
./mvnw package
docker build .
docker compose config
```

Environment-specific endpoints, credentials, certificates, private keys and trust material remain external configuration.
""",
    """## Container

GitHub Actions runs the Maven verification/build and builds the Docker image.
For branch pushes, the image is published to Docker Hub under
`d22002/regional-core-banking-api`; `latest` is published only from `main`.

Local Docker Compose consumes the published image and does not build the API image:

```bash
docker compose pull regional-core-banking-api
docker compose up -d
docker compose ps
```

To run a specific immutable CI image, set `REGIONAL_API_IMAGE` in the local `.env`
to the required `d22002/regional-core-banking-api:sha-<commit>` tag.

Environment-specific endpoints, credentials, certificates, private keys and trust material remain external configuration.
""",
    readme_path,
)

readme = replace_once(
    readme,
    """### Build prerequisite

This Docker increment does not execute OpenAPI generation. The Docker image
therefore consumes the already-built application JAR:

`target/regional-core-banking-api-1.0.0-SNAPSHOT.jar`

Because the Docker runtime connects to the Oracle mock, the prebuilt artifact must
contain the Oracle JDBC runtime dependency supplied by the existing
`customer-oracle-mock` Maven profile.

### Start

Optionally copy `.env.example` to `.env`, then start:

    docker compose up --build
""",
    """### Image prerequisite

The API image is built by GitHub Actions after Maven verification and published to
Docker Hub as `d22002/regional-core-banking-api`. Local Compose pulls that image;
it does not build the API container from the local workspace.

### Start

Optionally copy `.env.example` to `.env`, then pull and start:

    docker compose pull regional-core-banking-api
    docker compose up -d
""",
    readme_path,
)
write(readme_path, readme)

# ---------------------------------------------------------------------------
# 5. Local Docker runbook: remove local API image build workflow
# ---------------------------------------------------------------------------
runbook_path = "documentation/runbooks/LOCAL_DOCKER_COMPOSE_RUNBOOK.md"
runbook = read(runbook_path)

runbook = replace_once(
    runbook,
    """## Purpose
This runbook describes how to build, start, inspect and stop the local Docker Compose environment for the Regional Core Banking API. It applies to local integration only and does not define production or authoritative Amplitude/Informix settings.
""",
    """## Purpose
This runbook describes how to pull, start, inspect and stop the local Docker Compose environment for the Regional Core Banking API. The API image is published by GitHub Actions to Docker Hub and is not built by local Compose. This applies to local integration only and does not define production or authoritative Amplitude/Informix settings.
""",
    runbook_path,
)

runbook = replace_once(
    runbook,
    """- Docker Desktop running.
- Java 21 and Maven available.
- Local `.env` containing required secrets and SMTP configuration.
""",
    """- Docker Desktop running.
- Access to the Docker Hub repository `d22002/regional-core-banking-api`.
- Local `.env` containing required secrets, SMTP configuration and optionally an explicit `REGIONAL_API_IMAGE` tag.
""",
    runbook_path,
)

start = runbook.find("## 1. Build\n")
end = runbook.find("## 3. Start\n")
if start == -1 or end == -1 or end <= start:
    fail(f"{runbook_path}: unable to locate build sections")
runbook = (
    runbook[:start]
    + """## 1. Select the API image
By default, Compose uses:

```text
d22002/regional-core-banking-api:latest
```

For reproducible testing, set an immutable image tag in `.env`, for example:

```text
REGIONAL_API_IMAGE=d22002/regional-core-banking-api:sha-<commit>
```

`latest` is reserved for images published from `main`.

## 2. Pull Docker image
```powershell
docker compose pull regional-core-banking-api
```

Local Compose must not run `docker compose build` for the API service.

"""
    + runbook[end:]
)

runbook = replace_once(
    runbook,
    """Then rebuild:
```powershell
mvn clean package -DskipTests
docker compose build --no-cache regional-core-banking-api
docker compose up -d
```
""",
    """If the published image is missing the Oracle JDBC driver, do not rebuild it through
local Compose. Validate the CI artifact/build configuration, publish a corrected image,
then pull it again:

```powershell
docker compose pull regional-core-banking-api
docker compose up -d
```
""",
    runbook_path,
)

write(runbook_path, runbook)

print()
print("Patch applied successfully.")
print("No tests/gates were executed.")
print("No commit, push or PR was performed.")
print()
print("Next: configure GitHub repository secrets:")
print("  DOCKERHUB_USERNAME = d22002")
print("  DOCKERHUB_TOKEN    = <Docker Hub access token>")
