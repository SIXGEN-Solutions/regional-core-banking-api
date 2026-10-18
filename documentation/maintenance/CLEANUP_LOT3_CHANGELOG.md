# Cleanup — lot 3 : retrait documentaire

- Révision examinée : `feat/payment-orchestration-worflow @ 37bb78f087cb86e298666ea15470c6242071082a`
- Portée : documents uniquement, aucune source Java, SQL, OpenAPI ou CI modifiée.
- Conservation : les originaux restent disponibles dans l'historique Git.
- Inventaires lots 1 et 2 : conservés comme instantanés historiques.
- Décisions MVP, preuves BKEVE et activation réelle : conservées.

## Retraits

| Chemin | SHA-256 du contenu avant retrait |
| --- | --- |
| `documentation/architecture/R6_6_2_CONTRACT_EVOLUTION_PROPOSAL.md` | `52d3d7ae36d08b49969aa7885223a31b292c833f569b412adf5e2514e948c388` |
| `documentation/architecture/R6_6_2_OPENAPI_REVIEW_CHECKLIST.md` | `145311cbe98ed8dc9c4a07cfb17a4105940ff75ee5d3885f67d0a2b9b106d757` |
| `documentation/architecture/R6_6_6_BKEVE_JDBC_POSTING_PREPARATORY.md` | `e092c92c2a450dba5b5a64645fc87e29fc85a45ce0dcdf509fd8229feb63b1af` |
| `documentation/architecture/R6_6_7_INDICATIVE_BALANCE_TRANSACTION_PREPARATORY.md` | `fe920336acfceff9b6641194ecd9e5558c3f3a98a124408328863f237109b479` |
| `documentation/architecture/R6_6_8_RECOVERY_PREPARATORY.md` | `86601b6ffc2e4681f1bc8a37d44da3135e36c177505234160f0858a7d065c214` |

## Actualisations

- `README.md` : remplace le récit R6 par la documentation courante.
- Les deux plans et les documents de gouvernance restent inchangés.

## Validation

Vérifier `git diff --check`, `git diff --stat`, les liens locaux et l'absence de modifications des chemins `src/`, `contracts/`, `.github/` et `pom.xml`.
