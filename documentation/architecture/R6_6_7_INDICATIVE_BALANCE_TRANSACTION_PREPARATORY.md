# R6.6.7 — Soldes indicatifs / frontière transactionnelle (préparatoire)

**Statut : PREPARATORY / BLOCKED_BANK_EVIDENCE. Aucun SQL de solde approuvé ; aucune activation du postage.**

Repository : `SIXGEN-Solutions/regional-core-banking-api`.
Branche : `feat/payment-orchestration-worflow` ; base de patch : `d59826fb7653776bc9c9cd347f99bd1eeb6a580a`.

## Contrôle des lots antérieurs

R6.6.5 : la coordination JDBC et le verrou JVM mono-instance sont présents, mais l'allocation bancaire authoritative EVE n'existe pas. R6.6.5 ne peut pas être clos pour l'exécution financière.

R6.6.6 : `BkeveJdbcPostingAdapter` est présent, accepte BKEVE/BKEVE_EOD et 233 valeurs, mais attend un INSERT approuvé par la banque. Il n'est pas câblé au paiement actif.

Le prompt mentionne R6.6.6 une seconde fois ; l'objectif « soldes indicatifs » correspond au lot R6.6.7.

## Réalisation locale

`BkeveIndicativeBalanceUpdate` représente une règle bancaire injectable et volontairement non implémentée. `BkevePostingAndBalanceWrite` compose l'INSERT BKEVE puis les soldes dans le **même `Connection`**, sans commit ni rollback ; `JdbcBkeveTransactionCoordinator` reste seul responsable de leur transaction et du verrou JVM.

Le stub non configuré échoue volontairement, la transaction appelante doit alors effectuer un rollback. Aucun SQL, table de solde, formule de variation, branches par code opération, règle de valorisation ou stratégie jour/nuit n'est inventé. Les tests Mockito vérifient l'ordre des appels et le rollback ; ce ne sont **pas** des tests d'intégration Informix.

## Conditions avant activation

- La Régionale valide les SQL précis de mise à jour des soldes indicatifs et les colonnes/tables jour/nuit ; identifier clairement les opérations concernées et exclusions.
- Confirmer soldes initiaux, débits/crédits, contrôles de concurrence, cardinalité des lignes, échecs et règles de vérification.
- Confirmer que ces mutations et l'INSERT BKEVE sont bien atomiques dans une transaction Informix autoritative ; fournir la preuve concernant le composant legacy `executeUpdateSystemQuery`.
- Valider allocation EVE, compatibilité avec les producteurs externes et récupération de commit incertain.
- Exécuter des tests d'intégration Informix autorisés couvrant commit, rollback après INSERT, échec d'UPDATE, jour/nuit, concurrence et recovery.

**Aucun changement à OpenAPI, SIXPAY, CI, base, schéma, sécurité ni aux endpoints.**

## Commandes de validation utilisateur

```bash
./mvnw -Dtest=BkevePostingAndBalanceWriteTest test
./mvnw clean verify
git diff --check
git status --short
```
