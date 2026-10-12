# R6.6.2 — Checklist de validation du contrat événementiel

> **PENDING_APPROVAL — aucun changement de contrat autorisé par cette checklist.**

| ID | Décision à prendre | Preuve demandée | Responsable attendu | Statut |
|---|---|---|---|---|
| C-01 | Identifier cas métier T0 réellement supportés | Scénarios bancaires applicables La Régionale | Banque / architecture Regional | OPEN |
| C-02 | Confirmer noyau actuel des 9 champs | Mapping R6.6.1 + scénarios | Banque / contrat Regional | OPEN |
| C-03 | Sélectionner seulement les attributs complémentaires qui doivent venir du client | Pour chaque champ candidat : raison, type, cardinalité, nullabilité, contrainte | Banque / consommateurs / Regional | OPEN |
| C-04 | Valider les montants par jambe `MHT1/MON1/MHT2/MON2` | Signification et contrôle financier | Banque | OPEN |
| C-05 | Clarifier `AGSA`, `AGE`, `CSP1` | Arbitrage explicite, y compris `VBUSS` / `VMoMo` | Banque / consommateurs | OPEN |
| C-06 | Confirmer champs autoritatifs CBS non acceptés du client | Priorité sur `EVE`, `DCO`, `DVA1`, `DVA2`, `DOU`, etc. | Banque / Regional | OPEN |
| C-07 | Maintenir BKMVTI hors T0 | Validation séparation T0/T1 et cas supportés | Banque / architecture | OPEN |
| C-08 | Vérifier compatibilité wire SIXPAY réellement exécutée | Clients/mappers/tests SIXPAY actuels en lecture seule ; formes JSON et parsing des réponses | Consommateur / Regional | OPEN |
| C-09 | Déterminer caractère breaking ou non du delta proposé | Tests sur corps de requête, `required`, réponses, headers, codes | Contract review | OPEN |
| C-10 | Approuver explicitement une version révisée du contrat | Décision humaine documentée sur la proposition exact-match | Propriétaire contrat Regional | OPEN |

Une approbation générale du lot R6.6.2 **n'est pas** une approbation implicite des ajouts futurs. Après validation des C-01 à C-09, présenter une révision OpenAPI concrète et exiger son approbation spécifique avant génération ou implémentation.
