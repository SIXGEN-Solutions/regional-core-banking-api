# R6.6.2 — Analyse de compatibilité SIXPAY / Regional

> **REFERENCE_ONLY / ANALYSE / NON APPROUVÉ**. La référence copiée SIXPAY ne prouve pas les requêtes effectivement émises par les versions déployées actuellement.

## Sources

- Contrat Regional : `contracts/openapi/regional-core-banking-api-v1.yaml` observé sur `regional-core-banking-api @ 90e21a3b2ecaccd1b8ab8ade1d765b75495ccc96`.
- Référence copiée de SIXPAY : `contracts/reference/sixpay/amplitude-payment-posting-api-v1.yaml`, attribuée par le manifest à `sixpay-connect main @ b6da7db33432cb81997cc293b21080dd46fdcc14` ; `SIXPAY_REFERENCE / REFERENCE_ONLY`.
- Legacy historique utilisateur : `payment(1).java` (ancien `POST /event`, réponse métier `code/eve`) ; ne représente pas nécessairement le SIXPAY actuel.
- Inventaire de traçabilité : R6.6.1, lecture au HEAD Regional `90e21a3...`.

## Comparaison de contrats

| Aspect | Regional approuvé | SIXPAY référence historique | Compatibilité / action |
|---|---|---|---|
| POST | `/api/v1/payment-events` | `/api/v1/payment-events` | Chemin/méthode cohérents dans les deux contrats. |
| GET paymentReference | `/api/v1/payment-events/{paymentReference}` | Identique | Cohérence de route ; vérifier réponses/erreurs. |
| GET Idempotency-Key | `/api/v1/payment-events/idempotency/{idempotencyKey}` | Identique dans la référence | Cohérence de route ; vérifier idempotence réelle. |
| Headers | Corrélation, institution, idempotence | Équivalents conceptuels | Vérifier exactes contraintes, valeurs et transport des clients actuels. |
| `PaymentEventEnvelope` | `paymentReference`, `snapshotVersion`, `providerEvent`, `requestedAt` | Les mêmes **plus `providerEntries` obligatoire** | **Incompatibilité de forme** : propriété absente/fermée côté Regional ; cette référence ne prouve pas qu'elle est encore émise. |
| `providerEvent` | 9 propriétés requises, objet fermé | Objet libre `additionalProperties: true`, fields non détaillés | **Risque élevé** : un payload legacy enrichi peut être rejeté. Mapper actualisé SIXPAY indispensable. |
| `providerEntries` | Absent intentionnellement (BKMVTI = T1) | Présent et requis dans la référence | Décision R6 déjà actée côté Regional ; ne pas ajouter en T0 sans nouvelle approbation. |
| Contrôles `checks` | Exactement 3 résultats | Exactement 8 résultats | **Incompatibilité contractuelle potentielle** ; vérifier consommateurs des réponses actuelles. |
| `outcome` | `COMPLETED`, `REJECTED`, `UNKNOWN` | Identiques | Sémantique de recovery globalement alignée au niveau nominal. |
| `bankReference` | Optionnel / nullable | Optionnel / nullable | Source bancaire/règle de confirmation toujours dépendantes de décision validée. |
| HTTP success/inconclusif | `200` / `202` | `200` / `202` | Alignement nominal ; vérifier erreurs 4xx/5xx et gestion des retries. |
| Legacy plus ancien | Aucune route `POST /event` | Client legacy historique utilise `/event` | **Pas de compatibilité implicite** avec ce client ; aucun alias à créer sans approbation. |

## Mesure d'impact d'une extension optionnelle

- L'ajout de propriétés optionnelles à `PaymentProviderEvent` **peut** conserver l'acceptation des requêtes conformes existantes si aucune contrainte actuelle n'est modifiée.
- Il ne garantit **pas** la compatibilité des DTO générés et des parseurs de réponse, ni celle des clients qui transmettent déjà des propriétés excédentaires, ni des anciens consommateurs de huit checks.
- Toute évolution de `required`, type, montants, headers, idempotence, `providerEntries`, `additionalProperties` ou réponse pourrait rompre des clients : demander vérification par requêtes capturées ou tests de contrat du consumer actif.
- Les classes Java des clients ne seront ni importées ni modifiées. Si une évolution demande une mise à jour d'un client SIXPAY, cette tâche ne fait pas partie de ce lot et exige autorisation séparée.

## Matrice de vérification client (non exécutée)

| Vérification | Attendu | Statut |
|---|---|---|
| Requête POST réellement émise par SIXPAY actuel | Path, headers, body JSON, types, nulls | À collecter / vérifier en lecture seule |
| Tests consumer de sérialisation | Conformité du JSON actuel au Regional V1 | Non exécuté |
| Identité/fingerprint/idempotence | Même opération et même clé, recovery avant rejeu | À confronter au code client |
| Requête contenant attributs événementiels supplémentaires | Rejet actuel possible avec `additionalProperties: false` | À reproduire localement |
| Parsing des réponses (3 checks vs 8) | Tolérance ou incompatibilité réelle | À démontrer |
| Statuts HTTP et `UNKNOWN` | Absence de retry financier automatique | À confirmer dans le client actif |
| Legacy `/event` et enveloppe `code/eve` | Historique sans alias Regional | Non porté automatiquement |

## Conclusion

**Compatibilité nominale des routes, mais compatibilité wire de bout en bout NON DÉMONTRÉE.** Le delta le plus important concerne `providerEvent` fermé dans Regional versus objet libre dans la référence SIXPAY, et `providerEntries` requis dans la référence tandis qu'absent du Regional T0. L'élargissement contractuel est donc une demande de revue, non une décision d'implémentation ou une approbation.
