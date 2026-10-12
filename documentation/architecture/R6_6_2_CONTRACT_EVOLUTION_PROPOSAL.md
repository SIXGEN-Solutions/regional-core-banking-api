# R6.6.2 — Proposition de revue du contrat événementiel

> **DRAFT / PENDING_HUMAN_APPROVAL / REFERENCE_ONLY — codeGenerationAllowed: false pour cette proposition.** Ce document n'altère pas le statut ni le contenu du contrat OpenAPI Regional actuellement approuvé. Ce n'est ni une décision bancaire ni une autorisation de génération, de postage, de migration ou de déploiement.

## Révisions et périmètre

- Implémentation : `SIXGEN-Solutions/regional-core-banking-api`.
- Branche demandée : `feat/payment-orchestration-worflow`.
- SHA fourni (antérieur) : `b12bb5d525be6d2684e2bd5b03098b811ebc2f1b`.
- HEAD GitHub effectivement observé : `90e21a3b2ecaccd1b8ab8ade1d765b75495ccc96` (un commit en avance, uniquement les 3 livrables R6.6.1).
- Mode : proposition documentaire, lecture seule du distant, aucun worktree contrôlé.
- Source canonique actuellement approuvée : `contracts/openapi/regional-core-banking-api-v1.yaml`.
- Preuve de traçabilité : `R6_6_1_BKEVE_MAPPING_233.csv`, `R6_6_1_BKEVE_ANALYSIS.md`, `R6_6_1_GAP_REGISTER.md` ; sources legacy `payment(1).java`, `BKEVE(1).txt` : historiques, applicabilité bancaire à confirmer.
- Référence consommateur : `contracts/reference/sixpay/amplitude-payment-posting-api-v1.yaml` (`SIXPAY_REFERENCE / REFERENCE_ONLY`), source du starter pack historique SIXPAY `main @ b6da7db33432cb81997cc293b21080dd46fdcc14` ; vérifier les clients SIXPAY **actuels** avant toute conclusion de compatibilité.

## Découvertes certaines

1. `POST /api/v1/payment-events` accepte `PaymentEventEnvelope` comportant `paymentReference`, `snapshotVersion`, `providerEvent` et `requestedAt`. Les deux endpoints GET de récupération restent inchangés.
2. `PaymentProviderEvent` expose 9 champs obligatoires : `paymentReference`, `operationCode`, `currency`, `nature`, `technicalUser`, `debtorAccountReference`, `creditorAccountReference`, `amount`, `label`, avec `additionalProperties: false`.
3. Le legacy client initialise de nombreux attributs événementiels (`AGE1`, `DEV1`, `NCP1`, `SUF1`, `CLC1`, `CLI1`, `NOM1`, `GES1`, `MHT1`, `MON1`, `SOL1`, leurs homologues crédit, `AGSA`, `AGE`, `CSP1`, etc.). Tous ces attributs n'ont pas d'équivalent transport dans le schéma approuvé. **Il n'est pas démontré qu'ils doivent tous traverser l'API ou être tous obligatoires.**
4. L'API historique réaffecte `EVE`, `DCO`, `DVA1`, `DVA2`, `DOU`, `AD1P`, `AD2P` et des valeurs techniques. Ces champs ne doivent pas être traités comme autoritatifs lorsque fournis par un client.
5. `BKMVTI` relève de T1 et non du postage T0 ; aucun `providerEntries` ne doit être ajouté pour R6.6.
6. `CSP1` est contradictoire (`VBUSS` versus `VMoMo`) ; `AGSA` et `AGE` ont des valeurs legacy sans portée multi-client approuvée. Ne pas attribuer de valeur universelle.

## Inventaire des écarts de transport

| Famille | Champs BKEVE attestés | Contrat existant | Recommandation de revue (NON APPROUVÉE) |
|---|---|---|---|
| Métier événement | `OPE`, `NAT`, `UTI`, `LIB1` | `operationCode`, `nature`, `technicalUser`, `label` | **Conserver** les quatre concepts ; valider mapping exact, type et longueur. |
| Références de comptes | `AGE1/NCP1/CLC1`, `AGE2/NCP2/CLC2` | `debtorAccountReference`, `creditorAccountReference` | **Conserver** les références existantes ; valider besoins réels des autres composantes. |
| Attributs complémentaires des comptes | `DEV1`, `SUF1`, `CLI1`, `NOM1`, `GES1`, `SOL1`; équivalents crédit | Aucun champ équivalent explicite | **Candidats** à représentation explicite seulement si propriété client requise, et après examen de l'enrichissement bancaire et des risques de données obsolètes. |
| Montants des deux legs | `MHT1`, `MON1`, `MHT2`, `MON2` | Un seul `amount` | **Écart majeur** : vérifier la sémantique exacte des quatre montants et les cas supportés ; ne pas supposer leur égalité. |
| Marqueurs du client et agences | `AGSA`, `AGE`, `CSP1` | Aucun | **Candidats** à attributs optionnels explicites, après décision bancaire (pas de defaults universels). |
| Caractéristiques de l'événement | `DEV`, `MHT`, `MNAT`, `MBOR`, `TYP` | `currency`, `amount`, `nature` (partiellement) | Correspondance non prouvée ; étudier uniquement les champs réellement nécessaires. |
| Contexte CBS | `EVE`, `DCO`, `DVA1`, `DVA2`, `DOU`, `AD1P`, `AD2P` | Absents intentionnellement | **Conserver absents de l'entrée** ; valeurs finales autoritatives Regional. |
| Commissions, taxes et variantes | `TCOM1`, `FRAI1`, `TTAX1`, `MNT1`, `MNT5`, `CCO`, etc. | Absents | **Hors périmètre R6.6** ; ne pas réintroduire par contrat implicite. |
| Écritures T1 | `BKMVTI` | Absentes du Regional T0 | **Conserver séparation R7**. |
| Métadonnées de protocole | `paymentReference`, `snapshotVersion`, `requestedAt`, `Idempotency-Key` | Déjà définies | **Conserver** ; ne pas confondre `paymentReference` de l'enveloppe et de `providerEvent`. |

