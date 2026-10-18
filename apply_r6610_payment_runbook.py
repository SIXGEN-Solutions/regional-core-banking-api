#!/usr/bin/env python3
"""Ajoute les étapes R6.6.10 au runbook existant (local uniquement)."""
import argparse
from pathlib import Path
import subprocess

EXPECTED_BRANCH = 'feat/payment-orchestration-worflow'
EXPECTED_SHA = '18fe5c7bd0ac949f139186fc9a93331b9401b75d'
TARGET = Path('documentation/runbooks/LOCAL_DOCKER_COMPOSE_RUNBOOK.md')
MARKER = '## R6.6.10 — Paiement simulé Oracle Docker + PostgreSQL de test'

SECTION = r'''## R6.6.10 — Paiement simulé Oracle Docker + PostgreSQL de test

**Périmètre : intégration locale uniquement.** Les commandes de cette section
ne réalisent aucune opération sur Amplitude/Informix et ne constituent pas
une validation du schéma bancaire réel. Elles réutilisent le véritable adapter
`JdbcPaymentExecutionBankingAdapter` contre une base Oracle Free **synthétique**,
ainsi que la persistance technique PostgreSQL réelle dans une base **jetable**.

Cette procédure est distincte du lancement de l'image publiée décrit plus haut :
le test Oracle est exécuté par Maven sur les sources locales. Un `docker compose up`
standard ou le démarrage de l'API seule **ne lance pas** `R6610OraclePaymentIT`.

### A. Vérifier que le correctif d'intégration Oracle a été appliqué

Les fichiers suivants doivent exister dans le workspace local :

- `docker/customer-oracle/002_payment_schema.sql` (fixture TEST ONLY) ;
- `src/test/java/com/regional/corebanking/payment/infrastructure/amplitude/R6610OraclePaymentIT.java` ;
- `compose.customer-oracle.yml` doit monter `002_payment_schema.sql`, en plus de `001_customer_schema.sql`.

Au SHA de référence `18fe5c7bd0ac949f139186fc9a93331b9401b75d`, ces
modifications n'existent pas encore sur GitHub. Si elles ne sont pas présentes
localement, appliquer d'abord le correctif local `apply_r6610_oracle_payment.py`
fourni séparément ; **ne pas exécuter le test avant cette étape**. Ce runbook
n'applique ni la fixture ni les modifications Java.

```powershell
git branch --show-current
git rev-parse HEAD
Test-Path .\docker\customer-oracle\002_payment_schema.sql
Test-Path .\src\test\java\com\regional\corebanking\payment\infrastructure\amplitude\R6610OraclePaymentIT.java
Select-String -Path .\compose.customer-oracle.yml -Pattern '002_payment_schema.sql'
```

Les deux commandes `Test-Path` doivent retourner `True` et `Select-String`
doit trouver le montage. La vérification Git permet d'enregistrer la révision
réellement testée ; elle ne remplace pas l'inspection des modifications locales.

### B. Démarrer les bases de test isolées (PowerShell)

Depuis la racine du repository, dans une session PowerShell :

```powershell
$env:ORACLE_APP_PASSWORD = '<mot-de-passe-local-de-test>'
$env:REGIONAL_CONFIRMATION_TEST_POSTGRES_PASSWORD = '<mot-de-passe-postgres-test>'

docker compose -f compose.customer-oracle.yml up -d
docker compose -f compose.test-postgres.yml up -d

docker compose -f compose.customer-oracle.yml ps
docker compose -f compose.test-postgres.yml ps
```

Attendre que les services `customer-oracle` et `test-postgres` soient
`healthy`. Si besoin :

```powershell
docker compose -f compose.customer-oracle.yml logs --tail=100 customer-oracle
docker compose -f compose.test-postgres.yml logs --tail=100 test-postgres
```

**Volume Oracle existant :** `container-entrypoint-initdb.d` n'est exécuté
qu'à la première initialisation du volume Oracle. Si `customer-oracle-data`
existait avant l'ajout de `002_payment_schema.sql`, **ne pas conclure** que
la fixture Payment a été créée. Examiner la présence des tables dans le mock ;
si nécessaire, appliquer explicitement la fixture dans cette instance de test
ou recréer **uniquement** son volume jetable après contrôle des données.
Ne jamais exécuter un `down -v` sur une stack partagée ou contenant des données
à conserver. Le runbook ne prescrit aucune suppression automatique de volume.

### C. Configurer les variables de test (même session PowerShell)

```powershell
$env:RUN_R6610_ORACLE_IT = 'true'
$env:ORACLE_JDBC_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:REGIONAL_CONFIRMATION_IT_DB_URL = 'jdbc:postgresql://localhost:15433/regional_core_banking_test?currentSchema=core_banking'
$env:REGIONAL_CONFIRMATION_IT_DB_USERNAME = 'regional_core_banking_test'
$env:REGIONAL_CONFIRMATION_IT_DB_PASSWORD = $env:REGIONAL_CONFIRMATION_TEST_POSTGRES_PASSWORD
```

Le test PostgreSQL détruit/recrée `regional_payment_execution` et
`api_eodprocess` dans **`regional_core_banking_test` uniquement**. Il contient
une garde refusant tout autre nom de base. Les fixtures Oracle sont également
réinitialisées par le test. Ne jamais utiliser une base partagée, la base
technique runtime `regional_core_banking`, ou une instance Oracle bancaire réelle.

### D. Exécuter les validations dans l'ordre

Test d'intégration JDBC avec Oracle Free + PostgreSQL jetable :

```powershell
.\mvnw.cmd '-Dtest=R6610OraclePaymentIT' test
```

Le test doit démontrer, sur le véritable adapter bancaire Spring Java :

| Scénario | Contrôles attendus |
| --- | --- |
| Paiement réussi | Résultat `COMPLETED`, une ligne BKEVE, 233 positions JDBC traitées par le mapper, soldes débiteur/créditeur mis à jour, `OPE/EVE` persisté dans PostgreSQL |
| Idempotence et recovery | Même clé sans second INSERT, lecture Oracle de l'événement par référence de paiement et clé d'idempotence |
| Fonds insuffisants | `REJECTED`, aucun BKEVE créé, aucune réservation EVE |
| Échec d'écriture de solde | Rollback Oracle (pas de ligne BKEVE committée), `UNKNOWN` technique, réservation EVE conservée dans PostgreSQL |

Les résultats ci-dessus sont **attendus**, et non attestés tant que le test
n'a pas terminé avec succès. Le jeu de tests R6.6.10 actuel traverse
`JdbcPaymentExecutionBankingAdapter.execute()` mais ne constitue pas encore
un test HTTP MockMvc traversant l'endpoint Payment Execution.

Puis gates généraux (à exécuter localement, jamais lancés par ce runbook) :

```powershell
.\mvnw.cmd clean verify
git diff --check
git status --short
```

Ne pas confondre `clean verify` vert et résultat du test opt-in : si la variable
`RUN_R6610_ORACLE_IT` est absente, JUnit **désactive** le test Oracle.
Conserver les logs de chaque commande et vérifier qu'aucun test ciblé n'a été
skippé. Un test qui échoue ne doit pas être présenté comme validation passée.

### E. Observation et diagnostic

Vérifier les traces Maven/JUnit du test, puis les journaux Docker :

```powershell
docker compose -f compose.customer-oracle.yml logs --tail=100 customer-oracle
docker compose -f compose.test-postgres.yml logs --tail=100 test-postgres
```

Les assertions Java sont la vérification primaire des données BKEVE, des
soldes, de la réservation `API_EODProcess` et de l'idempotence. Ne pas déduire
une exécution bancaire validée de la seule ligne PostgreSQL : la preuve
simulée de postage est la ligne BKEVE dans Oracle Free.

En cas d'erreur `ORA-00942` ou `ORA-00904`, vérifier en premier lieu que le
script `002_payment_schema.sql` a réellement été appliqué sur la base
Oracle mock. En cas d'erreur PostgreSQL, confirmer le nom exact
`regional_core_banking_test`, le port `15433`, l'utilisateur et le mot de passe.
Si les tests échouent pour des raisons de dialecte SQL, **ne pas modifier** le
SQL Amplitude de production à partir du seul comportement Oracle mock.

### F. Arrêter sans détruire les volumes

```powershell
docker compose -f compose.test-postgres.yml down
docker compose -f compose.customer-oracle.yml down
```

Le conteneur PostgreSQL de test utilise `tmpfs` ; ses données sont volatiles.
Le volume Oracle persiste après `down` (sans `-v`). Aucun service de production
n'est concerné par ces commandes, à condition de cibler uniquement ces fichiers
Compose de test.

### Critère de sortie R6.6.10

**GO Spring Boot simulé** seulement si les tests ciblés réellement exécutés
sont verts, les assertions PostgreSQL/Oracle satisfaites, et les anomalies
résiduelles documentées. **Ce GO n'est pas un GO d'écriture Amplitude réelle.**
Les contrôles sur un environnement bancaire autorisé restent couverts par
`documentation/runbooks/R6_6_10_CONTROLLED_ACTIVATION.md`.
'''

