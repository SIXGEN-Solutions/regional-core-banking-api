#!/usr/bin/env python3
"""R6.6.8: patch the existing Regional PostgreSQL payment recovery projection.

Dry-run by default; --apply writes only the two explicitly listed files.
Never changes branches, commits, pushes, or checks worktree cleanliness.
"""
from __future__ import annotations
import argparse
import hashlib
import pathlib
import subprocess
import sys

BRANCH = 'feat/payment-orchestration-worflow'
BASE = '81842d5c44901b69e708f2e639f2298220a63749'
MAIN = pathlib.Path('src/main/java/com/regional/corebanking/payment/infrastructure/persistence/JdbcPaymentExecutionPersistenceAdapter.java')
TEST = pathlib.Path('src/test/java/com/regional/corebanking/payment/infrastructure/persistence/JdbcPaymentExecutionPersistenceAdapterTest.java')


def git(*args):
    return subprocess.check_output(['git', *args], text=True, stderr=subprocess.STDOUT).strip()


def replace_once(source: str, old: str, new: str, where: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f'{where}: expected exactly one matching block; found {count}. No files written.')
    return source.replace(old, new, 1)


def patch_main(source: str) -> str:
    source = replace_once(source,
        'import java.nio.charset.StandardCharsets;\n',
        'import java.nio.charset.StandardCharsets;\nimport java.time.OffsetDateTime;\nimport java.time.ZoneOffset;\nimport java.util.List;\n', str(MAIN))
    old = '''    @Override
    public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String paymentReference) {
        return jdbc.query("""
                SELECT result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND payment_reference = ?
                """,
                (rs, row) -> deserialize(rs.getString(1)),
                institution, paymentReference).stream().findFirst().flatMap(Optional::ofNullable);
    }

    @Override
    public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
        return jdbc.query("""
                SELECT result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND idempotency_key = ?
                """,
                (rs, row) -> deserialize(rs.getString(1)),
                institution, key).stream().findFirst().flatMap(Optional::ofNullable);
    }
'''
    new = '''    @Override
    public Optional<PaymentExecutionResult> findByPaymentReference(String institution, String paymentReference) {
        return jdbc.query("""
                SELECT payment_reference, outcome, result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND payment_reference = ?
                """,
                (rs, row) -> recoverableResult(rs.getString(1), rs.getString(2), rs.getString(3)),
                institution, paymentReference).stream().findFirst();
    }

    @Override
    public Optional<PaymentExecutionResult> findByIdempotencyKey(String institution, String key) {
        return jdbc.query("""
                SELECT payment_reference, outcome, result_json::text
                  FROM regional_payment_execution
                 WHERE institution_code = ? AND idempotency_key = ?
                """,
                (rs, row) -> recoverableResult(rs.getString(1), rs.getString(2), rs.getString(3)),
                institution, key).stream().findFirst();
    }

    /**
     * A durable idempotency claim with no result JSON is NOT a missing payment.
     * UNKNOWN (or a still-pending NULL outcome) means the financial result has
     * not been established. This technical projection is never banking proof.
     * An inconsistent terminal state must fail closed instead of inventing it.
     */
    private PaymentExecutionResult recoverableResult(String reference, String outcome, String resultJson) {
        if (resultJson != null) {
            PaymentExecutionResult result = deserialize(resultJson);
            if (!reference.equals(result.paymentReference())) {
                throw new PaymentExecutionUnavailableException("Stored payment execution reference mismatch");
            }
            return result;
        }
        if (outcome == null || "UNKNOWN".equals(outcome)) {
            return new PaymentExecutionResult(reference,
                    com.regional.corebanking.payment.domain.PaymentExecutionOutcome.UNKNOWN,
                    List.of(), null, null, OffsetDateTime.now(ZoneOffset.UTC));
        }
        throw new PaymentExecutionUnavailableException(
                "Payment execution claim has terminal outcome without a recoverable result");
    }
'''
    return replace_once(source,old,new,str(MAIN))