## Proposition d'évolution minimale, en deux temps

### A. Choix compatibles immédiatement (sans changement contractuel)

- Maintenir les opérations, headers, réponses, sécurité et obligations existants.
- Conserver la construction métier chez le client ; la validation et l'enrichissement bancaire côté Regional.
- Confirmer le mapping du noyau existant avec le ledger R6.6.1 ; ne pas importer les DTO Java SIXPAY.
- Geler les scénarios non représentables par les neuf attributs ; ne pas extrapoler avec des defaults implicites.

### B. Candidats de contrat soumis à décision humaine

La matrice ne permet pas encore de fixer une *nouvelle* signature valide. Le modèle de révision recommandé est une **extension explicite et typée du schéma existant `PaymentProviderEvent`**, limitée aux champs client nécessaires et justifiés par des cas bancaires validés. Les groupes candidats issus du legacy sont :

1. Identification client/agence : `AGSA`, `AGE`, `CSP1` ; légitimité, propriété et cardinalité à confirmer.
2. Montants des jambes : `MHT1`, `MON1`, `MHT2`, `MON2` ; sens, arrondis, règles jour/nuit à confirmer.
3. Attributs des jambes débiteur et créditeur non dérivables de manière autoritative : `SUF1/2`, `DEV1/2`, `CLI1/2`, `NOM1/2`, `GES1/2`, `SOL1/2` ; cas d'usage et confiance à confirmer avant sélection.
4. Métadonnées client nécessaires non représentées par les neuf attributs : `DEV`, `MHT`, `MNAT`, `MBOR` et autres lignes `CLIENT_CONSTRUCTEUR_A_VERIFIER` ; vérifier le constructeur concret avant promotion contractuelle.

**Aucun nouveau champ ni schéma n'est ajouté à l'OpenAPI canonique dans R6.6.2.** La sélection finale, les noms de transport (non nécessairement identiques à BKEVE), les contraintes (`required`, nullable, format, précision) et la compatibilité sont des décisions à approuver. Ne pas utiliser `additionalProperties: true` pour contourner cette validation ; ne pas transporter les 233 colonnes brutes par défaut.

## Alternatives à arbitrer

| Option | Évaluation |
|---|---|
| A — Conserver strictement les 9 champs | Compatibilité Regional existante maximale, mais impossible d'attester la préservation des initialisations client legacy pour tous les cas. |
| B — Extension explicite de `PaymentProviderEvent` (recommandée sous réserve d'approbation) | Préserve le contrat de base et permet les attributs métier prouvés ; exige sélection, typage, analyse de compatibilité, validations et approbation. |
| C — Objet ouvert `additionalProperties: true` / BKEVE brut | Ressemble au legacy, mais laisse passer des propriétés CBS autoritatives, complique la validation, la sécurité et la compatibilité ; **non recommandé**. |

## Décisions demandées à La Régionale et à l'architecture Regional

- Quels scénarios T0 R6.6 doivent être couverts, précisément, hors frais/taxes ?
- Quelles propriétés construites par chaque client doivent impérativement traverser l'API et ne peuvent être résolues de façon autoritative par le Core Banking ?
- Quelle est la sémantique bancaire des montants `MHT1`, `MON1`, `MHT2`, `MON2` en contexte retenu ?
- Quel rôle et quelle portée ont `AGSA`, `AGE`, `CSP1` ? Quelle source tranche `VBUSS` / `VMoMo` ?
- Quel mécanisme de compatibilité sera accepté pour les clients existants (ajout facultatif, versionnement, migration) ?
- Quel est le mapping réel des clients SIXPAY actuels : HTTP, headers, JSON obligatoire, types, erreurs, résultats, idempotence et recovery ?

## Garde-fous de la proposition

- **Aucune mutation** de `contracts/openapi/regional-core-banking-api-v1.yaml`.
- Aucune création d'endpoint ou de nouveau schéma OpenAPI.
- Aucune génération d'interface, DTO, SQL, migration ou code d'implémentation.
- Aucune déclaration d'approbation de la proposition ; les approbations antérieures restent limitées au contrat actuellement en vigueur.
- La preuve historique ne devient pas une preuve de règles bancaires multi-clients par simple présence dans le dépôt.

## Critère de sortie R6.6.2

Proposition documentaire prête à être soumise à une revue humaine. **Non prête pour génération** tant que le périmètre des champs, les règles de montants, les conflits de source et la compatibilité consommateur ne sont pas validés.
