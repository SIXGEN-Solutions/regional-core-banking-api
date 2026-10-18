# R6.6.5 — Numérotation EVE multi-instance : frontière transactionnelle

Statut : **ARCHITECTURE PREPARATOIRE / BLOCKED_BANK_EVIDENCE**. Aucun allocateur Informix production, INSERT BKEVE ou déploiement autorisé par ce changement.

## Revision
`SIXGEN-Solutions/regional-core-banking-api`, `feat/payment-orchestration-worflow`, SHA de référence `c96fab3abefdd8e999ddc3d841cf7e323519df68`.

## Risque confirmé
Le resolver R6.5 utilise `MAX(eve)` + vérification d'existence. Les deux lectures distinctes n'offrent aucune atomicité entre JVM ou instances. Le port de R6.6.5 introduit précédemment n'est pas câblé ; ses tests avec `AtomicInteger` ne prouvent pas l'atomicité bancaire.

## Solution cible proposée — à approuver par La Régionale
La sérialisation doit être assurée par une ressource **commune à toutes les instances**, de préférence la base bancaire autoritative. La section critique doit couvrir : verrou/attribution EVE, contrôle autoritatif, écriture BKEVE et issue transactionnelle. La clé de sérialisation/unicité, les tables ou procédures autorisées, le mode jour/nuit, les règles de débordement et le comportement de récupération doivent être confirmés. Un verrou JVM, un cache partagé, `MAX(eve)+1` sans verrou, ou une séquence PostgreSQL non réconciliée avec Informix ne suffisent pas.

La frontière Java `BkeveTransactionScopedAllocationPort` matérialise ce contrat attendu **sans sélectionner d'instructions SQL inconnues**. Le service valide les entrées/EVE et n'effectue aucun retry. Le port par défaut refuse toute opération. Le test simule deux objets applicatifs avec un verrou commun uniquement pour caractériser le protocole ; **il ne remplace PAS le test de concurrence Informix entre deux connexions/instances**.

## Éléments à obtenir avant implémentation production
1. Détail banque : BKOPE/SYN_BKOPE, numérotation autoritative, index uniques et clé métier, comportement de rollover.
2. SQL/procédure et mode d'isolation/verrouillage approuvés, et preuve transactionnelle allocation + INSERT BKEVE.
3. Comportement jour/nuit et changement de table BKEVE/BKEVE_EOD pendant la transaction.
4. Persistance idempotente de la corrélation paiement/EVE/DCO et récupération de transaction à issue inconnue.
5. Tests Informix multi-connexions, rollback, crash, ambiguïté de commit, saturation numéro, redémarrage et concurrence inter-instances.

## Non-changements volontaires
L'adaptateur R6.5 existant reste en lecture seule / fail-closed. Son ancien calcul EVE n'est **pas** une allocation réservée. Cette préparation n'active pas le postage et ne modifie aucun contrat OpenAPI, schéma, SQL ou repository SIXPAY. Avant activation, il faut supprimer la voie MAX/existence du chemin de postage.
