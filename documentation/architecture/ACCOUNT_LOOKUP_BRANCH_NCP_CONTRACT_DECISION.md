# REGIONAL — Consultation de compte par code agence et NCP

**Decision contractuelle approuvee par instruction explicite de l'utilisateur (4 novembre 2026).**
Cette approbation porte exclusivement sur la nouvelle consultation par agence et NCP,
pas sur les autres endpoints ou les parametres de securite/infrastructure.

Repository: `SIXGEN-Solutions/regional-core-banking-api`.
Revision observee et exigee: `feat/payment-orchestration-worflow @ 42f4a98f588ad0fe7810964ef5434192e918aaa0`.
Source: extrait Java legacy transmis par l'utilisateur (`req_get_infos_account`, `getSolde`).
Chemin/SHA original du legacy: non fournis; la preuve est limitee a cet extrait.
Le prefixe `documentation/architecture/R6_6_1_*` est absent du commit de base.

## Endpoint et semantique

`GET /api/v1/getaccountinfo/{branchid}/{accountnumber}`

Headers requis suivant la convention Regional existante: `X-Correlation-ID`
(UUID) et `X-Financial-Institution-Code` (chaine).

**HTTP 200 dans les deux cas de consultation aboutie :**

Compte trouve (objet unique, jamais une liste):

```json
{"code":"SUCCESS","message":"Compte trouvé","data":{"age":"00001","ncp":"04942231051","clc":"47","cli":"0494223","dev":"001","ife":"N","cfe":"N","sin":50000,"typ":"105"}}
```

Compte absent:

```json
{"code":"COMPTE_INEXISTANT","message":"COMPTE INEXISTANT","data":null}
```

`data` est le modele de transport Regional `BankAccount` **existant**. Uniquement
le schema d'enveloppe `AccountLookupResult` est ajoute. Ne pas assimiler
une indisponibilite JDBC, un solde ambigu ou une erreur d'acces a COMPTE_INEXISTANT.
Les erreurs techniques continuent d'utiliser `application/problem+json` et les
statuts HTTP correspondants (400/401/403/500/503).

## Lecture bancaire et securite

Les criteres utilises proviennent exactement de l'extrait legacy fourni:
`trim(bkcom.age)=?`, `trim(bkcom.ncp)=?`, `trim(bkcom.ife)='N'`,
`trim(bkcom.cfe)='N'`. Aucune restriction `dev='001'` n'est ajoutee a la
selection, car l'extrait ne la contient pas.

La lecture reemploie le `BankAccount` domain, la transformation BKCOM du
`JdbcCustomerBankingAdapter` et son `availableBalance` (equivalent existant de
`getSolde`, avec devise 001 dans son implementation historique).
Un compte trouve avec balance indisponible n'est pas rapporte comme inexistant.
Plusieurs comptes admissibles pour le meme couple age/ncp produisent une anomalie
technique, jamais une selection arbitraire.

Les controles d'acces Regionale existants restent applicables ; ce lot ne cree
aucun nouveau scope ni relation de confiance. Le consumer SIXPAY reste inchangé.

## Suivi

- Contrat: approbation humaine du comportement fonctionnel acquise dans la discussion.
- Compatibilite consumer: a confirmer si SIXPAY ou un autre consommateur adopte
  le chemin historique ou le nouvel emballage JSON; ne pas supposer une
  compatibilite automatique avec la reponse JSON du legacy.
- Unicite de `(age,ncp)` dans BKCOM: confirmation bancaire encore requise.
- Aucune modification distante ni execution de gates par le script.
## Modification de route (4 novembre 2026)

Sur demande explicite, le chemin REST Regional est `/api/v1/getaccountinfo/{branchid}/{accountnumber}`.
L'ancien chemin `/getaccountinfo/{branchid}/{accountnumber}` n'est pas maintenu en alias.
Le contrat OpenAPI canonique, sa copie Swagger UI et le mapping Spring sont synchronises. Les entetes, les modeles, les statuts HTTP et la semantique de consultation sont inchanges.
