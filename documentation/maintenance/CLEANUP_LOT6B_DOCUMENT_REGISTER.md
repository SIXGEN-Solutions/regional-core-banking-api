# Lot 6B — Registre de migration documentaire R6

- Repository : `SIXGEN-Solutions/regional-core-banking-api`
- Branche / SHA de départ : `feat/payment-orchestration-worflow @ 77798dba552bd4a1fb08e2328e6cc1cdf726d6fe`
- Le déplacement ne constitue ni approbation ni révision de fond.
- Les documents archivés restent historiques ; leurs assertions ne sont pas réactivées.
- Décision NO-GO pour le postage bancaire réel inchangée.

| Source | Classement | Destination | SHA-256 original |
| --- | --- | --- | --- |
| `documentation/architecture/R6_6_10_ACTIVATION_DECISION.md` | `EVIDENCE` | `documentation/evidence/r6/R6_6_10_ACTIVATION_DECISION.md` | `8b0b025968f026439249476cff205cf606452c7fe5305a151f7c2bb89eeb64bc` |
| `documentation/architecture/R6_6_10_TRANSACTION_WRITE_ASSEMBLY.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_10_TRANSACTION_WRITE_ASSEMBLY.md` | `90cfa37633aa66136a121b905f61719c32b5f2ef431ef5a1720c51261719e0ad` |
| `documentation/architecture/R6_6_11_LEGACY_BINDINGS_VERIFIED.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_11_LEGACY_BINDINGS_VERIFIED.md` | `f14959ea50bf927be05a8d31de2f33ab4af10590cff540de786dcac8739c0f33` |
| `documentation/architecture/R6_6_1_BKEVE_ANALYSIS.md` | `EVIDENCE` | `documentation/evidence/r6/R6_6_1_BKEVE_ANALYSIS.md` | `f1629df4dbb49cb8e138aab4d4bd51c1f27e0fa21e55d3382d325d0a5a4f5500` |
| `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv` | `KEEP` | `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv` | `1c65c7dfd74e944fae09b5d8f1172b78606b7042fde2c6be65bb0ccaf0594f02` |
| `documentation/architecture/R6_6_1_GAP_REGISTER.md` | `EVIDENCE` | `documentation/evidence/r6/R6_6_1_GAP_REGISTER.md` | `269fc7a3df136064b4ee7692dbfe66f65ff98812b2f6f9c57de11f0a07b454f4` |
| `documentation/architecture/R6_6_2_MVP_CONTRACT_DECISIONS.md` | `EVIDENCE` | `documentation/evidence/r6/R6_6_2_MVP_CONTRACT_DECISIONS.md` | `60fe59d76ceb15b15ed91870c795ca7eb7e2bd054b11e3f654668644979e22a3` |
| `documentation/architecture/R6_6_2_SIXPAY_COMPATIBILITY_REVIEW.md` | `EVIDENCE` | `documentation/evidence/r6/R6_6_2_SIXPAY_COMPATIBILITY_REVIEW.md` | `cb38e975738caa0f86e6dc3ba4c2aa2832fdddfa37ad87236eabf4cfd94ca674` |
| `documentation/architecture/R6_6_3_BKEVE_MODEL_MAPPING.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_3_BKEVE_MODEL_MAPPING.md` | `8802bfdde985f4a0af4e9ffb655da6671a4a0d5d8a97fba7643a3f50d69bbd29` |
| `documentation/architecture/R6_6_4_BKEVE_BANK_ENRICHMENT.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_4_BKEVE_BANK_ENRICHMENT.md` | `4474069ff6188c8341b488e86fc670ed31b41b13d0b8da3340a97bf86f12e9f7` |
| `documentation/architecture/R6_6_5_BKEVE_EVE_ALLOCATION.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_5_BKEVE_EVE_ALLOCATION.md` | `c819b5edb8274e4d447d99f8ed414ce827b9ada81c624d82b047070339a71910` |
| `documentation/architecture/R6_6_5_JDBC_TRANSACTION_SCOPE.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_5_JDBC_TRANSACTION_SCOPE.md` | `143d321156ba86d08b527a7cda201f91b30195b5b6bde98fa09ad995f0981cf5` |
| `documentation/architecture/R6_6_5_SINGLE_INSTANCE_TEMPORARY_GUARD.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_5_SINGLE_INSTANCE_TEMPORARY_GUARD.md` | `c16e79b2cc18cf12080d0ea863ff6e06244070fb0a1ac2388eafb47c5244178e` |
| `documentation/architecture/R6_6_5_TRANSACTION_SCOPED_EVE_DESIGN.md` | `ARCHIVE` | `documentation/archive/r6/R6_6_5_TRANSACTION_SCOPED_EVE_DESIGN.md` | `1fd1e8e5dc281879cba96f4368005a2d9060cce28c317e1a39a3c2b7e80ebf9e` |
| `documentation/architecture/R6_6_BKEVE_TRANSPORT_233.md` | `KEEP` | `documentation/architecture/R6_6_BKEVE_TRANSPORT_233.md` | `8f5243d8d4d8cb146758b9d01b68af9084fd71eeacfe752c51d2902b868c8191` |
| `documentation/architecture/R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` | `EVIDENCE` | `documentation/evidence/r6/R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` | `4a1525b1edc33b10caa5a465b8540d2030b6484b30aa4d69e23df2002f01702f` |
| `documentation/architecture/R6_PAYMENT_EXECUTION_RECOVERY_BASELINE.md` | `EVIDENCE` | `documentation/evidence/r6/R6_PAYMENT_EXECUTION_RECOVERY_BASELINE.md` | `80c19db32cb3bb42ce2380a7f258f8c739ec2b94d09f583e37a5e91ea485a3bd` |

## Vérifications supplémentaires obligatoires

- Rechercher les références en texte libre aux anciens noms et chemins.
- Vérifier les liens relatifs Markdown des documents déplacés.
- Vérifier le mapping 233 positions à son chemin stable.
- Tester `BkeveJdbcParameterMapperTest` et `./mvnw clean verify`.
- Vérifier l'exactitude des déclarations de statut et le NO-GO réel.
- Confirmer l'absence de modification de production, de contrat ou de SIXPAY.
