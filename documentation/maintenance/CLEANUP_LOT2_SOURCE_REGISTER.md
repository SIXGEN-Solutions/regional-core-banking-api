# Assainissement — traçabilité des sources du lot 2

> Inventaire généré automatiquement à partir des fichiers présents localement.
> Les hashes SHA-256 ci-dessous permettent de vérifier l'absence de réécriture
> des documents sources pendant la consolidation.

| Source | SHA-256 | Rôle |
| --- | --- | --- |
| `contracts/openapi/regional-core-banking-api-v1.yaml` | `a4dda86649b4571a378bc17322d9af50662189734ec68a01cb1cfbe68e09c2af` | REGIONAL_CANONICAL - approval metadata controls |
| `documentation/SOURCE_CLASSIFICATION.md` | `f3fb8e1445a7ce982c0b0bcfe824a1d311e25804c870c20e964c7421f5b1871b` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` | `4a1525b1edc33b10caa5a465b8540d2030b6484b30aa4d69e23df2002f01702f` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_6_2_MVP_CONTRACT_DECISIONS.md` | `60fe59d76ceb15b15ed91870c795ca7eb7e2bd054b11e3f654668644979e22a3` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_6_1_BKEVE_ANALYSIS.md` | `f1629df4dbb49cb8e138aab4d4bd51c1f27e0fa21e55d3382d325d0a5a4f5500` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv` | `1c65c7dfd74e944fae09b5d8f1172b78606b7042fde2c6be65bb0ccaf0594f02` | BANK_REFERENCE / mapping evidence; status not promoted |
| `documentation/architecture/R6_6_10_ACTIVATION_DECISION.md` | `8b0b025968f026439249476cff205cf606452c7fe5305a151f7c2bb89eeb64bc` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_6_10_TRANSACTION_WRITE_ASSEMBLY.md` | `90cfa37633aa66136a121b905f61719c32b5f2ef431ef5a1720c51261719e0ad` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/architecture/R6_6_5_SINGLE_INSTANCE_TEMPORARY_GUARD.md` | `c16e79b2cc18cf12080d0ea863ff6e06244070fb0a1ac2388eafb47c5244178e` | REGIONAL_DOCUMENT / preserve original status |
| `documentation/runbooks/R6_6_10_CONTROLLED_ACTIVATION.md` | `8546ee83897b996f517646aaeee75ac6ababe2f7d22412c568c4cc2cb87b32fd` | REGIONAL_DOCUMENT / preserve original status |
| `src/main/java/com/regional/corebanking/payment/application/service/PaymentExecutionService.java` | `4634855da4d98800f19e491f3f9f7c39bb03f2f7d5507fad9e5b8b5a3dbef830` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/application/service/PaymentExecutionRecoveryCoordinator.java` | `3b785576ddbeb86d31da3bc413ff57a2523674943bd6839408fa8e0b459cc741` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/amplitude/JdbcPaymentExecutionBankingAdapter.java` | `b56a9d2413e11412f7b4fb61057882fb764fecdbf25c949c11ad78de075ab35f` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/amplitude/PaymentExecutionBankingConfiguration.java` | `a938475fa52b9e9552111edacdde227c3b68dd4f0a7cd3176771f6dada332f89` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/amplitude/JdbcBkeveTransactionCoordinator.java` | `60715096e0f06cb8cb310e3bf85030f870a6226b8ce7b220651fdeaab3ea3a0a` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/amplitude/BkeveTransactionWriteAssembly.java` | `b8d5f7ff2d279d87e4ee8c92090a537395582f4fda783ed89ddf6a5c8ba1d3ef` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/amplitude/BkeveJdbcParameterMapper.java` | `eee1d44ad96a8dd86040994925448184a426794cdf8f1a542c5e4d638e9d34b9` | IMPLEMENTATION_EVIDENCE |
| `src/main/java/com/regional/corebanking/payment/infrastructure/persistence/JdbcPaymentEventNumberReservationAdapter.java` | `5e5e8252d5c92f640165d6fb20a2f6af7b1776ffda0ea691957e8c53bf4f83ec` | IMPLEMENTATION_EVIDENCE |
| `src/test/java/com/regional/corebanking/payment/infrastructure/amplitude/R6610OraclePaymentIT.java` | `c0b93b6265f8287e10f4184bd3967d8d8124f6741af148659523bc274a3a161d` | IMPLEMENTATION_EVIDENCE |

## Contrôle de portée
- Révision de référence : `d31f42e91305e30b2f5f01c7f9073ea2bfc6ae3b`.
- Aucun document source n'est modifié, déplacé ou supprimé.
- La conservation des sources de référence ne confère aucune approbation bancaire.
- Tous les liens dans les deux nouvelles synthèses sont à vérifier avant lot 3.