def git(*args):
    return subprocess.check_output(['git', *args], text=True, stderr=subprocess.STDOUT).strip()

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apply', action='store_true')
    args = p.parse_args()
    if not Path('pom.xml').is_file() or not TARGET.is_file():
        raise SystemExit('Exécuter depuis la racine du repository régional existant.')
    branch, sha = git('branch', '--show-current'), git('rev-parse', 'HEAD')
    if branch != EXPECTED_BRANCH or sha != EXPECTED_SHA:
        raise SystemExit(f'Revision inattendue ({branch} @ {sha}). Cible attendue : {EXPECTED_BRANCH} @ {EXPECTED_SHA}.')
    current = TARGET.read_text(encoding='utf-8')
    if MARKER in current:
        raise SystemExit('Section R6.6.10 déjà présente : refus de doubler le contenu.')
    if not current.startswith('# Local Docker Compose Runbook'):
        raise SystemExit('Runbook non reconnu ; aucun changement.')
    print('Fichier MODIFIÉ :', TARGET)
    print('Section AJOUTÉE :', MARKER)
    if args.apply:
        TARGET.write_text(current.rstrip() + '\n\n' + SECTION.rstrip() + '\n', encoding='utf-8')
        print('Runbook mis à jour localement. Aucun commit, push, PR ou gate.')
    else:
        print('Prévisualisation seulement. Exécuter avec --apply pour écrire.')

if __name__ == '__main__':
    main()
