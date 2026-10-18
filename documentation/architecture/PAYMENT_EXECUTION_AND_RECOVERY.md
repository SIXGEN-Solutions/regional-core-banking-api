# Payment Execution and Recovery — référence technique

> **Statut** : description du code courant au SHA `d31f42e91305e30b2f5f01c7f9073ea2bfc6ae3b`.
> Cette synthèse **ne remplace pas** le contrat OpenAPI approuvé, les preuves bancaires ni les décisions d'activation.
> Elle ne certifie pas une exécution sur un environnement bancaire réel.

## Périmètre
L'API Regional est autonome et réutilisable par des consommateurs autorisés.
T0 exécute le parcours Payment Execution ; les écritures comptables BKMVTI restent
du ressort du consommateur / T1 selon les décisions de périmètre du MVP.
Le chemin T0 n'écrit pas BKMVTI.

## Composants et enchaînement
1. `PaymentExecutionService` valide la commande, calcule l'empreinte et délègue
   `executeIdempotent` à la persistance technique PostgreSQL.
2. `JdbcPaymentExecutionBankingAdapter` vérifie le compte débiteur,
   les oppositions et le solde disponible, puis résout le contexte bancaire.
3. `PaymentExecutionBankingContextResolver` détermine date comptable, jour/nuit,
   dates de valeur et candidat EVE à partir des requêtes du dialecte.
4. `JdbcBkeveTransactionCoordinator` sérialise le traitement par OPE dans une
   même JVM et porte la transaction JDBC bancaire.
5. `JdbcPaymentEventNumberReservationAdapter` réserve durablement l'EVE en
   PostgreSQL (`api_eodprocess`, `regional_payment_execution`) avant l'écriture.
6. Le mapper à 233 positions et l'assembly produisent l'INSERT
   `BKEVE` / `BKEVE_EOD` et les mises à jour de soldes indicatifs prévues.
7. La transaction bancaire effectue commit ou rollback ; une issue ambiguë
   reste `UNKNOWN` et n'autorise pas de rejeu automatique.

## Recovery
`PaymentExecutionRecoveryCoordinator` privilégie un résultat technique
conclusif ; pour un état `UNKNOWN`, il consulte le port bancaire.
Le lookup vérifie l'existence de l'événement `(OPE,EVE)` dans les tables
bancaires. Une réservation PostgreSQL seule **n'est jamais** une preuve de
postage. Une absence immédiate d'événement ne garantit pas l'absence d'effet.

## Isolation des modèles et dépendances
- API : contrat et modèles de transport Regional.
- Application / domaine : orchestration et résultats métier.
- `infrastructure/amplitude` : contrôles et dialectes Oracle / Informix.
- PostgreSQL : idempotence, réservation et traçabilité technique.
- Aucune classe Java SIXPAY ne fait autorité pour Regional.

## Limites connues
- Verrou par OPE **intra-JVM** ; pas de garantie inter-instance ou vis-à-vis
  d'autres producteurs Amplitude. Déploiement mono-instance dans le périmètre
  indiqué par les décisions sources.
- Atomicité entre PostgreSQL et la transaction bancaire **non garantie**.
- Types, nullabilité et autorisations sur le schéma bancaire réel à homologuer.
- Le test Oracle Docker utilise un schéma **synthétique**, pas Amplitude réel.
- Aucune autorisation de postage financier réel par le présent document.

## Références
- Contrat : `contracts/openapi/regional-core-banking-api-v1.yaml`
- Évidence BKEVE : `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv`
- Décision d'activation : `documentation/evidence/PAYMENT_EXECUTION_ACTIVATION_DECISION.md`
- Procédure : `documentation/runbooks/PAYMENT_EXECUTION_CONTROLLED_ACTIVATION.md`
