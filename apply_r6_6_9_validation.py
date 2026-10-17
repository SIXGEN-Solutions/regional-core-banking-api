#!/usr/bin/env python3
"""R6.6.9 offline validation preparation. Never runs Maven, modifies Git refs, or checks worktree cleanliness."""
import argparse
import subprocess
from pathlib import Path

BRANCH = 'feat/payment-orchestration-worflow'
SHA = 'ccd58a4585b4b01017baf28760891e705ce311e4'
REPORT = r'''# R6.6.9 — Validation R6 Payment Execution / Recovery

**Status: VALIDATION_PLAN_PREPARED / FINANCIAL_GO_BLOCKED.**

## Revision and evidence
- Repository: `SIXGEN-Solutions/regional-core-banking-api`.
- Requested/observed GitHub branch: `feat/payment-orchestration-worflow`.
- Requested/observed SHA: `ccd58a4585b4b01017baf28760891e705ce311e4`.
- Mode: GitHub read-only assessment; local offline patch script; no Maven gates run by author.
- Consumer repository `SIXGEN-Solutions/sixpay-connect`: `SIXPAY_REFERENCE / REFERENCE_ONLY`, not modified.
- Reviewed: Regional governance, handoff, architecture, plan, manifest, approved Regional OpenAPI, SIXPAY posting architecture, R6.6.1 analysis/gap/mapping, README and .gitignore, R6.6.5–R6.6.8 preparation, JDBC and recovery sources.
- GitHub Actions run `39075656987` reported `completed/success`; this only establishes the CI workflow passed, **not** Informix integration nor banking approval.

## R6.6.8 closure assessment

`PaymentExecutionRecoveryCoordinator` is wired into service recovery lookups and avoids calling `execute`. For local `UNKNOWN` it seeks a bank result, validates payment reference where available, and requires a nonblank bank reference for bank `COMPLETED`. The technical PostgreSQL `executeIdempotent` claim is retained after an exception and prevents execution under an already-claimed key. However:

1. `JdbcPaymentExecutionBankingAdapter.findByPaymentReference` and `findByIdempotencyKey` still return `Optional.empty()`; authoritative Informix recovery is not implemented.
2. After an execution exception, PostgreSQL can store `outcome='UNKNOWN'` with `result_json IS NULL`; both persistence lookup methods deserialize **only** `result_json`, so `UNKNOWN` is not surfaced on these lookup paths, despite a durable non-replay claim. The recovery endpoint can therefore report NotFound while the claim remains unresolved. This must not be interpreted as proof of non-posting.
3. OPE/EVE/DCO correlation is not durably populated by the banking adapter, and neither bank-reference semantics nor no-effect evidence are approved.
4. An `UNKNOWN` banking lookup currently raises `PaymentExecutionUnavailableException` rather than returning the local `UNKNOWN` result. Confirm desired approved HTTP behavior; do not silently alter a canonical contract.

**Conclusion: R6.6.8 preparatory recovery is implemented, but cannot be production-closed.**

## R6.6.9 validation matrix

| ID | Layer | Scenario | Existing/prepared evidence | Gate / blocker |
|---|---|---|---|---|
| VAL-01 | Contract | Approved Regional OpenAPI, existing Payment API paths, status/error mapping | Contract guard in CI and Payment API tests | Verify consumer wire contract separately; SIXPAY reference-only |
| VAL-02 | Unit | 233 column binding, day/night selection | `BkeveJdbcParameterMapperTest`, `BkeveJdbcPostingAdapterTest` | Not a real bank schema test |
| VAL-03 | Unit | EVE numbering input format, lock and single-JVM concurrency | `BkeveEventNumberAllocationServiceTest`, `BkeveTransactionScopedAllocationServiceTest`, `JdbcBkeveTransactionCoordinatorTest` | No cross-process or external Amplitude producer coverage |
| VAL-04 | Unit | INSERT + balance order and rollback | `BkevePostingAndBalanceWriteTest` | Mockito, not Informix; unknown approved balance SQL |
| VAL-05 | Unit | UNKNOWN lookup never triggers execute; reference mismatch | `PaymentExecutionRecoveryCoordinatorTest`, `PaymentExecutionServiceTest` | Banking lookup implementation unavailable |
| VAL-06 | Unit | Repeat of uncertain payment with same idempotency key never re-executes, including concurrent callers | `R669PaymentNonReplayValidationTest` (this patch) | In-memory and mock only |
| VAL-07 | PostgreSQL IT | Claim survives exception; same key replay blocked; fingerprint conflict; reference/key lookups | `JdbcPaymentExecutionPersistenceAdapterTest` | Requires **dedicated disposable** `regional_core_banking_test` DB; confirm isolated environment and schema |
| VAL-08 | PostgreSQL IT | Assert `UNKNOWN` lookup after null result JSON; claim remains protected | Residual anomaly; expected failure until separately approved correction | R6.6.8 not closed |
| VAL-09 | Informix IT | Approved EVE allocation and unique numbering against other bank producers | No real bank allocator available | BLOCKED_BANK_EVIDENCE |
| VAL-10 | Informix IT | Physical 233-field BKEVE/BKEVE_EOD INSERT, JDBC types, nullability and character padding | No approved INSERT supplied | BLOCKED_BANK_EVIDENCE |
| VAL-11 | Informix IT | Indicative balances within BKEVE transaction; rollback after failed balance update | No approved balance mutation supplied | BLOCKED_BANK_EVIDENCE |
| VAL-12 | Failure IT | Connection lost at commit, restart, authoritative lookup, no second financial posting | Coordinator and recovery mock tests only | BLOCKED_BANK_EVIDENCE |
| VAL-13 | Operations | Production single JVM assumption and restriction of other Amplitude producers | ReentrantLock is JVM-local | Infrastructure and bank confirmation required |

## Residual anomaly register (do not silently close)

| ID | Severity | Finding | Required authority |
|---|---|---|---|
| R669-01 | BLOCKER | No bank-approved cross-producer EVE uniqueness or authoritative allocation; JVM lock insufficient | La Régionale banking evidence / architecture |
| R669-02 | BLOCKER | R6.6.6 physical bank INSERT and complete positional JDBC mapping not approved | La Régionale banking evidence |
| R669-03 | BLOCKER | R6.6.7 bank indicative balance SQL, transaction effects and real rollback unknown | La Régionale banking evidence |
| R669-04 | BLOCKER | Informix execution and recovery still fail-closed (`Optional.empty`) | La Régionale banking evidence / integration |
| R669-05 | HIGH | PostgreSQL `UNKNOWN` claim with null result JSON cannot be read as `UNKNOWN` by current recovery endpoints | Regional architecture / existing contract review |
| R669-06 | HIGH | OPE/EVE/DCO durable correlation and bank reference source unimplemented | Regional architecture + bank evidence |
| R669-07 | HIGH | Consumer wire compatibility and real database transaction/failure injection not demonstrated | Consumer compatibility verification / infrastructure |
| R669-08 | MEDIUM | Mockito self-attaching agent warning in recent local tests; not causal to JDBC mock failures | Test/toolchain decision |

## User-run validation commands (NOT executed by the patch)

Focused, nondestructive unit tests:

```bash
./mvnw -Dtest=R669PaymentNonReplayValidationTest,PaymentExecutionRecoveryCoordinatorTest,PaymentExecutionServiceTest test
./mvnw -Dtest=JdbcBkeveTransactionCoordinatorTest,BkevePostingAndBalanceWriteTest,BkeveJdbcPostingAdapterTest,BkeveJdbcParameterMapperTest test
./mvnw clean verify
```

Optional PostgreSQL integration test: start **only a dedicated disposable test database** and configure `REGIONAL_CONFIRMATION_IT_DB_URL` to `jdbc:postgresql://.../regional_core_banking_test` with test-only credentials. Then run:

```bash
./mvnw -Dtest=JdbcPaymentExecutionPersistenceAdapterTest test
```

The PostgreSQL test class runs destructive DDL/DELETE, and its `@BeforeEach` guard rejects non-test database names. Do not point it at any production, staging, shared or runtime database. No Informix integration commands are provided until La Régionale validates a disposable test environment and authorized SQL.

Final local review commands:

```bash
git diff --check
git status --short
```

## Final decision

- Static source/code review: `REPOSITORY_VALIDATED` at requested SHA.
- GitHub CI observed: `SUCCESS` for run 39075656987, not independently re-executed.
- Unit/concurrency/failure tests added by this patch: `PREPARED / NOT_EXECUTED`.
- PostgreSQL, Informix, end-to-end and destructive tests: `NOT_EXECUTED`.
- Financial authorization/release: **NO-GO** pending R669 blockers and explicit bank review.
'''
TEST = r'''package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import com.regional.corebanking.payment.application.port.out.PaymentExecutionBankingPort;
import com.regional.corebanking.payment.domain.*;
import com.regional.corebanking.payment.infrastructure.persistence.InMemoryPaymentExecutionPersistenceAdapter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** R6.6.9: JVM-only non-replay validation. NOT a PostgreSQL or Informix integration test. */
class R669PaymentNonReplayValidationTest {
    private static PaymentExecutionCommand command() {
        return new PaymentExecutionCommand("P-R669", "v1",
                new PaymentProviderEvent("P-R669", "001", "XAF", "PAYMENT", "SYSTEM",
                        "00001-12345678901-42", "00001-12345678902-40",
                        new BigDecimal("1000"), new BigDecimal("990"), "R669 test",
                        "00001", "00001", "test-marker"),
                OffsetDateTime.parse("2026-10-17T12:00:00Z"));
    }

    private static PaymentExecutionBankingPort failingBank(AtomicInteger attempts) {
        return new PaymentExecutionBankingPort() {
            @Override public PaymentExecutionResult execute(String institution, String key, PaymentExecutionCommand cmd) {
                attempts.incrementAndGet();
                throw new PaymentExecutionUnavailableException("Simulated uncertain banking outcome");
            }
            @Override public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String ref) {
                return Optional.empty();
            }
            @Override public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
                return Optional.empty();
            }
        };
    }

    @Test void uncertainExecutionIsNotInvokedTwiceForSameKey() {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669", command()));
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669", command()));
        assertEquals(1, attempts.get(), "An unresolved banking effect must not be replayed");
    }

    @Test void concurrentSameKeyDoesNotInvokeBankTwiceAfterUnknown() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < 32; i++) {
                calls.add(() -> {
                    try { service.execute("REGIONAL", "idem-R669-concurrent", command()); return false; }
                    catch (PaymentExecutionUnavailableException expected) { return true; }
                });
            }
            for (Future<Boolean> result : workers.invokeAll(calls)) assertTrue(result.get());
            assertEquals(1, attempts.get(), "Concurrency must not duplicate a financial operation");
        } finally {
            workers.shutdownNow();
        }
    }

    @Test void unresolvedLookupCannotTriggerAnotherBankExecution() {
        AtomicInteger attempts = new AtomicInteger();
        var service = new PaymentExecutionService(failingBank(attempts), new InMemoryPaymentExecutionPersistenceAdapter());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669-lookup", command()));
        // No authoritative bank record; technical claim still prevents retry.
        assertThrows(RuntimeException.class,
                () -> service.recoverByIdempotencyKey("REGIONAL", "idem-R669-lookup"));
        assertEquals(1, attempts.get());
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> service.execute("REGIONAL", "idem-R669-lookup", command()));
        assertEquals(1, attempts.get());
    }
}
'''
FILES = {
 'documentation/validation/R6_6_9_VALIDATION_REPORT.md': REPORT,
 'src/test/java/com/regional/corebanking/payment/application/service/R669PaymentNonReplayValidationTest.java': TEST,
}

