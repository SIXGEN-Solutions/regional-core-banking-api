# R6.6.2 — Consolidation des décisions MVP et gates du contrat événementiel

> **ADDENDUM R6.6.2 — CONTRAT APPROUVÉ ET INTÉGRÉ (14 octobre 2026)**  
> Les sections historiques ci-dessous consignent l'état *avant* l'approbation et ne doivent
> pas être interprétées comme le statut actuel du contrat. La version exacte de
> `PaymentProviderEvent` a été approuvée par le propriétaire du projet puis
> intégrée au contrat canonique à la révision de base `e4c9d17a0ff88c6b2af6895d8d3711607db3a814`.
> `amount` métier est absent de l'événement ; `debitAmount` = MON1 = MHT1 ;
> `creditAmount` = MON2 = MHT2. Les deux montants sont indépendants et transmis
> par le client. `agsa`, `age`, `csp1` sont requis et fournis par le client.
> Enrichissement bancaire Regional, MVP mono-agence sans comptes de liaison,
> écritures BKMVTI stockées côté client jusqu'à T1. Aucun nouveau postage bancaire
> ou mécanisme de recovery n'est autorisé par cet alignement Java.
> Compatibilité avec le JSON réel de SIXPAY et décisions bancaires de postage :
> **toujours à vérifier** avant activation financière.

**État : DRAFT / PENDING_APPROVAL.** Document de revue, pas une approbation du contrat canonique ni une autorisation de génération ou d'implémentation bancaire.

## Révision et source

- Repository : `SIXGEN-Solutions/regional-core-banking-api`.
- Branche observée : `feat/payment-orchestration-worflow`.
- SHA observé : `e9f39fc53668bc82dfea9ba5cbb58c0f12487160`.
- Mode : analyse GitHub en lecture seule et production d'artefacts locaux non appliqués.
- Base : `contracts/openapi/regional-core-banking-api-v1.yaml`, proposition R6.6.2, checklist C-01 à C-10, matrice R6.6.1, décisions utilisateur fournies en discussion.

## Décisions MVP communiquées par le propriétaire du projet

- T0 : BKEVE est construit et posté lors de l'appel à l'API Payment Events. Les écritures BKMVTI sont préparées à la construction de BKEVE, mais leur transmission comptable est réservée à T1.
- Conservation durable BKMVTI **côté client** ; pas de transmission de `providerEntries` à T0 et pas de stockage BKMVTI chez Regional pour ce MVP.
- **Mono-agence seulement**. Aucun compte de liaison ; les comptes interagences et les écritures de liaison sont reportés à une évolution future.
- Noyau des neuf champs `PaymentProviderEvent` confirmé.
- `MHT1/MON1` : montants à débiter ; `MHT2/MON2` : montants à créditer ; précision indiquée `DECIMAL(19,4)` pour chacun. L'identité ou la différence entre `MHT` et `MON` par jambe n'est pas définie par cette seule confirmation.
- Types fournis : `DEV1 CHAR(3)` valeur legacy par défaut `001`, `SUF1 CHAR(2)`, `CLI1 CHAR(15)`, `NOM1 CHAR(36)`, `GES1 CHAR(3)`, `SOL1 DECIMAL(19,4)` ; ne pas présumer l'autorité ni la nécessité de transmission de ces champs.
- Valeurs communiquées pour le parcours SIXPAY : `CSP1 = VSixpay`, `AGSA = 00001`, `AGE = 00001`. Pas de valeur globale universelle pour les autres consommateurs Regional.
- Champs bancaires autoritatifs restent gérés côté Regional, notamment EVE, DCO, dates de valeur et mode jour/nuit.
- `C-08 = json` confirme le format de transport, **pas** les propriétés réellement sérialisées par le client SIXPAY actif.

## Compatibilité et incohérences à lever

1. Le schéma régional actuel est fermé et exige neuf champs. Ne pas introduire de champs additionnels obligatoires ni modifier les neuf sans approbation du YAML exact.
2. Le choix de stockage client évite le besoin de `providerEntries` dans le POST T0.
3. `AccountingEntriesPaymentItem.entries` impose exactement deux éléments dans le contrat T1 ; les deux legs simples du MVP suggèrent un débit et un crédit, mais une vérification métier du périmètre d'opérations reste nécessaire avant de généraliser.
4. Il faut distinguer la **forme de transport** des données bancaires enrichies par Regional : des champs du BKEVE legacy ne doivent pas être reproduits mécaniquement dans le JSON.
5. La confirmation « C-10 : oui » porte sur le principe de la révision. L'approbation de la **nouvelle version YAML exacte** n'a pas encore été donnée.
6. Aucun contrat signé de compatibilité du client SIXPAY actuel n'a été produit : ne pas déclarer C-08/C-09 clos.

## Proposition d'architecture de transport

Conserver les neuf champs existants et `additionalProperties: false`. Évaluer des propriétés additionnelles **facultatives, explicitement typées** uniquement si l'enrichissement bancaire ne permet pas de les obtenir, après arbitrage du propriétaire de la donnée et examen du JSON émis par SIXPAY. Ne pas accepter une map arbitraire de 233 colonnes BKEVE. Ne pas coder `VSixpay`, `00001` ou `001` en valeurs générales de la spécification Regional.

## Gate de sortie avant mutation canonique

- Inventaire final des champs supplémentaires à transporter, noms JSON, propriétaires, `required`, maximums, types, précision et nullabilité.
- Réconciliation `amount` versus MHT1/MON1/MHT2/MON2 et politique d'arrondi.
- Preuve des requests/réponses client SIXPAY actuelles ; tests de compatibilité des schémas et statuts.
- YAML de révision précis, approuvé explicitement par l'humain.
- Après approbation seulement : patch du canonique, génération limitée à l'interface HTTP, tests ciblés puis gates élargis.

**Conclusion :** les décisions MVP sont consolidées, mais le contrat révisé reste `PENDING_APPROVAL`. Aucun postage Amplitude n'est autorisé par ce document.
