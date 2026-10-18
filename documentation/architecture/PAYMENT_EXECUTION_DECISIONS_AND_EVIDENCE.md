# Payment Execution — décisions, preuves et historique

> **Périmètre de lecture** : SHA `d31f42e91305e30b2f5f01c7f9073ea2bfc6ae3b`.
> Ce registre ne crée aucune nouvelle décision ni approbation. Les qualifications
> ci-dessous proviennent des pièces existantes et sont limitées à leur portée.

| Sujet | Statut et propriété | Référence / restriction |
| --- | --- | --- |
| Contrat de transport | `REGIONAL_CANONICAL` uniquement dans sa version explicitement approuvée | `contracts/openapi/regional-core-banking-api-v1.yaml` ; vérifier sa métadonnée |
| T0 BKEVE / T1 BKMVTI | Décisions MVP consignées dans les sources Regional | `R6_6_2_MVP_CONTRACT_DECISIONS.md` ; aucun BKMVTI posté à T0 |
| Montants débit/crédit indépendants | Addendum de contrat approuvé dans la source MVP | `R6_6_2_MVP_CONTRACT_DECISIONS.md` ; distinguer de ses sections historiques `DRAFT` |
| Mono-agence / sans compte de liaison | Périmètre MVP indiqué par les décisions | `R6_6_2_MVP_CONTRACT_DECISIONS.md` |
| Mapping 233 positions | `BANK_REFERENCE` / extraction legacy : preuve de correspondance, **pas** validation du schéma physique | `R6_6_1_BKEVE_MAPPING_233.csv`, `R6_6_1_BKEVE_ANALYSIS.md` |
| Contexte comptable et EVE | Responsabilité du CBS / traitement Regional | `R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` ; valider le comportement sur la cible réelle |
| EVE par OPE | Décision de mono-instance et exclusivité OPE énoncée dans le document d'activation | `R6_6_10_ACTIVATION_DECISION.md` ; verrou JVM non distribué |
| Idempotence et `UNKNOWN` | Sûreté financière du code et du contrat | Pas de rejeu aveugle ; rechercher une preuve bancaire |
| Simulation Oracle Docker | Infrastructure de test synthétique | `R6610OraclePaymentIT.java` ; aucun GO bancaire réel |
| Activation réelle | **NO-GO** consigné au 18 octobre 2026 | `R6_6_10_ACTIVATION_DECISION.md` ; décisions G1 à G8 à vérifier sur révision/environnement cible |
| SIXPAY | `SIXPAY_REFERENCE` / `REFERENCE_ONLY` | Vérification de compatibilité, pas modèle source Regional |

## Contradictions et limites de temporalité
- `R6_6_2_MVP_CONTRACT_DECISIONS.md` comporte un addendum d'approbation
  et conserve des sections `DRAFT / PENDING_APPROVAL`. Celles-ci sont
  **historiques** et ne doivent pas prévaloir sur le contrat approuvé.
- `R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` contient des questions `OPEN`
  établies à un stade antérieur. Ne pas les qualifier automatiquement de
  décisions encore ouvertes ou fermées sans pièce de validation ultérieure.
- `R6_6_5_SINGLE_INSTANCE_TEMPORARY_GUARD.md` décrit un état préparatoire
  avant branchement du postage ; il ne décrit pas à lui seul le code actuel.
- Les constats de validation intermédiaires ne prouvent pas un GO sur une
  base Oracle/Informix réelle.

## Préservation des sources
Les documents sources restent intacts pendant ce lot. Les reclassements,
renommages et suppressions dépendent d'une décision distincte au lot 3.
