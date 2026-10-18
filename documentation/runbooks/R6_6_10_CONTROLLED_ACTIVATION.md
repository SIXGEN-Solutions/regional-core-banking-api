# R6.6.10 — Procédure d'activation contrôlée du postage financier

## Objectif et garde-fous

Procédure opératoire **proposée**, non ordre d'activation. Ne l'utiliser pour des écritures réelles qu'après le GO signé décrit dans `documentation/architecture/R6_6_10_ACTIVATION_DECISION.md`.

**Interdictions permanentes** : aucun rejeu automatique d'un paiement `UNKNOWN`, aucune déduction de succès à partir du seul PostgreSQL, aucun changement implicite de contrat OpenAPI, aucun secret/certificat dans Git, aucune action sur SIXPAY et aucun paiement réel dans une base non expressément autorisée.

## A. Avant activation (hors écriture financière)

1. Identifier le propriétaire de l'activation, le DBA Amplitude, l'exploitation et le responsable de rapprochement ; fixer canal d'escalade et fenêtre autorisée. Recueillir la validation explicite de La Régionale.
2. Geler le SHA à déployer et archiver la configuration externe effective **sans secrets** : vendeur Informix/Oracle, URL de cible expurgée, utilisateur technique, nombre d'instances et affectation exclusive des OPE.
3. Vérifier les migrations PostgreSQL de la base technique, l'existence et droits sur `API_EODProcess`, `regional_payment_execution`, les contraintes d'idempotence, et les procédures de sauvegarde/restauration.
4. Vérifier côté Amplitude, avec l'exploitant, les droits minimaux, schéma physique et nullabilité de BKEVE/BKEVE_EOD, procédures de fermeture/TFJ et cohérence du chemin des soldes indicatifs.
5. Confirmer le niveau d'isolation réellement appliqué à la transaction JDBC d'écriture ; **ne pas propager `SET ISOLATION TO DIRTY READ` depuis les lectures vers les écritures sans décision bancaire explicite**.
6. Archiver les gates et jeux de tests exécutés sur ce SHA ; simuler succès, refus, rollback avant commit, coupure au commit, concurrence même OPE / OPE différents, redémarrage et recovery. Les simulations ne remplacent pas G4/G5 sur cible bancaire autorisée.
7. Préparer les sondes/mesures : nombre de claims, réservations EVE, POST réussis, échecs techniques, `UNKNOWN`, écarts de rapprochement ; masquer toutes les données financières sensibles.

## B. Commandes à exécuter localement (non exécutées par l'auteur)

Windows PowerShell, à la racine du repository :

```powershell
git rev-parse HEAD
git branch --show-current
.\mvnw.cmd -Dtest=BkeveJdbcParameterMapperTest,BkeveTransactionWriteAssemblyTest,JdbcBkeveTransactionCoordinatorTest,BkevePostingAndBalanceWriteTest test
.\mvnw.cmd clean verify
git diff --check
```

Exécuter les tests PostgreSQL **seulement avec une base isolée jetable** et les paramètres de test appropriés ; ne jamais pointer vers une base technique partagée ou de production. Aucun test Informix/Oracle réel ne doit démarrer avant feu vert explicite pour l'environnement bancaire cible.

## C. Séquence d'activation après GO humain

1. Confirmer une dernière fois le SHA, les dépendances/versions et les sources de configuration externes. Ne pas modifier le contrat ni les paramètres bancaires en cours de fenêtre sans revalidation.
2. Confirmer une unique instance Regional pour les OPE convenus ; prouver que ces OPE ne sont pas utilisés par d'autres producteurs Amplitude. Vérifier la cohérence `API_EODProcess.lasteve` et du dernier EVE bancaire avant toute transaction.
3. Sur environnement bancaire de test autorisé, effectuer **uniquement** un jeu de paiements bancaires explicitement approuvé, à montants/comptes de test définis par la banque ; observer la ligne `BKEVE` ou `BKEVE_EOD`, les soldes et la correspondance `(OPE,EVE)` avec PostgreSQL.
4. Confirmer pour chaque essai l'état et la référence bancaire depuis Amplitude ; comparer avec le résultat renvoyé à l'appelant. Ne pas assimiler `regional_payment_execution.outcome` à une preuve bancaire.
5. Exécuter des tests d'erreur contrôlés approuvés : opposition / insuffisance, rollback avant commit, perte de réponse au commit, récupération par référence et clé ; prouver l'absence de double postage. Ne jamais déclencher artificiellement une panne sur un environnement de production.
6. Consolider les résultats, journaliser anomalies et signatures ; autoriser la fenêtre de production seulement si G1–G8 sont tous satisfaits et si l'autorité bancaire approuve l'étape.
7. Production : activation limitée au périmètre validé, surveillance renforcée, rapprochement immédiat et décision explicite de poursuite/arrêt. Aucun nouveau mécanisme de bascule runtime n'est ajouté par ce runbook.

## D. Traitement des incidents

| Situation | Conduite obligatoire |
|---|---|
| Refus conclusif avant écriture bancaire | Conserver le résultat rejeté et les preuves de contrôle ; pas d'INSERT |
| Échec avant le commit | Exiger rollback bancaire ; garder la réservation EVE technique ; ne pas réattribuer EVE |
| Timeout, coupure de connexion ou échec autour du commit | Marquer ou conserver `UNKNOWN` ; arrêter tout rejeu ; retrouver `OPE/EVE` par PostgreSQL, puis vérifier Amplitude |
| Événement trouvé une seule fois dans Amplitude | Soumettre la preuve bancaire au processus de rapprochement ; rétablir le résultat autoritatif selon la logique implémentée |
| Événement absent ou vérification impossible | Maintenir l'incertitude, escalader ; l'absence immédiate ne constitue pas une autorisation de rejeu |
| Plusieurs événements, soldes divergents ou collision | Stopper le postage du périmètre, isoler l'incident, conserver les journaux, escalader au DBA et à La Régionale |

## E. Arrêt et retour arrière

1. Suspendre **l'envoi de nouvelles exécutions financières** à l'aide des mécanismes d'exploitation approuvés. Si aucun contrôle fin n'existe, arrêter le service suivant la procédure d'exploitation ; ne pas inventer de flag d'activation.
2. Préserver PostgreSQL et les traces d'allocation ; ne jamais supprimer ni remettre `lasteve` à zéro pour « libérer » des EVE.
3. Établir le bilan des opérations `COMPLETED`, `REJECTED`, `UNKNOWN` par rapprochement avec Amplitude ; traiter individuellement tout `UNKNOWN`.
4. Aucun rollback Git, déploiement logiciel ou restauration PostgreSQL **n'annule une écriture bancaire déjà commitée**. Toute correction financière suit exclusivement les procédures de La Régionale validées séparément.
5. Reprendre uniquement après résolution documentée, validation des autorités et nouveau GO.

## F. Procès-verbal de validation (à compléter par les responsables)

- Révision déployée / image immuable : **à compléter**
- Environnement bancaire et OPE autorisés : **à compléter**
- Gates Maven et tests PostgreSQL : **à compléter**
- Tests Amplitude jour/nuit, commit/rollback/recovery : **à compléter**
- Isolation JDBC effective / validation DBA : **à compléter**
- Disponibilité supervision, rapprochement et réponse aux incidents : **à compléter**
- Visa La Régionale / exploitation / sécurité / décisionnaire release : **à compléter**
- Décision finale signée (GO ou NO-GO), date et périmètre : **à compléter**

**Sans procès-verbal GO signé : aucun postage réel.**
