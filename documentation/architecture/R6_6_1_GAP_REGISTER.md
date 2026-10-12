# R6.6.1 — Registre des écarts, conflits et décisions

> **ANALYSE / PENDING_REVIEW**. Les constats sont rattachés aux extraits legacy transmis et au repository Regional lu au SHA de départ. Les propositions ci-dessous ne sont pas des décisions approuvées.

| ID | Gravité | Constat étayé | Risque | Classe de décision requise | Action / preuve attendue |
|---|---|---|---|---|---|
| GAP-01 | BLOQUANT_CONTRAT | `PaymentProviderEvent` régional n'expose que 9 champs (`additionalProperties: false`) ; le client legacy sérialise l'objet `bkeve` avec de nombreuses propriétés. | Perte des sémantiques métier client ; impossibilité de préserver tel quel le wire legacy. | Approbation de contrat + compatibilité consommateur | Élaborer une évolution minimale du contrat en R6.6.2 ; ne toucher ni aux DTO SIXPAY ni au contrat actuel sans approbation. |
| GAP-02 | A_ARBITRER | `AGSA=00001`, `AGE=00001` dans `BkeveBase`. Aucun mapping multi-client approuvé. | Généralisation d'une agence legacy. | Preuve bancaire La Régionale | Conserver provenance et valeurs legacy *du parcours concerné* ; arbitrer leur portée. |
| GAP-03 | CONFLIT_SOURCE | `CSP1=VBUSS` dans `BkeveBase` du fichier `payment(1).java`, contre `VMoMo` observé dans l'autre source `BKEVE(1).txt`. | Écrasement du marqueur métier et erreur de routage/comptabilisation. | Preuve bancaire / compatibilité consommateur | Classer `CSP1` propriété de l'appelant jusqu'à clarification ; **ne pas sélectionner silencieusement une valeur universelle**. |
| GAP-04 | BLOQUANT_PROD | `lastNumEveOpe()` côté client lit un compteur, mais `processPOST` recalcule `EVE` via branches `OPE=001` / autre ; `getNumEvenementEODProcess` utilise un `synchronized` JVM et des accès distincts. | Collision inter-instances ; perte des garanties d'atomicité. | Décision architecture + preuve bancaire | Concevoir attribution EVE atomique DB, justifier clé/unicité, couvrir course jour/nuit ; ne pas réintroduire endpoint historique. |
| GAP-05 | BLOQUANT_PROD | `checkedExistedEve` vérifie `OPE,EVE,NCP1,NCP2,MON1` avant réattribution EVE, puis INSERT. | Détection de doublon insuffisante et TOCTOU. | Preuve bancaire + architecture recovery | Définir source de vérité et contrainte unique, vérifier le point de contrôle pertinent. |
| GAP-06 | BLOQUANT_PROD | `executeUpdateSystemQuery(dsCBS, querries)` n'est pas fourni ; l'extrait ne démontre pas sa transaction JDBC ni son rollback. | Événement inséré sans solde ou inversement. | Preuve bancaire / transaction Informix | Obtenir implémentation et SQL exact des mises à jour, valider atomicité et verrouillage avant écriture. |
| GAP-07 | BLOQUANT_PROD | `bkeveEjb.create(eve)` suit `processPOST` et vise `API_BUS_eve`. Aucune coordination du commit avec Informix démontrée. | Événement bancaire exécuté mais référence technique absente. | Architecture recovery/idempotence | Valider persistance technique, corrélation `paymentReference -> OPE/EVE/DCO`, recovery après échec partiel. |
| GAP-08 | BLOQUANT_CONTRAT | Le legacy retourne HTTP 200 avec champ métier `code`, alors que le contrat Regional définit `PaymentEventResult` et `COMPLETED/REJECTED/UNKNOWN`. | Confusion succès HTTP et effet financier ; incompatibilité. | Contrat + vérification compatibilité consommateur | Ne pas importer l'enveloppe legacy ; documenter écarts wire pour consommateurs concernés. |
| GAP-09 | A_VERIFIER | `buildEvenement` utilise un constructeur concret `new bkeve(...)`; sa définition n'est pas fournie dans l'extrait. | Attribution erronée des champs à `initBkeveBase` et fausse exhaustivité des initialisations. | Preuve legacy supplémentaire | Vérifier le constructeur du modèle `bkeve` côté client ou la classe complète déjà fournie. |
| GAP-10 | CONFLIT_SOURCE | `buildEvenement` renseigne `setSuspendInTFJ(true)` la nuit, l'API `provessEvent` réinitialise à `false` avant `processPOST`. | État de suspension client ignoré ; ambiguïté reprise TFJ. | Preuve bancaire / règles TFJ | Confirmer autorité de décision Regional et traitement après TFJ. |
| GAP-11 | HORS_PERIMETRE | `processPOST` contient des traitements interbancaires (`BKEVEC`, frais, TVA), `OPE=302`, `OPE=001`, solde minimum spécifique `BKHLV`. | Généralisation non autorisée des règles de branches particulières. | La Régionale / scope décision | Exclure frais/commissions R6.6 ; documenter et garder fail-closed pour cas non couverts. |
| GAP-12 | A_VERIFIER | `setDebiteur` et `setCrediteur` reçoivent `dva1/dva2` mais les affectations des dates y sont commentées ; l'API réécrit ces dates. | Dates client non garanties comme source finale. | R6.5 / preuve bancaire | Conserver DCO/DVA1/DVA2 autoritatifs Regional ; tester priorités. |
| GAP-13 | BLOQUANT_PROD | Les méthodes legacy de lecture soldes et d'opposition capturent parfois `SQLException` et retournent valeurs par défaut. | Acceptation ou rejet non fiable en présence de panne. | Sécurité financière / règles bancaires | Préserver fail-closed, ne pas inférer de réussite en cas d'erreur technique. |
| GAP-14 | A_VERIFIER | Le modèle source contient 233 valeurs `Double`/`String`/`Date` et padding `CHAR`, mais le schéma physique réel Informix n'est pas revérifié ici. | Perte de précision, troncatures, NULL/espaces mal gérés. | Preuve bancaire Informix | Tester types JDBC, arrondis/scale, encodage, dates, longueur, alignement et valeurs NULL. |
| GAP-15 | BLOQUANT_PROD | La persistance R6 est actuellement fail-closed : `JdbcPaymentExecutionBankingAdapter.execute` termine par `PaymentExecutionUnavailableException`, recovery bancaire `Optional.empty()`. | Fonctionnalité non opérationnelle malgré contrat existant. | Implémentation post-approbations | Ne pas activer jusqu'à garanties de commit, recovery et signature approuvée. |
| GAP-16 | DECISION_OUVERTE | `bankReference=OPE+EVE`, `COMPLETED` après commit ont été retenus dans les échanges, mais la preuve technique d'atomicité reste à vérifier. | Confirmation erronée après outcome ambigu. | Décision métier + validation technique | Tests de commit incertain, identifiants stables, contrôle autoritatif après panne. |
| GAP-17 | A_VERIFIER | `BKMVTI` est créé côté client et n'est pas inséré par le `POST /event` de l'API ; T1 séparé. | Mélange T0/T1 ou duplication des écritures. | Architecture / compatibilité | Conserver exclusion BKMVTI R6.6 ; maintenir séparation R7. |
| GAP-18 | A_VERIFIER | Le client utilise `/event` en legacy ; Regional expose `/api/v1/payment-events`. | Compatibilité wire non directe si même consommateur. | Compatibilité consommateur | Vérifier clients actuels (dont SIXPAY) avant toute évolution ; ne pas créer d'alias sans contrat. |

