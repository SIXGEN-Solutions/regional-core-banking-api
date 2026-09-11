from pathlib import Path
import subprocess

ROOT = Path.cwd()
EXPECTED_HEAD = "58bed4015a63af1981fd1910b6342f58bdc0841a"
EXPECTED_BRANCH = "feat/contract-generation-ci"

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

def require(condition, message):
    if not condition:
        raise RuntimeError(message)

require((ROOT / ".git").exists(), "Run this script from the repository root.")

head = git("rev-parse", "HEAD")
branch = git("rev-parse", "--abbrev-ref", "HEAD")

require(head == EXPECTED_HEAD, f"Unexpected HEAD: {head}; expected {EXPECTED_HEAD}")
require(branch == EXPECTED_BRANCH, f"Unexpected branch: {branch}; expected {EXPECTED_BRANCH}")

dockerignore = ROOT / ".dockerignore"
dockerfile = ROOT / "Dockerfile"

require(dockerignore.exists(), "Missing .dockerignore")
require(dockerfile.exists(), "Missing Dockerfile")

dockerfile_text = dockerfile.read_text(encoding="utf-8")
require(
    "COPY target/regional-core-banking-api-*.jar app.jar" in dockerfile_text,
    "Unexpected Dockerfile COPY instruction; refusing to patch."
)

before = dockerignore.read_text(encoding="utf-8")

require(
    "target/\n" in before,
    "Expected 'target/' exclusion not found in .dockerignore."
)

after = before.replace(
    "target/\n",
    "target/*\n!target/regional-core-banking-api-*.jar\n",
    1,
)

dockerignore.write_text(after, encoding="utf-8", newline="\n")

print("UPDATED .dockerignore")
print()
print("Docker context policy:")
print("  - target contents remain ignored by default")
print("  - only target/regional-core-banking-api-*.jar is included")
print("  - Dockerfile remains unchanged")
print()
print("No Maven build, Docker build, commit, push or PR was executed.")
print()
subprocess.run(["git", "diff", "--", ".dockerignore"], cwd=ROOT, check=False)
subprocess.run(["git", "status", "--short"], cwd=ROOT, check=False)
