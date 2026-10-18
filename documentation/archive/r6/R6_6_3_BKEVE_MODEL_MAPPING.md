# R6.6.3 — Modèle positionnel BKEVE et mapping JDBC

**Statut : IMPLEMENTATION_PREPARATORY / BANK_EVIDENCE_PENDING.** Ce lot ne déclenche aucun INSERT BKEVE.

## Traçabilité

- Repository : `SIXGEN-Solutions/regional-core-banking-api`.
- Base vérifiée : `feat/payment-orchestration-worflow @ bd5bf60049903edac5f7685a8f121d7cc3a4927a`.
- Source des **233 positions** : `documentation/architecture/R6_6_1_BKEVE_MAPPING_233.csv`, extraite de `payment(1).java` legacy. Non assimilée à une validation bancaire de toutes les colonnes.
- Modèle Regional indépendant des DTO SIXPAY et des modèles OpenAPI de transport.

## Fonctionnement

- `BkeveJdbcColumnOrder` fige les 233 colonnes dans l'ordre des sources legacy.
- `BkeveJdbcParameterMapper` crée une liste de 233 paramètres et les lie à un `PreparedStatement` déjà fourni par un adaptateur futur.
- Les propriétés approuvées côté client renseignent AGSA, AGE, OPE, MON1/MHT1, MON2/MHT2, LIB1 et CSP1.
- Tous les autres champs, y compris EVE, dates, enrichissements bancaires et valeurs historiques non approuvées, **doivent être fournis explicitement** par un composant bancaire autoritatif à construire après validation de la preuve.
- Si une colonne manque, le mapping échoue sans affecter de valeur implicite. Une valeur `null` n'est admise que lorsqu'elle est explicitement présente ; son autorisation effective devra être validée contre les contraintes bancaires.
- Le `bind` ne prépare ni n'exécute aucune requête SQL et n'établit pas de transaction.

## Hors périmètre / blocages

La Régionale doit confirmer la structure physique et les règles de nullabilité, tailles, échelles, défauts, devises, enrichissement complet des deux comptes, procédure/transaction de postage et recovery. Le champ EVE doit provenir d'une attribution bancaire atomique et approuvée. Aucun comportement BKMVTI/T1 n'est ajouté. Les références SIXPAY restent READ_ONLY.

## Validation locale à lancer par l'utilisateur

```bash
./mvnw -Dtest=BkeveJdbcParameterMapperTest test
./mvnw clean verify
 git diff --check
 git status --short
```

**Aucun gate, commit, push ou PR n'a été lancé par le script.**
