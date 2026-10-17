#!/usr/bin/env python3
"""R6.6.5 temporary SINGLE JVM concurrency guard. Dry-run unless --apply."""
from __future__ import annotations
import argparse
import subprocess
from pathlib import Path

SHA = '63e61f7db65914f65ecb10b126cfc6dac59e81ff'
BRANCH = 'feat/payment-orchestration-worflow'
BASE = Path('src/main/java/com/regional/corebanking/payment/infrastructure/amplitude')
TEST = Path('src/test/java/com/regional/corebanking/payment/infrastructure/amplitude')

old_import = 'import java.util.Objects;\n'
new_import = old_import + 'import java.util.concurrent.locks.ReentrantLock;\n'
old_field = 'final class JdbcBkeveTransactionCoordinator {\n'
new_field = '''final class JdbcBkeveTransactionCoordinator {
    // TEMPORARY SINGLE-JVM MODE ONLY: shared by all coordinator objects in this process.
    // This does NOT protect against other application processes or Amplitude producers.
    private static final ReentrantLock SINGLE_INSTANCE_LOCK = new ReentrantLock(true);
'''
old_open = '''        try (Connection connection = dataSource.getConnection()) {
'''
new_open = '''        // Serialize the entire allocation + write + commit/rollback scope in this JVM.
        // All production callers MUST use this coordinator; no direct MAX(eve) posting.
        SINGLE_INSTANCE_LOCK.lock();
        try (Connection connection = dataSource.getConnection()) {
'''
old_close = '''        } catch (SQLException failure) {
            PaymentExecutionUnavailableException unavailable = new PaymentExecutionUnavailableException(
                "BKEVE JDBC transaction could not be opened or closed; authoritative recovery may be required");
            unavailable.initCause(failure);
            throw unavailable;
        }
    }
}
'''
new_close = '''        } catch (SQLException failure) {
            PaymentExecutionUnavailableException unavailable = new PaymentExecutionUnavailableException(
                "BKEVE JDBC transaction could not be opened or closed; authoritative recovery may be required");
            unavailable.initCause(failure);
            throw unavailable;
        } finally {
            SINGLE_INSTANCE_LOCK.unlock();
        }
    }
}
'''

