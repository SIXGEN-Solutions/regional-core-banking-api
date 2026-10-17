# R6.6.5 — Numérotation EVE : préparation de la frontière d'allocation atomique

**Statut : PREPARATORY / BLOCKED_BANK_EVIDENCE. Aucun allocateur Informix de production n'est implémenté.**

## Traçabilité

- Repository : `SIXGEN-Solutions/regional-core-banking-api`.
- Base ciblée : `feat/payment-orchestration-worflow @ 53c4025528d41983b35a5b0490e3ca94de3c62d9`.
- Sources : R6.6.1 BKEVE analysis/gap register, R6.5 context resolver, R6.6.4 enrichment.
- SIXPAY : référence en lecture seule.

## Constats

Le `PaymentExecutionBankingContextResolver` existant utilise `MAX(eve)` puis un contrôle d'existence. Ces lectures distinctes ne prouvent pas l'unicité en situation concurrente et ne constituent pas une réservation bancaire atomique. Le service R6.6.4 recopie simplement la valeur reçue, sans la réserver.

## Livrables préparatoires

- `BkeveAtomicEventNumberPort` : contrat interne pour une allocation **assurée atomique par la banque** et non déduite de `MAX(eve)`.
- `BkeveEventNumberAllocationService` : validation d'entrée, délégation, validation du format des six chiffres, sans SQL.
- `UnconfiguredBkeveAtomicEventNumberPort` : refuse l'opération tant que l'implémentation bancaire n'est pas validée.
- `BkeveEventNumberAllocationServiceTest` : scénario de concurrence avec faux port `AtomicInteger` **strictement réservé aux tests** ; vérifie l'absence de doublons avec ce faux port, pas sur Informix.

## Blocages bancaires et architecturaux

1. La Régionale doit définir la source de numérotation autoritative, la clé/portée d'unicité, les procédures ou commandes SQL autorisées et la gestion du débordement.
2. Définir le mécanisme d'exclusion mutuelle **inter-instances** et la transaction garantissant allocation + postage BKEVE, ainsi que l'issue en cas de rollback ou commit incertain.
3. Démontrer la compatibilité et le rôle éventuel des compteurs `BKOPE`/`SYN_BKOPE` observés dans les traces legacy ; aucune mutation ne peut être inventée.
4. Confirmer la stratégie jour/nuit, notamment `BKEVE_EOD`, le passage de mode et le périmètre de l'unicité.
5. Valider l'intégration au `PaymentExecutionBankingContextResolver` **en supprimant sa voie MAX/existence avant tout postage**, puis démontrer des tests d'intégration concurrents multi-connexions sur le SGBD cible.
6. Corréler `paymentReference`, clé d'idempotence et EVE pour récupération autoritative. Ne jamais rejouer une issue financière inconnue.

**Les classes préparées ne sont pas câblées dans le flux actif et ne rendent pas R6.6.5 opérationnel.** Le `JdbcPaymentExecutionBankingAdapter` reste fail-closed, aucun INSERT/commit BKEVE.

## Validations à exécuter par l'utilisateur

```bash
./mvnw -Dtest=BkeveEventNumberAllocationServiceTest test
./mvnw clean verify
git diff --check
git status --short
```
