# R6.6.5 — Verrou JVM temporaire en déploiement mono-instance

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