TEST_IMPORT = 'import java.util.concurrent.atomic.AtomicInteger;\n'
TEST_IMPORT_REPLACE = TEST_IMPORT + '''import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
'''
TEST_ADDITION = '''
    @Test void distinctCoordinatorsSerializeWholeTransactionWithinSingleJvm() throws Exception {
        var ds = mock(DataSource.class);
        AtomicInteger currentlyInTransaction = new AtomicInteger();
        AtomicInteger maximumConcurrent = new AtomicInteger();
        AtomicInteger sequence = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        when(ds.getConnection()).thenAnswer(invocation -> {
            Connection c = mock(Connection.class);
            when(c.getAutoCommit()).thenReturn(true);
            doAnswer(x -> {
                commits.incrementAndGet();
                currentlyInTransaction.decrementAndGet();
                return null;
            }).when(c).commit();
            return c;
        });
        JdbcBkeveTransactionCoordinator.LockedAllocator testOnly = (connection, op, table, date) -> {
            int active = currentlyInTransaction.incrementAndGet();
            maximumConcurrent.accumulateAndGet(active, Math::max);
            // Widen the race window without relying on the ordering of threads.
            Thread.sleep(2);
            return "%06d".formatted(sequence.incrementAndGet());
        };
        var instanceA = new JdbcBkeveTransactionCoordinator(ds, testOnly);
        var instanceB = new JdbcBkeveTransactionCoordinator(ds, testOnly);
        var workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> operations = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                var coordinator = i % 2 == 0 ? instanceA : instanceB;
                operations.add(() -> coordinator.allocateAndWrite("001", "BKEVE", DATE,
                        (connection, eve) -> eve));
            }
            List<Future<String>> results = workers.invokeAll(operations);
            var numbers = new HashSet<String>();
            for (var f : results) numbers.add(f.get());
            assertEquals(64, numbers.size());
            assertEquals(64, commits.get());
            assertEquals(0, currentlyInTransaction.get());
            assertEquals(1, maximumConcurrent.get());
        } finally {
            workers.shutdownNow();
        }
    }
'''
DOC_PATH = Path('documentation/architecture/R6_6_5_SINGLE_INSTANCE_TEMPORARY_GUARD.md')
DOC = '''# R6.6.5 — Verrou JVM temporaire en déploiement mono-instance

**STATUT : SINGLE_INSTANCE_PREPARATORY / BANK_EVIDENCE_PENDING — aucune activation du postage bancaire.**

- Repository : `SIXGEN-Solutions/regional-core-banking-api`, branche `feat/payment-orchestration-worflow`.
- Base du correctif : `63e61f7db65914f65ecb10b126cfc6dac59e81ff`.
- Décision projet : déploiement temporaire d'une seule JVM Regional Core Banking API.

## Réalisation

`JdbcBkeveTransactionCoordinator` porte un `ReentrantLock` **statique** pour
sérialiser toutes les allocations/écritures transitant par ce coordinateur au sein
d'une même JVM, jusqu'au commit, rollback ou échec de fermeture de connexion.
Deux objets coordinateurs dans la même JVM partagent ce verrou.
Cela ne remplace pas le verrouillage transactionnel bancaire inter-instances.

Le test `distinctCoordinatorsSerializeWholeTransactionWithinSingleJvm` exerce
64 opérations concurrentes sur deux coordinateurs partageant un fournisseur
fictif et vérifie une section critique unique. Il ne simule pas plusieurs JVM
et ne valide aucun SQL Informix.

## Limitations bloquantes

1. D'autres logiciels Amplitude peuvent allouer EVE hors de cette JVM :
   le verrou local ne les empêche pas d'obtenir le même numéro.
2. `PaymentExecutionBankingContextResolver` continue à calculer un **numéro
   provisoire non réservé** depuis MAX(eve). Le code de postage ne doit pas
   l'utiliser comme allocation finale.
3. Aucun allocateur bancaire autoritatif (`LockedAllocator`) n'est implémenté,
   et aucune instruction Informix/Oracle nouvelle n'est supposée.
4. Aucune écriture BKEVE ni intégration du coordinateur à `execute()` n'est
   activée ; le flux existant conserve son fail-closed.
5. Si la Régionale passe à plusieurs instances, **retirer/désactiver cette
   protection comme mécanisme de sûreté** et mettre en place un verrou DB
   partagé, dans la même transaction que le postage, validé par la banque.
6. En cas de commit incertain, ne jamais rejouer automatiquement : utiliser
   la récupération autoritative après validation des règles bancaires.

## Validations à exécuter localement

```bash
./mvnw -Dtest=JdbcBkeveTransactionCoordinatorTest test
./mvnw clean verify
git diff --check
git status --short
```
'''

def git(*args):
    return subprocess.check_output(['git', *args], text=True, stderr=subprocess.STDOUT).strip()

def replace_once(value, before, after, name):
    if value.count(before) != 1:
        raise SystemExit(f'ABORT: unexpected source at {name}: expected one match, got {value.count(before)}')
    return value.replace(before, after, 1)

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apply', action='store_true', help='Write files (default: dry-run)')
    a = p.parse_args()
    if git('rev-parse', '--abbrev-ref', 'HEAD') != BRANCH:
        raise SystemExit('ABORT: branch mismatch; no file written')
    actual = git('rev-parse', 'HEAD')
    if actual != SHA:
        raise SystemExit(f'ABORT: HEAD {actual} differs from approved starting SHA {SHA}')
    source = BASE / 'JdbcBkeveTransactionCoordinator.java'
    test = TEST / 'JdbcBkeveTransactionCoordinatorTest.java'
    original = source.read_text(encoding='utf-8')
    changed = replace_once(original,old_import,new_import,str(source))
    changed = replace_once(changed,old_field,new_field,str(source))
    changed = replace_once(changed,old_open,new_open,str(source))
    changed = replace_once(changed,old_close,new_close,str(source))
    tests = test.read_text(encoding='utf-8')
    new_tests = replace_once(tests,TEST_IMPORT,TEST_IMPORT_REPLACE,str(test))
    if not new_tests.rstrip().endswith('}'):
        raise SystemExit('ABORT: unexpected JUnit test class structure')
    new_tests = new_tests.rstrip()[:-1] + TEST_ADDITION + '}\n'
    if DOC_PATH.exists():
        raise SystemExit(f'ABORT: {DOC_PATH} exists, refusing overwrite')
    operations = [(source, changed), (test, new_tests), (DOC_PATH, DOC)]
    for path, content in operations:
        print(('APPLY' if a.apply else 'DRY-RUN'), path, f'({len(content)} chars)')
    if not a.apply:
        print('No files written. Re-run with --apply to apply.')
        return
    for path, content in operations:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8', newline='\n')
    print('Files written. Run targeted Maven test and verify locally. No git commit/push performed.')

if __name__ == '__main__':
    main()