def git(*args):
    return subprocess.check_output(['git', *args], text=True, stderr=subprocess.STDOUT).strip()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apply',action='store_true',help='Write the two new files, never run tests or commit')
    args=parser.parse_args()
    root=Path.cwd()
    try:
        observed_branch=git('branch','--show-current')
        observed_sha=git('rev-parse','HEAD')
        top=Path(git('rev-parse','--show-toplevel')).resolve()
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        raise SystemExit(f'Cannot verify Git revision: {exc}')
    if top != root.resolve():
        raise SystemExit('Run this script from the repository root.')
    if observed_branch != BRANCH or observed_sha != SHA:
        raise SystemExit(f'BLOCKED: expected {BRANCH} @ {SHA}; found {observed_branch} @ {observed_sha}')
    for name in FILES:
        if (root/name).exists():
            raise SystemExit(f'BLOCKED: will not overwrite existing file {name}')
    print(f'VALIDATED: {observed_branch} @ {observed_sha}')
    for name,content in FILES.items():
        print(f'ADD {name} ({len(content.encode())} bytes)')
    if not args.apply:
        print('DRY RUN: no files written. Use --apply to create the files.')
        return
    for name,content in FILES.items():
        file=root/name
        file.parent.mkdir(parents=True,exist_ok=True)
        with file.open('x',encoding='utf-8',newline='\n') as out:
            out.write(content)
    print('APPLIED. No gates, worktree-cleanliness checks, commits or pushes performed.')

if __name__=='__main__':
    main()
