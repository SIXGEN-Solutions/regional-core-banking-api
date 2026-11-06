#!/usr/bin/env python3
"""Prepare a narrowly scoped Flyway V3 migration for regional-core-banking-api.

Does not connect to a database, run Maven/tests, inspect the worktree, commit,
change branches or push. It verifies the expected Git revision and source API
before adding exactly one new migration file.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import subprocess
import sys

EXPECTED_SHA = "fc8b6d47b04c84f4733dfea909a72420271a3012"
MIGRATION_NAME = "V3__create_payment_event_number_reservation_store.sql"
MIGRATION_CONTENT = """-- Regional-owned technical PostgreSQL persistence for Payment Event EVE reservations.
-- NOT an Amplitude / Informix / Oracle banking schema.
-- A reserved EVE is not proof that a banking event has been committed.
-- Used by JdbcPaymentEventNumberReservationAdapter.
CREATE TABLE core_banking.api_eodprocess (
    id TEXT PRIMARY KEY,
    lasteve VARCHAR(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_api_eodprocess_id_not_blank
        CHECK (length(btrim(id)) > 0),
    CONSTRAINT ck_api_eodprocess_lasteve_format
        CHECK (lasteve IS NULL OR lasteve ~ '^[0-9]{6}$'),
    CONSTRAINT ck_api_eodprocess_lasteve_positive
        CHECK (lasteve IS NULL OR lasteve <> '000000'),
    CONSTRAINT ck_api_eodprocess_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE core_banking.api_eodprocess IS
    'Technical EVE high-water mark per operation code; not financial execution evidence';
"""


def check_revision(root: Path) -> None:
    try:
        result = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            capture_output=True, text=True, check=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        raise RuntimeError("Impossible de lire la revision Git HEAD") from exc
    observed = result.stdout.strip()
    if observed != EXPECTED_SHA:
        raise RuntimeError(
            f"Revision differente: HEAD={observed or '<vide>'}, attendu={EXPECTED_SHA}. "
            "Aucune ecriture. Revoir le correctif pour cette revision."
        )


def require_sources(root: Path) -> Path:
    adapter = root / "src/main/java/com/regional/corebanking/payment/infrastructure/persistence/JdbcPaymentEventNumberReservationAdapter.java"
    config = root / "src/main/java/com/regional/corebanking/confirmation/infrastructure/TechnicalDataSourceConfiguration.java"
    migrations = root / "src/main/resources/db/migration"
    for path in (adapter, config,
                 migrations / "V1__create_confirmation_store.sql",
                 migrations / "V2__create_payment_execution_store.sql"):
        if not path.is_file():
            raise RuntimeError(f"Fichier source attendu absent: {path}")
    a = adapter.read_text(encoding="utf-8")
    required_adapter = (
        "api_eodprocess", "ON CONFLICT (id) DO NOTHING", "SELECT lasteve",
        "FOR UPDATE", "UPDATE api_eodprocess", "lasteve = ?", "version = version + 1",
    )
    missing = [s for s in required_adapter if s not in a]
    if missing:
        raise RuntimeError(f"Code de reservation different de la version auditee: {missing}")
    c = config.read_text(encoding="utf-8")
    for marker in ('@Bean(initMethod = "migrate")', '.schemas("core_banking")', '.locations("classpath:db/migration")'):
        if marker not in c:
            raise RuntimeError(f"Configuration Flyway inattendue: {marker}")
    return migrations


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd(),
                        help="Racine du repository local (defaut: dossier courant)")
    args = parser.parse_args()
    root = args.repo.resolve()
    try:
        check_revision(root)
        migration_dir = require_sources(root)
        target = migration_dir / MIGRATION_NAME
        if target.exists():
            if target.read_text(encoding="utf-8") == MIGRATION_CONTENT:
                print(f"Deja present et identique: {target}")
                return 0
            raise RuntimeError(f"Migration V3 existe avec un contenu different: {target}. Aucune ecriture.")
        existing_v3 = list(migration_dir.glob("V3__*.sql"))
        if existing_v3:
            raise RuntimeError(f"Une migration V3 existe deja: {existing_v3}. Aucune ecriture.")
        with target.open("x", encoding="utf-8", newline="\n") as file:
            file.write(MIGRATION_CONTENT)
        print(f"CREE: {target}")
        print("Migration preparee. Non appliquee en base. Aucun gate, commit ou push execute.")
        return 0
    except (RuntimeError, OSError, UnicodeError) as exc:
        print(f"ERREUR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
