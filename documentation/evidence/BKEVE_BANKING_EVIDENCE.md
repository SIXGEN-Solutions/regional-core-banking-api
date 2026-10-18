# R6.6.1 — Matrice de traçabilité BKEVE (233 positions)

> **ANALYSE / NON APPROUVÉ — ne constitue ni un schéma physique validé ni une autorisation de postage.**

## Traçabilité et périmètre

- Repository d'implémentation : `SIXGEN-Solutions/regional-core-banking-api`.
- Base GitHub lue en mode lecture seule : `feat/payment-orchestration-worflow @ b12bb5d525be6d2684e2bd5b03098b811ebc2f1b`.
- Source legacy fournie par l'utilisateur : `payment(1).java`, extrait composé de code client et de code CBS ; **preuve historique, non code Regional canonique**.
- Références associées : `BKEVE(1).txt`, `builevenement_imple.java`; la présente extraction ordonnée utilise `payment(1).java`.
- Contrat régional consulté : `contracts/openapi/regional-core-banking-api-v1.yaml`, sans modification.
- Exactement **233** colonnes explicitement nommées dans `INSERT INTO BKEVE` et **233** affectations ordonnées `values[0..232]` contrôlées à l'extraction.
- La présence d'une valeur initiale dans `BkeveBase` **ne prouve pas** sa validité bancaire pour les autres consommateurs.

## Règles de lecture de la matrice

Le tableau exhaustif est conservé dans `R6_6_1_BKEVE_MAPPING_233.csv` (UTF-8, import Excel possible).

- `CLIENT_DEBIT` / `CLIENT_CREDIT` : appel explicite à `setDebiteur` / `setCrediteur` dans `buildEvenement`, avec les affectations réellement montrées dans `BkeveBase`.
- `CLIENT_METIER` : affectation explicite dans `buildEvenement` (`LIB1`).
- `CLIENT_PROPOSE` : champs construits/initialisés côté appelant selon les sources, mais détails/valeurs et arbitrage encore ouverts.
- `CLIENT_CONSTRUCTEUR_A_VERIFIER` : `initBkeveBase` est visible dans le modèle legacy ; **son appel effectif par le constructeur concret `new bkeve(...)` n'est pas prouvé par le seul extrait fourni**.
- `CLIENT_DEFAUT_LEGACY` : initialisation de `BkeveBase`, ou absence de valeur ; non automatiquement autoritative.
- `REGIONAL_AUTORITATIF` : champs explicitement réaffectés par `processPOST` ; valeur finale à protéger côté Core Banking.
- `CONDITIONNEL_LEGACY` : réécriture uniquement dans certaines branches de `processPOST`, **à ne pas généraliser**.

## Synthèse du parcours observé

1. Le client exécute `buildEvenement(transaction)`, construit les comptes débiteur/créditeur et les écritures `bkmvti` T1, puis sérialise `bkeve` pour `POST /event` (chemin **legacy**).
2. L'API legacy `provessEvent` recherche un éventuel doublon via `id`, réinitialise `suspendInTFJ`, affecte `RLET` puis appelle `processPOST`.
3. `processPOST` contrôle fermeture/oppositions/fonds, calcule `DCO`, `DVA1`, `DVA2`, `DOU`, remplace `EVE`, renseigne `AD1P`/`AD2P`; `TYP` et plusieurs champs changent selon code/nature d'opération.
4. L'API prépare l'INSERT BKEVE (jour/nuit selon état TFJ) et les écritures de solde indicatif. `executeUpdateSystemQuery(dsCBS, querries)` est appelé ; **le comportement atomique exact de son implémentation n'est pas présent dans l'extrait**.
5. Après retour `processPOST`, le code legacy enregistre l'objet via `bkeveEjb.create(eve)`. La coordination de ces deux persistances n'est pas démontrée.

## Conformité au périmètre Regional