## Inventaire des sources et statuts

- `payment(1).java` (transmis par l'utilisateur) : `LEGACY_IMPLEMENTATION_EVIDENCE / REFERENCE_ONLY` tant que son applicabilité n'est pas confirmée.
- `BKEVE(1).txt` (transmis par l'utilisateur) : `LEGACY_IMPLEMENTATION_EVIDENCE / REFERENCE_ONLY` ; conserver divergences.
- `contracts/openapi/regional-core-banking-api-v1.yaml` : `REGIONAL_CANONICAL`, état approuvé pour la version actuellement présente ; **aucun changement implicite**.
- `documentation/architecture/R6_PAYMENT_EXECUTION_DECISION_MATRIX.md` : état/observations du programme à conserver, avec résolution formelle des conflits.
- SIXPAY : `SIXPAY_REFERENCE`, lecture seule ; identité Java jamais reprise comme modèle Regional.

## Décisions nécessaires avant R6.6.2

1. Contrat d'entrée client-agnostique transportant les attributs réellement nécessaires et leur cardinalité, sans imposer 233 propriétés si non justifiées.
2. Cas financiers acceptés en R6.6 et exclusions explicites : virement interne, cas particuliers, frais, compensations.
3. Origine et portée réelles de `AGSA`, `AGE`, `CSP1` en présence de plusieurs clients.
4. Allocation EVE atomique, tables autoritatives, contraintes d'unicité et comportement de rollover.
5. Transaction des soldes et recovery en cas d'issue inconnue.

**R6.6.1 reste une analyse, pas une décision d'implémentation ni un GO financier.**
