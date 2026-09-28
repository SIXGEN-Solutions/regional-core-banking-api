# R5 — Activation EMAIL OTP sur la messagerie La Régionale

## Statut

`READY_FOR_BANK_EMAIL_CONFIGURATION`

Ce runbook décrit le branchement du mécanisme EMAIL OTP déjà implémenté vers
l'infrastructure de messagerie réelle de La Régionale.

Il ne modifie ni le contrat OpenAPI, ni les endpoints, ni les schémas.

## Flux production déjà implémenté

```text
Payment Confirmation
  -> BankingConfirmationRecipientAdapter
  -> CustomerBankingPort.getCustomer(financialInstitutionCode, customerReference)
  -> JdbcCustomerBankingAdapter
  -> première ligne bkemacli.email pour cli = customerReference
  -> CustomerIdentity.email()
  -> EmailConfirmationDeliveryAdapter
  -> JavaMailSender
  -> SMTP / relay La Régionale
  -> client
```

La qualité et la mise à jour des coordonnées client/KYC appartiennent au Core Banking.
Payment Confirmation ne sélectionne pas une adresse selon une règle KYC supplémentaire :
la première adresse renvoyée par la capacité Customer est utilisée.

Le profil `local` reste réservé au test développeur : destinataire fixe + Mailtrap.
Hors `local`, le destinataire provient du Core Banking.

## Informations à recevoir de La Régionale

Avant activation, La Régionale doit fournir les valeurs et contraintes applicables :

| Paramètre / information | Rôle |
| --- | --- |
| `REGIONAL_OTP_EMAIL_HOST` | Nom DNS ou adresse du serveur SMTP/relay autorisé. |
| `REGIONAL_OTP_EMAIL_PORT` | Port d'écoute SMTP fourni par La Régionale. |
| `REGIONAL_OTP_EMAIL_USERNAME` | Identifiant du compte technique SMTP, si authentification requise. |
| `REGIONAL_OTP_EMAIL_PASSWORD` | Secret du compte technique SMTP. Doit être injecté comme secret de déploiement. |
| `REGIONAL_OTP_EMAIL_TLS` | Active STARTTLS dans la configuration actuelle. La valeur exacte doit être fournie/validée par La Régionale. |
| `REGIONAL_OTP_EMAIL_SENDER` | Adresse expéditeur autorisée pour les OTP. |
| `REGIONAL_OTP_EMAIL_ENABLED` | Met à `true` l'activation du canal EMAIL. |
| `REGIONAL_OTP_EMAIL_CONNECTION_TIMEOUT_MS` | Timeout d'établissement de connexion SMTP ; défaut actuel 5000 ms. |
| `REGIONAL_OTP_EMAIL_READ_TIMEOUT_MS` | Timeout de lecture SMTP ; défaut actuel 5000 ms. |
| `REGIONAL_OTP_EMAIL_WRITE_TIMEOUT_MS` | Timeout d'écriture SMTP ; défaut actuel 5000 ms. |

La Régionale doit également confirmer les contraintes d'infrastructure qui ne sont pas
des valeurs applicatives : résolution DNS, routage/VPN, firewall, IP allowlist, chaîne de
certificats/trust, mode d'authentification SMTP et politique du relay.

Ne pas déduire ces valeurs à partir de Mailtrap.

## Fichiers de l'application

### `src/main/resources/application.yml`

Les paramètres SMTP génériques nécessaires sont déjà externalisés. Aucune valeur de
production ne doit être écrite en dur dans ce fichier.

Le fichier ne doit être modifié que si les exigences réelles de La Régionale nécessitent
une propriété non représentée aujourd'hui, par exemple un mode TLS différent, une propriété
JavaMail spécifique ou une configuration de trust explicitement approuvée.

### `src/main/resources/application-local.yml`

Aucune modification pour la production.

Ce fichier conserve Mailtrap et `regional.confirmation.email.test-recipient` pour les tests
locaux uniquement.

### `config/application-local-secrets.example.yml`

Aucune modification pour la production. Il documente uniquement les secrets locaux.

### `.gitignore`

Aucune modification requise. Les fichiers de secrets locaux et principaux formats de
certificats/keystores sont déjà exclus. Les secrets de production doivent être injectés par
le mécanisme de déploiement et ne doivent pas être ajoutés au repository.

### Code Java

Aucune modification Java n'est requise pour un SMTP/relay compatible avec la configuration
actuelle.

`BankingConfirmationRecipientAdapter` résout déjà le destinataire bancaire réel.
`EmailConfirmationDeliveryAdapter` réalise déjà l'envoi via `JavaMailSender`.
`ConfirmationConfiguration` branche ces composants hors du profil `local`.

Une modification Java/configuration supplémentaire n'est justifiée que si les paramètres
réels fournis par La Régionale révèlent une exigence non couverte. Cette exigence doit être
validée avant implémentation ; elle ne doit pas être inventée.

## Secrets et configuration de production

Les valeurs suivantes doivent être fournies au processus Java par le mécanisme de
configuration/secrets de l'environnement, et non committées :

```text
REGIONAL_OTP_EMAIL_ENABLED=true
REGIONAL_OTP_EMAIL_HOST=<fourni-par-la-regionale>
REGIONAL_OTP_EMAIL_PORT=<fourni-par-la-regionale>
REGIONAL_OTP_EMAIL_USERNAME=<fourni-par-la-regionale-si-requis>
REGIONAL_OTP_EMAIL_PASSWORD=<secret>
REGIONAL_OTP_EMAIL_TLS=<valeur-validee>
REGIONAL_OTP_EMAIL_SENDER=<expediteur-autorise>
```

Les paramètres HMAC OTP et les paramètres de la base technique PostgreSQL restent des
configurations de production distinctes et doivent également être injectés par le mécanisme
de secrets/configuration prévu.

## Procédure d'activation

1. Recevoir les paramètres SMTP/relay et les contraintes réseau/sécurité de La Régionale.
2. Vérifier si le mode SMTP/TLS/authentification est couvert par `application.yml`.
3. Si oui, ne modifier aucun code : injecter les valeurs dans l'environnement non-production bancaire.
4. Si non, documenter l'écart et faire approuver la propriété/configuration supplémentaire avant implémentation.
5. Ouvrir les flux réseau nécessaires entre l'API et le SMTP/relay selon les règles La Régionale.
6. Démarrer l'application hors profil `local`, avec `REGIONAL_OTP_EMAIL_ENABLED=true`.
7. Utiliser un client bancaire de test dont `bkemacli` contient une adresse contrôlée.
8. Créer un challenge OTP et vérifier que l'email arrive dans la boîte du client de test.
9. Soumettre l'OTP reçu et vérifier le résultat `VERIFIED` / `OTP_VERIFIED`.
10. Vérifier les cas absence d'email, rejet SMTP et timeout sans exposer OTP ou credentials dans les logs.
11. Exécuter les gates applicatifs requis sans génération OpenAPI.
12. Après validation bancaire et opérationnelle, injecter les paramètres/secrets de production et activer EMAIL.

## Critère de disponibilité production EMAIL

La capacité EMAIL OTP est applicativement raccordée. Son activation sur l'infrastructure
réelle de La Régionale est conditionnée à la fourniture et à la validation de ses paramètres
SMTP/relay, secrets, certificats/trust éventuels et règles réseau.

Ce statut ne clôt pas la capacité SMS/BKSMS, qui reste un chantier séparé dépendant de ses
propres preuves bancaires.
