# Regional Payment Event lookup — résultat, preuves historiques et recovery

Statut : décision humaine explicite, 7 novembre 2026, appliquée localement à partir de
`SIXGEN-Solutions/regional-core-banking-api`, branche
`feat/payment-orchestration-worflow`, SHA `77c7eeddff22538bd6aa8b54b3b3372de17b9488`.
Le contrat canonique demeure `contracts/openapi/regional-core-banking-api-v1.yaml`.

## Périmètre approuvé

- `GET /api/v1/payment-events/{paymentReference}` et
  `GET /api/v1/payment-events/idempotency/{idempotencyKey}` renvoient le même
  `PaymentEventResult` que le POST pour toute référence connue, avec `outcome`
  `COMPLETED`, `REJECTED` ou `UNKNOWN` selon les preuves disponibles.
- Les contrôles historiques enregistrés à l'exécution sont conservés sans
  recalcul, modification ou enrichissement artificiel.
- Une récupération bancaire peut certifier `COMPLETED` sans les contrôles
  historiques. Le tableau `checks: []` signifie alors *contrôles non
  disponibles*, et non une réussite des contrôles. Trois contrôles sont
  conservés exactement lorsqu'ils ont été enregistrés ; une longueur partielle
  de 1 ou 2 est considérée incohérente plutôt que complétée artificiellement.
- Référence de paiement ou clé d'idempotence totalement inconnue : HTTP 404
  avec `outcome: UNKNOWN` et `checks: []`, jamais `REJECTED`.
- Pour la recherche par clé inconnue, le corps 404 n'invente pas de
  `paymentReference` ; cette propriété est absente.
- `REFERENCE_UNKNOWN` est exclu : aucun nouveau `reasonCode` n'est introduit.
- Indisponibilité de la banque ou preuve `COMPLETED` insuffisante : erreur
  technique HTTP 503, sans transformer le résultat en `REJECTED`.
- Les GET restent en lecture seule, sans appel au port bancaire d'exécution.
  Une absence immédiate d'événement ne prouve pas l'absence d'écriture.

## Contrat

- Pour les résultats connus, `PaymentEventResult` conserve les propriétés et
  l'énumération d'issue existantes. Seule la cardinalité de `checks` permet
  désormais l'absence documentée des contrôles historiques.
- Pour le 404, `PaymentEventLookupNotFound` reprend les champs utiles du résultat
  mais rend `paymentReference` facultatif, car une clé inconnue ne permet pas
  d'établir la référence du paiement. Ce n'est pas un événement bancaire
  `COMPLETED` ni une preuve d'absence définitive de postage.
- Le POST conserve son contrat de statut HTTP 200/202 et ses contrôles réels.

## Vérifications locales à effectuer

1. `PaymentExecutionDcoMapperTest` : contrôles historiques, recovery sans
   contrôles, preuve COMPLETED/DCO, incohérence partielle.
2. `PaymentExecutionRecoveryCoordinatorTest` : UNKNOWN conservé, référence
   absente, pas de réexécution.
3. `PaymentExecutionLookupHttpSemanticsTest` : 404/UNKNOWN et 503.
4. `SwaggerUiCanonicalContractTest`, puis `mvnw verify`.

Aucune activation du postage réel ni changement de règles Amplitude/Informix
n'est autorisé par cet alignement.
