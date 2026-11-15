# R6.6.10 — Décision GO / NO-GO pour le postage BKEVE réel

**Décision au 18 octobre 2026 : NO-GO pour le postage financier réel.**
**GO limité : préparation, tests unitaires, tests simulés et qualification technique sans effets bancaires réels.**

## Références de révision et portée

- Implémentation cible : `SIXGEN-Solutions/regional-core-banking-api`.
- Révision examinée : `feat/payment-orchestration-worflow @ 0bd14abd853b2ad0cb4fa6213ab6b68390d07d21`.
- Mode : lecture GitHub ; livrable documentaire appliqué localement par script ; aucune modification distante.
- Consommateur : `SIXGEN-Solutions/sixpay-connect`, `SIXPAY_REFERENCE / REFERENCE_ONLY`, aucune modification.
- Source canonique : `contracts/openapi/regional-core-banking-api-v1.yaml` ; aucun changement contractuel.
- Anciennes pièces d'analyse R6.6 et registre R6.6.9 : preuve datée de leur propre révision, **pas** qualification automatique du HEAD actuel.

## Ce qui est établi par inspection du code actuel

1. `PaymentExecutionService` passe par la revendication idempotente PostgreSQL.
2. `JdbcPaymentExecutionBankingAdapter` effectue des contrôles bancaires, détermine le contexte jour/nuit, puis utilise le coordinateur JDBC.
3. `JdbcBkeveTransactionCoordinator` applique un verrou JVM par OPE, ouvre une transaction avec `setAutoCommit(false)`, alloue, exécute l'écriture, puis commit ou rollback. En cas de commit incertain : recovery obligatoire, pas de rejeu automatique.
4. `JdbcPaymentEventNumberReservationAdapter` réserve l'EVE via `API_EODProcess.lasteve` et associe le numéro au claim `regional_payment_execution` dans une transaction PostgreSQL avant l'INSERT bancaire.
5. Le chemin BKEVE assemble enrichissement, mapping de 233 positions, INSERT et soldes indicatifs sur la connexion bancaire transactionnelle ; le recovery trouve `OPE/EVE` en PostgreSQL et interroge Amplitude.
6. PostgreSQL est une traçabilité technique : ni sa réservation ni un claim idempotent ne prouvent la réussite bancaire.
7. La connexion d'écriture n'applique pas automatiquement `prepareConnection()` qui configure `DIRTY READ` pour les lectures Informix. Cette séparation est conservée ; le niveau d'isolation effectif de la transaction d'écriture dépend du pilote/de l'environnement.

Ces constats démontrent un **câblage statique**, non une homologation bancaire, une preuve d'atomicité multi-base ni une autorisation de produire des effets financiers.

## Décisions bancaires déjà actées dans ce lot

- Une seule instance applicative Regional au périmètre visé ; verrou par OPE, sans verrou global BKEVE.
- Un code OPE est exclusivement attribué à cette application dans Amplitude ; unicité de l'événement par `(OPE,EVE)`.
- `API_EODProcess` suit le dernier EVE affecté ; la réservation survit aux rollbacks bancaires.
- Les valeurs non fournies/non résolues des 233 positions BKEVE sont liées en `NULL` selon la décision fournie ; cela ne prouve pas la nullabilité effective du schéma bancaire.
- Les soldes indicatifs suivent les conditions historiques retenues ; pas de nouvelles règles inventées dans R6.6.10.
- Amplitude demeure l'autorité pour le fait bancaire et la décision de paiement exécuté.
- Une issue `UNKNOWN` interdit tout rejeu aveugle. L'absence d'événement à l'instant du lookup ne prouve pas l'absence définitive d'effet financier.

## Critères de décision d'activation

| Gate | Preuve à produire | Statut à cette révision |
|---|---|---|
| G1 | Revue documentaire, SHA figé, schéma et mappings concordants | Préparé, à signer |
| G2 | `mvnw clean verify` réussi sur le même SHA, journal archivé | Signalé réussi par le demandeur, preuve locale non recueillie ici |
| G3 | Tests PostgreSQL dédiés : claim, `EODProcess`, unicité EVE, UNKNOWN et reprise | À fournir |
| G4 | Essais BKEVE/BKEVE_EOD et soldes jour/nuit sur environnement bancaire **autorisé**, types/NULL/JDBC | Non exécuté / environnement indisponible |
| G5 | Tests transaction : commit, rollback, déconnexion pendant commit, absence de rejeu, recovery Amplitude | Simulations possibles ; validation réelle non exécutée |
| G6 | Connexion et isolation bancaire effectives, privilèges minimaux, sauvegarde et supervision | À attester par exploitant/DBA |
| G7 | Procédure d'arrêt, gestion `UNKNOWN`, rapprochement bancaire, référents et critères d'escalade | Procédure rédigée, approbation opérationnelle requise |
| G8 | Autorisation expresse de La Régionale et validation de changement/déploiement | Non fournie |

**Règle de GO** : G1 à G8 conformes et signés par les autorités compétentes, pour un SHA, une configuration et un environnement précisément identifiés. Aucun GO implicite par succès Maven ou présence de classes.

**NO-GO immédiat** : toute incertitude sur le résultat d'une opération financière, divergence de soldes/événements, impossibilité du recovery autoritatif, défaut de privilèges, erreur d'isolation/transaction, environnement non autorisé, ou absence d'approbation bancaire.

## Portée de cette décision

- Ce fichier **n'active aucun runtime** et ne crée ni nouvelle variable de sécurité, ni endpoint, ni changement SQL, ni nouveau statut OpenAPI.
- Les décisions d'infrastructure, bancaires et de mise en production demeurent humaines.
- En cas de révision différente, reprendre la revue ciblée et les validations affectées ; ne pas recycler la présente décision comme GO.

**Décision formelle : `R6.6.10 = NO-GO_REAL_POSTING / GO_NON_FINANCIAL_VALIDATION`.**