- La construction métier reste **chez chaque client appelant**. Regional ne doit pas reconstruire un modèle métier MTN/SIXPAY.
- Le contrat actuel `PaymentProviderEvent` contient **neuf attributs** et `additionalProperties: false`. Il ne peut pas accueillir tels quels les 233 champs physiques ; aucun changement contractuel n'est autorisé par ce document.
- `EVE`, date comptable et mode nuit sont autoritatifs CBS ; il faut préserver cette propriété malgré les éventuelles valeurs préliminaires reçues.
- `BKMVTI` reste côté client / T1 ; aucune écriture BKMVTI dans le postage R6.6 T0.
- La présence de commissions/taxes/virement interbancaire dans le legacy est **hors périmètre R6.6** et ne vaut pas approbation de leur exécution.
- Les champs `AGSA`, `AGE` et `CSP1` gardent leur provenance historique et leur statut d'arbitrage, sans composant Resolver artificiel.

## Résumé par type de responsabilité (classification de travail)

- `CLIENT_CONSTRUCTEUR_A_VERIFIER` : 8 colonnes.
- `CLIENT_CREDIT` : 11 colonnes.
- `CLIENT_DEBIT` : 11 colonnes.
- `CLIENT_DEFAUT_LEGACY` : 176 colonnes.
- `CLIENT_METIER` : 1 colonnes.
- `CLIENT_PROPOSE` : 6 colonnes.
- `CONDITIONNEL_LEGACY` : 9 colonnes.
- `REGIONAL_AUTORITATIF` : 11 colonnes.

## Extrait de lecture (la totalité se trouve dans le CSV)

| Position | Colonne | Valeur initiale observée | Propriété proposée | Affectation / réécriture |
|---:|---|---|---|---|
| 1 | `AGSA` | `"00001"` | `CLIENT_PROPOSE` | aucune affectation directe prouvée |
| 2 | `AGE` | `"00001"` | `CLIENT_PROPOSE` | aucune affectation directe prouvée |
| 3 | `OPE` | `` | `CLIENT_PROPOSE` | aucune affectation directe prouvée |
| 4 | `EVE` | `""` | `REGIONAL_AUTORITATIF` | processPOST |
| 5 | `TYP` | `"100"` | `CONDITIONNEL_LEGACY` | processPOST |
| 7 | `AGE1` | `` | `CLIENT_DEBIT` | setDebiteur |
| 9 | `NCP1` | `` | `CLIENT_DEBIT` | setDebiteur |
| 17 | `MON1` | `0d` | `CLIENT_DEBIT` | setDebiteur+processPOST |
| 18 | `DVA1` | `new Date()` | `REGIONAL_AUTORITATIF` | processPOST |
| 19 | `EXO1` | `"N"` | `REGIONAL_AUTORITATIF` | processPOST |
| 28 | `AGE2` | `` | `CLIENT_CREDIT` | setCrediteur |
| 30 | `NCP2` | `` | `CLIENT_CREDIT` | setCrediteur |
| 38 | `MON2` | `0d` | `CLIENT_CREDIT` | setCrediteur |
| 39 | `DVA2` | `new Date()` | `REGIONAL_AUTORITATIF` | processPOST |
| 41 | `EXO2` | `"O"` | `REGIONAL_AUTORITATIF` | processPOST |
| 87 | `NAT` | `"VIR"` | `CLIENT_PROPOSE` | aucune affectation directe prouvée |
| 93 | `DCO` | `` | `REGIONAL_AUTORITATIF` | processPOST |
| 103 | `LIB1` | `"OPERATION BUS "` | `CLIENT_METIER` | buildEvenement |
| 127 | `AD1P` | `HelperQuerry.padText("", HelperQuerry.RIGHT, 30, " ")` | `REGIONAL_AUTORITATIF` | processPOST |
| 128 | `AD2P` | `HelperQuerry.padText("", HelperQuerry.RIGHT, 30, " ")` | `REGIONAL_AUTORITATIF` | processPOST |
| 161 | `CSP1` | `"VBUSS"` | `CLIENT_PROPOSE` | aucune affectation directe prouvée |
| 173 | `EXOF` | `" "` | `REGIONAL_AUTORITATIF` | processPOST |

## Limite explicite

Ce document est un **inventaire de preuve** : la propriété « proposée » n'est pas encore une décision bancaire approuvée. La matrice ne permet ni de générer un nouveau contrat, ni d'activer des écritures financières.