def patch_test(source: str) -> str:
    old = '''    @Test
    void schemaStoresRequiredCorrelationColumnsAndLeavesBankContextNullableUntilKnown() {'''
    new = '''    @Test
    void unknownClaimWithoutJsonIsRecoverableByBothIdentifiersAndNeverReposts() {
        AtomicInteger executions = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> repository.executeIdempotent(
                        "REGIONAL", "idem-unknown", "a".repeat(64), command(),
                        () -> {
                            executions.incrementAndGet();
                            throw new IllegalStateException("simulated uncertain bank commit");
                        }));

        var row = jdbc.queryForMap("""
                SELECT outcome, result_json FROM regional_payment_execution
                 WHERE institution_code = ? AND idempotency_key = ?
                """, "REGIONAL", "idem-unknown");
        assertEquals("UNKNOWN", row.get("outcome"));
        assertNull(row.get("result_json"));

        var byReference = repository.findByPaymentReference("REGIONAL", "PAY-1").orElseThrow();
        var byKey = repository.findByIdempotencyKey("REGIONAL", "idem-unknown").orElseThrow();
        assertEquals(PaymentExecutionOutcome.UNKNOWN, byReference.outcome());
        assertEquals(PaymentExecutionOutcome.UNKNOWN, byKey.outcome());
        assertEquals("PAY-1", byReference.paymentReference());
        assertEquals("PAY-1", byKey.paymentReference());
        assertNull(byReference.bankReference());
        assertNull(byKey.bankReference());

        assertThrows(PaymentExecutionUnavailableException.class,
                () -> repository.executeIdempotent(
                        "REGIONAL", "idem-unknown", "a".repeat(64), command(),
                        () -> { executions.incrementAndGet(); return completed(); }));
        assertEquals(1, executions.get());
    }

    @Test
    void pendingClaimWithoutJsonIsUnknownNotNotFound() {
        jdbc.update("""
                INSERT INTO regional_payment_execution
                    (institution_code, payment_reference, idempotency_key,
                     request_fingerprint, operation_code)
                VALUES (?, ?, ?, ?, ?)
                """, "REGIONAL", "PAY-PENDING", "idem-pending", "b".repeat(64), "001");
        assertEquals(PaymentExecutionOutcome.UNKNOWN,
                repository.findByPaymentReference("REGIONAL", "PAY-PENDING").orElseThrow().outcome());
        assertEquals(PaymentExecutionOutcome.UNKNOWN,
                repository.findByIdempotencyKey("REGIONAL", "idem-pending").orElseThrow().outcome());
    }

    @Test
    void terminalClaimWithMissingJsonFailsClosed() {
        jdbc.update("""
                INSERT INTO regional_payment_execution
                    (institution_code, payment_reference, idempotency_key,
                     request_fingerprint, operation_code, outcome)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "REGIONAL", "PAY-INCONSISTENT", "idem-inconsistent",
                "c".repeat(64), "001", "COMPLETED");
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> repository.findByPaymentReference("REGIONAL", "PAY-INCONSISTENT"));
        assertThrows(PaymentExecutionUnavailableException.class,
                () -> repository.findByIdempotencyKey("REGIONAL", "idem-inconsistent"));
    }

    @Test
    void noClaimRemainsNotFound() {
        assertTrue(repository.findByPaymentReference("REGIONAL", "missing").isEmpty());
        assertTrue(repository.findByIdempotencyKey("REGIONAL", "missing").isEmpty());
    }

    @Test
    void schemaStoresRequiredCorrelationColumnsAndLeavesBankContextNullableUntilKnown() {'''
    return replace_once(source,old,new,str(TEST))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='write the verified patch to local files')
    args = parser.parse_args()
    branch = git('branch', '--show-current')
    head = git('rev-parse', 'HEAD')
    if branch != BRANCH or head != BASE:
        raise RuntimeError(f'Refusing patch: expected {BRANCH}@{BASE}, observed {branch}@{head}')
    updates = {}
    for p,fn in [(MAIN,patch_main),(TEST,patch_test)]:
        if not p.is_file():
            raise RuntimeError(f'Missing required source file: {p}')
        old = p.read_text(encoding='utf-8')
        new = fn(old)
        if new == old:
            raise RuntimeError(f'No change prepared: {p}')
        updates[p] = (old,new)
        print(f'{"UPDATE" if args.apply else "PREVIEW"}: {p} ({len(old)} -> {len(new)} characters)')
    if args.apply:
        # All exact-content checks completed before the first file is written.
        for path,(old,new) in updates.items():
            if path.read_text(encoding='utf-8') != old:
                raise RuntimeError(f'Source changed since preflight: {path}')
        for path,(_,new) in updates.items():
            path.write_text(new, encoding='utf-8', newline='')
        print('Applied. No git commit, push, branch switch or tests were performed.')
    else:
        print('Dry-run only. Use --apply to write the two files.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, subprocess.CalledProcessError, RuntimeError) as ex:
        print(f'ERROR: {ex}', file=sys.stderr)
        sys.exit(1)
