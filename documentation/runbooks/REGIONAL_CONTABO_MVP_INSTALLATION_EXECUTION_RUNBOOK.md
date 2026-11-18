# Regional Core Banking API — Runbook d'installation et journal d'exécution Contabo MVP

**État :** `MVP_RUNNING / SMTP_SANDBOX_PENDING / SIXPAY_PRIVATE_CONNECTIVITY_PENDING`  
**Date des observations :** 15 novembre 2026 (logs Oracle/Java en UTC, session Contabo en heure locale du serveur).  
**Classification :** `REGIONAL_OWNED / OPERATIONAL_EXECUTION_RECORD / MOCK_ONLY` ; ne constitue ni une autorisation bancaire de production ni une preuve d'intégration SIXPAY.  
**Cible GitHub :** `SIXGEN-Solutions/regional-core-banking-api`, branche `feat/payment-orchestration-worflow`.  
**Révision de référence retenue lors du déploiement :** `6b5d66b54f9eb46b942e1a43a04614b721c29637`. **HEAD observé lors de la rédaction :** `b187f0ca2e805712012755c966313ae50e0d7b50`. **Image effectivement déployée :** commit OCI `52f31ec9441bd86facd65cb8e5de94638ec695c8` — écart explicitement accepté par l'opérateur pour ce MVP.  
**Mode de collecte :** fichiers source GitHub lus en lecture seule ; commandes VPS et résultats **rapportés par l'opérateur** au fil de la conversation. Aucune exécution directe sur le VPS par le rédacteur. Aucune mutation GitHub.  
**Politique de secrets :** ne jamais copier dans ce document PAT, HMAC, OTP, mots de passe, identifiants SMTP en clair, données clients réelles. Les valeurs SMTP précédemment publiées en conversation sont considérées compromises : les renouveler avant utilisation.

## 1. Périmètre, topologie et garanties

MVP bancaire **synthétique** autonome, distinct de SIXPAY. Un seul service Java/Spring Boot, PostgreSQL technique et Oracle Free MOCK. Pas d'Informix/Amplitude réel. Pas d'exposition HTTP publique de Regional. Les deux bases n'ont pas de port hôte publié. Le périmètre actuel n'inclut ni authentification inter-applicative finalisée ni mTLS ; un chantier distinct devra les définir/valider. Ne pas relier automatiquement les réseaux Docker SIXPAY et Regional.

```text
Contabo AlmaLinux 10.2 (SELinux Enforcing, firewalld, Docker Compose, Caddy présent)
  ├── SIXPAY préexistant : backend, frontend, LDAP, Keycloak, PostgreSQL (réseaux propres)
  └── Compose project: regional-core-banking-mvp
        ├── postgres:16-alpine          -- regional_private, /var/lib/regional/postgres/data
        ├── gvenzl/oracle-free:23.26.1-slim -- regional_private, /var/lib/regional/oracle/data
        └── regional-core-banking-api   -- regional_private + regional_access
              container 9092 -> host 127.0.0.1:9093 ONLY
              Java 21 / Spring Boot 3.5.6, PostgreSQL technical schema core_banking
              Oracle jdbc:oracle:thin:@//customer-oracle:1521/FREEPDB1
```

**VPS inventorié au début du chantier :** hôte `vmi3671192.contaboserver.net`; AlmaLinux 10.2, kernel 6.12 ; 8 vCPU AMD EPYC ; ~23 GiB RAM (~20 GiB disponibles lors de l'inventaire) ; ~287 GiB de disque libres ; pas de swap ; Docker Compose v5.7.0. Contabo héberge déjà SIXPAY. Valeurs **observées à cette date**, pas des garanties permanentes de capacité. Refaire l'inventaire avant augmentation de charge. La cible provisoire documentée dans le repository était ≥12 GiB libres et ~80–120 GiB libres sur SSD pour le MVP partagé ; il ne s'agit pas d'un dimensionnement bancaire approuvé, ni d'un test de charge. L'opérateur a accepté temporairement l'absence de swap pour ce MVP.

### 1.1 Inventaire reproductible (lectures seules)

| Commande | But | État/résultat connu |
|---|---|---|
| `hostnamectl; uptime; getenforce; free -h; df -hT; lsblk -f` | OS, SELinux, mémoire, volumes | AlmaLinux 10.2 ; SELinux Enforcing ; ~23 GiB RAM ; espace ~287 GiB ; absence de swap constatée |
| `sudo systemctl is-active docker caddy firewalld` | Services hôte | Docker, Caddy, firewalld actifs lors de l'inventaire |
| `sudo firewall-cmd --list-all; sudo ss -lntup` | Zones/ports | Inspection seulement, aucune ouverture Regional |
| `sudo docker compose version; sudo docker network ls` | Version, réseaux existants | Compose v5.7.0, réseaux SIXPAY conservés |
| `sudo docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'` | Baseline SIXPAY | Cinq conteneurs SIXPAY actifs avant lancement Regional |
| `id sixpayadmin; sudo df -i /var/lib /opt` | Droits hôte, inodes | Inspection initiale à revalider avant changement sensible |

**Baseline SIXPAY observée avant démarrage Regional :** `sixpay-connect-ldap-backend-1` (loopback `127.0.0.1:8081`), `sixpay-connect-ldap-frontend-1` (loopback `127.0.0.1:8088`), `sixpay-m2m-keycloak-cloud-keycloak-1` (loopback `127.0.0.1:8180`), `sixpay-cloud-ldap`, `sixpay-connect-cloud-postgres-1` (`healthy`, interne). Ne pas interpréter leur seule présence comme preuve d'absence de tout effet indirect ; aucune intervention SIXPAY n'a été demandée ici.

## 2. Arborescence, identités Linux, SELinux

### 2.1 Répertoires de staging et configuration

Commandes de préparation utilisées/proposées lors de cette phase (l'arborescence résultante a ensuite été confirmée sur le VPS) :

```bash
sudo install -d -o root -g root -m 0750 \
  /opt/regional /opt/regional/compose /opt/regional/config \
  /opt/regional/bootstrap /opt/regional/bootstrap/oracle /opt/regional/bootstrap/postgres
sudo install -d -o root -g root -m 0700 /opt/regional/secrets
sudo install -d -o root -g root -m 0750 \
  /var/lib/regional /var/lib/regional/postgres /var/lib/regional/oracle
sudo install -d -o 70 -g 70 -m 0700 /var/lib/regional/postgres/data
sudo install -d -o 54321 -g 54321 -m 0750 /var/lib/regional/oracle/data
sudo ls -ldZ /opt/regional /opt/regional/secrets \
  /var/lib/regional/postgres/data /var/lib/regional/oracle/data
```

**Résultat :** `/opt/regional/{compose,config,bootstrap/{oracle,postgres},secrets}` root-owned ; secrets répertoire `0700`, configurations `.env`, `application-production.yml`, `compose.yml` root-owned `0640` lors des vérifications ; scripts SQL `root:root 0644`. PostgreSQL données `70:70 0700` ; Oracle `54321:54321 0750`. Ne pas utiliser UID PostgreSQL 999 : l'image `postgres:16-alpine` étudiée utilise UID 70. Vérification image par `docker run --rm ... id postgres` ou image inspection préalable requise lors d'un changement d'image.

### 2.2 Relabeling et validation SELinux

```bash
sudo ls -laZ /opt/regional/secrets/
sudo ls -laZ /opt/regional/bootstrap/oracle/
sudo ls -laZ /opt/regional/bootstrap/postgres/
sudo ls -lZ /opt/regional/config/application-production.yml
sudo matchpathcon -V \
  /opt/regional/secrets/ORACLE_APP_PASSWORD \
  /opt/regional/secrets/REGIONAL_TECHNICAL_DB_PASSWORD \
  /opt/regional/secrets/REGIONAL_OTP_HMAC_KEYS \
  /opt/regional/config/application-production.yml \
  /opt/regional/bootstrap/oracle/001_customer_schema.sql
sudo ausearch -m AVC,USER_AVC -ts recent
```

**Observé :** labels staging `usr_t` jugés conformes par `matchpathcon -V` (`verified`) ; `ausearch` sans AVC récent au moment des tests. Montages de données et scripts avec `:Z`/`:ro,Z` ; après démarrage PostgreSQL, label `container_file_t:s0:c179,c574` sur les données ; après tentative Oracle, `container_file_t:s0:c186,c423`. Aucun `setenforce 0`, `chmod 755` sur les secrets ou relabeling récursif des chemins SIXPAY. `matchpathcon` ne prouve pas une lecture dans le conteneur : tests explicites effectués §4.

## 3. Fichiers et arborescence de déploiement

```text
/opt/regional/
  compose/compose.yml
  config/.env
  config/application-production.yml
  bootstrap/postgres/001_create_core_banking_schema.sql
  bootstrap/oracle/001_customer_schema.sql
  bootstrap/oracle/002_payment_schema.sql
  bootstrap/oracle/003_customer_multi_account_seed.sql
  secrets/REGIONAL_TECHNICAL_DB_PASSWORD
  secrets/ORACLE_APP_PASSWORD
  secrets/REGIONAL_OTP_HMAC_KEYS
  secrets/REGIONAL_OTP_EMAIL_USERNAME
  secrets/REGIONAL_OTP_EMAIL_PASSWORD
  secrets/REGIONAL_OTP_SMS_USERNAME
  secrets/REGIONAL_OTP_SMS_PASSWORD
/var/lib/regional/
  postgres/data/
  oracle/data/
  oracle/data-failed-faststart-20261115/   # archive échec, ne pas supprimer
```

Source dans le dépôt : `deploy/contabo/{compose.yml,application-production.yml,.env.example}`, `docker/postgres/001_create_core_banking_schema.sql`, `docker/customer-oracle/{001_customer_schema.sql,002_payment_schema.sql,003_customer_multi_account_seed.sql}`. **Écart local accepté :** sur Contabo, `compose.yml` monte *trois* scripts Oracle ; le Compose GitHub inspecté n'en monte que *deux*. De même, `.env` VPS retient l'image non-Faststart et la config Spring VPS ajoute `management.health.mail.enabled=false`, contrairement au modèle GitHub inspecté. Ne jamais écraser aveuglément les fichiers VPS par les templates GitHub.

### 3.1 Commandes de staging et validations

```bash
sudo install -o root -g root -m 0644 \
  /home/sixpayadmin/001_create_core_banking_schema.sql \
  /opt/regional/bootstrap/postgres/001_create_core_banking_schema.sql
sudo cat /opt/regional/bootstrap/postgres/001_create_core_banking_schema.sql
sudo ls -lZ /opt/regional/bootstrap/postgres/
sudo nano /opt/regional/compose/compose.yml
sudo nano /opt/regional/config/.env
sudo nano /opt/regional/config/application-production.yml
sudo docker compose --env-file /opt/regional/config/.env \
  -f /opt/regional/compose/compose.yml config --quiet
echo $?
sudo bash validate_stage.sh  # script local /home/sixpayadmin/validate_stage.sh
```

**Attendu / observé :** Docker Compose exit `0`; présence de tous les fichiers + secrets + volumes affiche `STAGING FILES PRESENT`. Attention : `config --quiet` valide le Compose, *pas* les propriétés Spring, les permissions effectives, le contenu SQL ou la disponibilité des services. Ne pas lancer `docker compose config` sans `--quiet` lors d'une collecte partagée : le rendu peut inclure variables sensibles.

**SQL PostgreSQL exact vérifié :**

```sql
-- Local Docker technical persistence bootstrap only.
-- This is not an Amplitude/Informix banking schema.

CREATE SCHEMA IF NOT EXISTS core_banking
    AUTHORIZATION regional_core_banking_app;
```

### 3.2 Incident de noms de fichiers Oracle (résolu)

Lors du transfert, les scripts avaient initialement été enregistrés sous les noms incorrects : `001_oracle_schema.sql` contenait le **script payment 002**, `002_customers_schema.sql` contenait le **script customer 001**, et `003_payment_schema.sql` contenait le **seed 003**. Des `cp -n` ont été utilisés pour obtenir les noms canoniques, `cmp` a vérifié les copies locales, puis les *trois fichiers incorrectement nommés* ont été supprimés. **Résultat final :** exactement `001_customer_schema.sql`, `002_payment_schema.sql`, `003_customer_multi_account_seed.sql`. `cmp` prouve l'identité entre deux copies locales, pas leur conformité à GitHub.

Vérifications :

```bash
sudo ls -laZ /opt/regional/bootstrap/oracle/
sudo sha256sum /opt/regional/bootstrap/oracle/*.sql
```

**SHA-256 GitHub vs VPS :**

| Fichier | Empreinte VPS | Décision |
|---|---|---|
| `001_customer_schema.sql` | `1c94e9a8ff2a9ed73cf14845e3568c3c342ff3eb5788f4789636b42244e60e0f` | Identique à GitHub `6b5d66b` |
| `002_payment_schema.sql` | `ac10f44c6b2c76e738fd632b48bba264ba43964a87439a00070bd50c4f933ad6` | Identique à GitHub `6b5d66b` |
| `003_customer_multi_account_seed.sql` | `50aaef068d73c64ce2219c940a8d7e7c42d72362e91e19dedaa661b66c91a173` | **Variante Contabo conservée par décision explicite** ; GitHub `6b5d66b`: `3b32137aa225bbfa4999cdea2677370e194765223b9979016e7ff8da9724a729` |

Commandes de diagnostic du troisième script :

```bash
sudo file -bi /opt/regional/bootstrap/oracle/003_customer_multi_account_seed.sql
sudo grep -n $'\r' /opt/regional/bootstrap/oracle/003_customer_multi_account_seed.sql | head
sudo diff -u regional_003_github.sql /opt/regional/bootstrap/oracle/003_customer_multi_account_seed.sql
sha256sum regional_003_github.sql
```

**Résultat :** ASCII, pas de CRLF ; comparaison textuelle a trouvé cinq substitutions `NID`/`NOMREST` pour les CLI `0801001` à `0801005`, associations de comptes inchangées. L'opérateur a décidé **de ne pas remplacer** le seed Contabo. Ne pas copier les identités elles-mêmes dans les documents ; utiliser uniquement données synthétiques autorisées, sinon risque de confidentialité.

## 4. Secrets : création, droits et accès

Sept secrets montés par Compose via `/run/secrets/` et chargés par Spring `configtree:/run/secrets/`. Les trois premiers ont été générés sur le VPS, les quatre Email/SMS initialement laissés vides car les canaux étaient désactivés. **Ne jamais regénérer les secrets d'une base existante ni la clé HMAC sans une procédure de rotation et de récupération métier.**

| Fichier | UID:GID | Mode | Taille observée initialement | Usage |
|---|---|---|---|---|
| `REGIONAL_TECHNICAL_DB_PASSWORD` | 0:0 | 0600 | 49 octets | PostgreSQL + Spring |
| `ORACLE_APP_PASSWORD` | 0:54321 | 0640 | 41 octets | Oracle MOCK + Spring |
| `REGIONAL_OTP_HMAC_KEYS` | 0:0 | 0600 | 47 octets | HMAC `v1` |
| `REGIONAL_OTP_EMAIL_USERNAME` | 0:0 | 0600 | 0 | Mailtrap à configurer |
| `REGIONAL_OTP_EMAIL_PASSWORD` | 0:0 | 0600 | 0 | Mailtrap à configurer |
| `REGIONAL_OTP_SMS_USERNAME` | 0:0 | 0600 | 0 | SMS désactivé |
| `REGIONAL_OTP_SMS_PASSWORD` | 0:0 | 0600 | 0 | SMS désactivé |

**Commande de création initiale, à conserver comme historique et à NE PAS rejouer sur volumes existants :**

```bash
# HISTORIQUE PREMIÈRE INSTALLATION SEULEMENT ; NE PAS RÉEXÉCUTER
sudo bash <<'SCRIPT'
set -euo pipefail
umask 077
DIR=/opt/regional/secrets
for name in REGIONAL_TECHNICAL_DB_PASSWORD ORACLE_APP_PASSWORD REGIONAL_OTP_HMAC_KEYS; do
  test ! -e "$DIR/$name" || { echo "ALREADY_EXISTS: $name"; exit 1; }
done
openssl rand -hex 24 > "$DIR/REGIONAL_TECHNICAL_DB_PASSWORD"
openssl rand -hex 20 > "$DIR/ORACLE_APP_PASSWORD"
printf 'v1:' > "$DIR/REGIONAL_OTP_HMAC_KEYS"
openssl rand -base64 32 | tr -d '\n' >> "$DIR/REGIONAL_OTP_HMAC_KEYS"
chown root:root "$DIR/REGIONAL_TECHNICAL_DB_PASSWORD" "$DIR/REGIONAL_OTP_HMAC_KEYS"
chmod 0600 "$DIR/REGIONAL_TECHNICAL_DB_PASSWORD" "$DIR/REGIONAL_OTP_HMAC_KEYS"
chown root:54321 "$DIR/ORACLE_APP_PASSWORD"; chmod 0640 "$DIR/ORACLE_APP_PASSWORD"
SCRIPT
```

Initialisation fichiers Email/SMS (historique ; ne pas effacer des credentials Mailtrap ultérieurs) :

```bash
# PREMIÈRE INSTALLATION : n'écrire que si le fichier n'existe pas
sudo bash <<'SCRIPT'
set -euo pipefail
umask 077
DIR=/opt/regional/secrets
for n in REGIONAL_OTP_EMAIL_USERNAME REGIONAL_OTP_EMAIL_PASSWORD \
         REGIONAL_OTP_SMS_USERNAME REGIONAL_OTP_SMS_PASSWORD; do
  test -e "$DIR/$n" || install -o root -g root -m 0600 /dev/null "$DIR/$n"
done
SCRIPT
sudo bash -c 'for f in /opt/regional/secrets/*; do stat -c "%n owner=%u:%g mode=%a bytes=%s" "$f"; done'
```

Tests de lecture Docker **effectivement réussis**, sans impression des valeurs :

```bash
# Oracle UID/GID 54321 : a retourné ORACLE_SECRET_READABLE
sudo docker run --rm --network none --read-only --user 54321:54321 \
 --mount type=bind,src=/opt/regional/secrets/ORACLE_APP_PASSWORD,dst=/run/secrets/ORACLE_APP_PASSWORD,readonly \
 --entrypoint /bin/sh gvenzl/oracle-free:23.26.1-slim-faststart \
 -c 'test -r /run/secrets/ORACLE_APP_PASSWORD && test -s /run/secrets/ORACLE_APP_PASSWORD && echo ORACLE_SECRET_READABLE'
# PostgreSQL root : POSTGRES_SECRET_READABLE_ROOT ; UID 70 : POSTGRES_SECRET_DENIED_UID70 (refus ATTENDU)
sudo docker run --rm --network none --read-only --user 0:0 \
 --mount type=bind,src=/opt/regional/secrets/REGIONAL_TECHNICAL_DB_PASSWORD,dst=/run/secrets/REGIONAL_TECHNICAL_DB_PASSWORD,readonly \
 --entrypoint /bin/sh postgres:16-alpine \
 -c 'test -r /run/secrets/REGIONAL_TECHNICAL_DB_PASSWORD && echo POSTGRES_SECRET_READABLE_ROOT'
sudo docker run --rm --network none --read-only --user 70:70 \
 --mount type=bind,src=/opt/regional/secrets/REGIONAL_TECHNICAL_DB_PASSWORD,dst=/run/secrets/REGIONAL_TECHNICAL_DB_PASSWORD,readonly \
 --entrypoint /bin/sh postgres:16-alpine \
 -c 'if test -r /run/secrets/REGIONAL_TECHNICAL_DB_PASSWORD; then echo POSTGRES_SECRET_READABLE_UID70; else echo POSTGRES_SECRET_DENIED_UID70; fi'
```

Le conteneur API (image ci-dessous, `Config.User` vide => root) a aussi été testé avec trois montages `--network none --read-only` et a affiché `READABLE` pour PostgreSQL/Oracle/HMAC ; l'exécution Spring avec `configtree` a ensuite été démontrée indirectement par le démarrage + Flyway. L'utilisateur root en conteneur est un **risque de durcissement ultérieur** : exécuter nonroot nécessitera une analyse de permissions + changement de build approuvé.

**Authentification GHCR :** connexion PAT temporaire via `docker login ghcr.io ... --password-stdin`, `docker logout` puis nettoyage du répertoire temporaire, sans imprimer le PAT. Docker a signalé une configuration potentiellement non chiffrée dans `/root/.docker/config.json`. Contrôle JSON ultérieur : `GHCR_AUTH_ENTRY_PRESENT=False`, `CREDENTIAL_STORE_CONFIGURED=False`, `GHCR_CREDENTIAL_HELPER_CONFIGURED=False`. Ne pas supprimer le fichier root Docker global ni supposer absence d'autres mécanismes externes de credential. Le PAT SMTP précédemment cité en discussion n'a aucun rapport avec le token GHCR.

## 5. Properties, images, réseau et reproductibilité

### 5.1 Valeurs non secrètes `.env` observées au GO

```dotenv
REGIONAL_API_IMAGE=ghcr.io/sixgen-solutions/regional-core-banking-api@sha256:bb4abf5c6460b776cec77da12b514555325e466632aca13f66c5d11d671d4666
REGIONAL_POSTGRES_IMAGE=postgres:16-alpine
REGIONAL_ORACLE_IMAGE=gvenzl/oracle-free:23.26.1-slim
REGIONAL_API_LOOPBACK_PORT=9093
SERVER_PORT=9092
REGIONAL_TECHNICAL_DB_URL=jdbc:postgresql://postgres:5432/regional_core_banking?currentSchema=core_banking
REGIONAL_TECHNICAL_DB_USERNAME=regional_core_banking_app
ORACLE_JDBC_URL=jdbc:oracle:thin:@//customer-oracle:1521/FREEPDB1
ORACLE_APP_USERNAME=REGIONAL_MOCK
REGIONAL_FINANCIAL_INSTITUTION_CODE=REGIONAL
REGIONAL_OTP_HMAC_ACTIVE_KEY_VERSION=v1
REGIONAL_OTP_EMAIL_ENABLED=false
REGIONAL_OTP_EMAIL_HOST=
REGIONAL_OTP_EMAIL_PORT=587
REGIONAL_OTP_EMAIL_TLS=true
REGIONAL_OTP_EMAIL_SENDER=
REGIONAL_OTP_EMAIL_CONNECTION_TIMEOUT_MS=5000
REGIONAL_OTP_EMAIL_READ_TIMEOUT_MS=5000
REGIONAL_OTP_EMAIL_WRITE_TIMEOUT_MS=5000
REGIONAL_OTP_SMS_ENABLED=false
REGIONAL_OTP_SMS_BASE_URL=
REGIONAL_OTP_SMS_SENDER=LAREGIONALE
REGIONAL_OTP_SMS_CONNECT_TIMEOUT_MS=5000
REGIONAL_OTP_SMS_READ_TIMEOUT_MS=5000
```

**Oracle non-Faststart :** `gvenzl/oracle-free:23.26.1-slim` digest `sha256:3c7f96bc7da254da1f4223809b3762705f3117fb999a92d27a41b2f39966f2d7` ; tag actuellement utilisé dans `.env`, recommandation future de pin digest après revue. **API :** image digest ci-dessus, `linux/amd64`, commit OCI `52f31ec...`, `ENTRYPOINT=["java","-jar","/opt/regional-core-banking-api/app.jar"]`; contrairement à `EXPOSE 8080` image, `SERVER_PORT=9092` gouverne réellement l'écoute. Docker publie **uniquement** `127.0.0.1:9093->9092/tcp` ; `8080/tcp` affiché dans `ps` n'est pas publié. **PostgreSQL :** image `postgres:16-alpine`, version observée `16.16`, port `5432/tcp` interne seul.

### 5.2 Configuration Spring effective `/opt/regional/config/application-production.yml`

- `SPRING_PROFILES_ACTIVE=production`; `SPRING_CONFIG_LOCATION=file:/etc/regional/application-production.yml` (remplace la configuration packagée) ; `spring.config.import=configtree:/run/secrets/`.
- `spring.mail.host/port/username/password` dérivés des variables et secrets ; `mail.smtp.starttls.enable` et timeouts 5000 ms.
- `regional.technical-datasource`: PostgreSQL `core_banking`; `regional.banking.database.vendor=oracle` **MOCK uniquement**, JDBC `FREEPDB1`, utilisateur `REGIONAL_MOCK`.
- `regional.confirmation.persistence=jdbc`, HMAC `v1`, email `enabled=false` au dernier état confirmé, SMS `enabled=false`.
- `management.endpoint.health.probes.enabled=true`, détails `never`. Correction *locale VPS* ajoutée après incident SMTP :

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  health:
    mail:
      enabled: false
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
```

- SpringDoc `/v3/api-docs` et `/swagger-ui.html` actifs dans l'image : avertissement startup ; à revoir avant toute exposition réseau future (ne pas les ouvrir automatiquement).
- **Ne pas mettre les mots de passe dans `.env`** ; les secrets fournis par les fichiers montés sont résolus par le `configtree` Spring.

### 5.3 Vérification de la configuration effective sans fuite

```bash
sudo grep -A 13 '^management:' /opt/regional/config/application-production.yml
sudo grep -E '^REGIONAL_OTP_(EMAIL|SMS)_ENABLED=' /opt/regional/config/.env
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml \
 config --format json | python3 -c 'import json,sys; s=json.load(sys.stdin)["services"]["regional-core-banking-api"]; print("Image:",s["image"]);print("Secrets:",len(s.get("secrets",[])));print("Networks:",list(s.get("networks",{})))'
```

**Résultat :** image OCI sélectionnée, 7 secrets, réseaux `regional_access`, `regional_private`; Compose `config --quiet` réussi. Ces 2 réseaux sont **Regional-owned**, pas partagés avec SIXPAY. Le réseau `regional_private` est `internal:true`, `regional_access` `internal:false` pour l'API (sorties SMTP éventuelles).

## 6. Journal d'exécution : commandes, succès et échecs

### 6.1 Staging : erreurs de commande résolues

| Commande / situation | But | Résultat observé | Résolution / statut |
|---|---|---|---|
| `install -o root -g root -m 0644 /home/sixpayadmin/001_create_core_banking_schema.sql /opt/regional/bootstrap/postgres/001_create_core_banking_schema.sql` **sans sudo** | Installer SQL | `Permission denied` (destination protégée) | Rejouée avec `sudo install`, puis contenu SQL 194 octets confirmé |
| `compose.yml` avec `healthcheck:` Oracle mal indenté (3 espaces) | Config YAML | Erreur structurelle identifiée à la lecture ; pas de code retour non nul consigné pour la version défectueuse | Alignement corrigé, `config --quiet` puis `echo $?` => `0` |
| Noms initdb Oracle locaux incorrects | Faire exécuter 001/002/003 en bon ordre | Les noms attendus manquaient ; staging incomplet | `cp -n`, `cmp`, suppression des seuls noms incorrects ; trois noms canoniques présents |
| `sudo bash validate_stage.sh` AVANT création secrets | Gate fichiers | `MISSING SECRET` x7 ; `STAGING INCOMPLETE` | Création puis `STAGING FILES PRESENT` |
| `sudo grep -n $'\r' ...003... | head` | Rechercher CRLF | Aucune ligne | Absence de `\r` ; différence SHA due à cinq données modifiées, pas à CRLF |
| `diff -u` référence GitHub vs seed Contabo | Vérifier le contenu | Cinq lignes `NID`/`NOMREST` différentes | **Variante locale approuvée et conservée**, pas un échec SQL |
| `sudo matchpathcon -V ...` ; `sudo ausearch -m AVC,USER_AVC -ts recent` | SELinux | `verified` ; `<no matches>` | Pas de changement SELinux généralisé |
| `sudo docker run ... --user 70:70 ...` | Contrôle d'accès PostgreSQL | `POSTGRES_SECRET_DENIED_UID70` | Refus **attendu** pour fichier root:root 0600 ; entrypoint PostgreSQL a bien initialisé la base |
| `docker image ls ... | grep -i regional` avant pull | Présence image API | Aucune ligne | Image OCI ensuite téléchargée avec succès |
| `sudo docker test -f /root/.docker/config.json` | Contrôle stockage GHCR | Fichier présent | Inspection JSON sans impression des credentials : aucune entrée GHCR explicite |

*Historique hors jalon actuel* : une commande SQL exploratoire antérieure `select table from user_tables order by table_name;` a produit `ORA-00936` car `TABLE` n'est pas le champ recherché ; la commande de catalogue correcte est `SELECT table_name FROM user_tables ORDER BY table_name;`. Ne pas confondre avec un échec de bootstrap Contabo.

### 6.2 PostgreSQL — première initialisation et validation

```bash
sudo find /var/lib/regional/postgres/data -mindepth 1 -maxdepth 1 -printf '%f\n'
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml up -d --no-deps postgres
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps postgres
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=80 postgres
sudo docker exec regional-core-banking-mvp-postgres-1 psql \
  -U regional_core_banking_app -d regional_core_banking \
  -c "SELECT schema_name,schema_owner FROM information_schema.schemata WHERE schema_name='core_banking';"
sudo ls -ldZ /var/lib/regional/postgres/data
sudo docker inspect regional-core-banking-mvp-postgres-1 \
  --format 'Health={{.State.Health.Status}} RestartCount={{.RestartCount}}'
```

**Observé :** répertoire initialement vide ; conteneur `healthy`, `RestartCount=0`, port conteneur 5432 uniquement ; log `CREATE DATABASE`, `CREATE SCHEMA`, `database system is ready to accept connections` ; propriétaire `core_banking -> regional_core_banking_app`. Avertissements non bloquants `sh: locale: not found`, `no usable system locales`, `initdb: enabling trust authentication for local connections` (le dernier concerne connexions **locales au conteneur**, à examiner dans une revue de durcissement). Pas d'erreur bloquante.

### 6.3 Oracle Faststart — échec réel puis remédiation réussie

**Échec initial :**

```bash
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml up -d --no-deps customer-oracle
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps customer-oracle
sudo docker inspect regional-core-banking-mvp-customer-oracle-1 \
  --format 'Status={{.State.Status}} Health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} RestartCount={{.RestartCount}}'
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=120 customer-oracle
```

Image **défaillante** : `gvenzl/oracle-free:23.26.1-slim-faststart`. `Restarting (205)`, `Health=unhealthy`, jusqu'à **14 redémarrages**, `ORA-00205: error in identifying control file`, `CONTAINER: database already initialized`, listener sans service. **Le script SQL 003 n'en était pas la cause démontrée** : Oracle échouait avant ouverture de la base. Bind mount d'un volume initialement vide sur `/opt/oracle/oradata` avec variante Faststart : incompatibilité probable étayée par le résultat de remédiation, pas causalité prouvée par une trace Oracle interne. Le volume ne contenait que `dbconfig/FREE/{spfileFREE.ora,orapwFREE,listener.ora,tnsnames.ora,sqlnet.ora}` et aucun `.ctl`.

**Diagnostic et confinement :**

```bash
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml stop customer-oracle
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps -a
sudo find /var/lib/regional/oracle/data -maxdepth 3 -printf '%y %p %s bytes\n' | head -80
sudo find /var/lib/regional/oracle/data -type f \( -iname '*.ctl' -o -iname 'spfile*' \) -printf '%p %s bytes\n'
sudo docker inspect regional-core-banking-mvp-customer-oracle-1 \
  --format 'Status={{.State.Status}} ExitCode={{.State.ExitCode}} RestartCount={{.RestartCount}}'
```

**Rétablissement exécuté par l'opérateur :**

```bash
sudo docker pull gvenzl/oracle-free:23.26.1-slim
sudo docker image inspect gvenzl/oracle-free:23.26.1-slim \
  --format 'Architecture={{.Architecture}} User={{.Config.User}} Digests={{json .RepoDigests}}'
# Après vérification explicite Status=exited ; ARCHIVAGE, pas suppression :
sudo bash -c '
set -euo pipefail
SOURCE=/var/lib/regional/oracle/data
ARCHIVE=/var/lib/regional/oracle/data-failed-faststart-20261115
test -d "$SOURCE" && test ! -e "$ARCHIVE"
mv -- "$SOURCE" "$ARCHIVE"
echo ORACLE_FAILED_VOLUME_PRESERVED
'
sudo install -d -o 54321 -g 54321 -m 0750 /var/lib/regional/oracle/data
sudo ls -ldZ /var/lib/regional/oracle/data
sudo find /var/lib/regional/oracle/data -mindepth 1 -maxdepth 1
# Éditer .env : REGIONAL_ORACLE_IMAGE=gvenzl/oracle-free:23.26.1-slim
sudo nano /opt/regional/config/.env
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml up -d --no-deps customer-oracle
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps customer-oracle
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=180 customer-oracle
```

**Résultat :** image `amd64`, utilisateur `oracle`, digest `sha256:3c7f96bc7da254da1f4223809b3762705f3117fb999a92d27a41b2f39966f2d7`. Volume ancien archivé `data-failed-faststart-20261115`, nouveau vide, `Database mounted`, puis `healthy`, `RestartCount=0`, `DATABASE IS READY TO USE!`, exécution des scripts `001/002/003` (fin globale `DONE: Executing user-defined scripts`). Seed : 5 clients, 15 comptes = 9 actifs / 6 inactifs :

| CLI | Comptes | Actifs | Inactifs |
|---|---:|---:|---:|
| 0801001 | 1 | 1 | 0 |
| 0801002 | 2 | 1 | 1 |
| 0801003 | 3 | 2 | 1 |
| 0801004 | 4 | 2 | 2 |
| 0801005 | 5 | 3 | 2 |

Journaux : contrôle de fin du seed `no rows selected` et `Commit complete.`. Avertissement de version Oracle `23.26.1` ancienne ; messages `Checkpoint not complete` transitoires, à surveiller s'ils se répètent. Requête SQL de validation directe clients/comptes **proposée, mais pas de résultat d'exécution fourni** ; ne pas la déclarer passée :

```bash
sudo docker exec -i regional-core-banking-mvp-customer-oracle-1 sqlplus -s / as sysdba <<'SQL'
ALTER SESSION SET CONTAINER = FREEPDB1;
SET LINESIZE 160
SET PAGESIZE 100
SELECT c.CLI, COUNT(a.NCP) AS NB_COMPTES
FROM REGIONAL_MOCK.BKCLI c
LEFT JOIN REGIONAL_MOCK.BKCOM a ON a.CLI = c.CLI
WHERE c.CLI BETWEEN '0801001' AND '0801005'
GROUP BY c.CLI ORDER BY c.CLI;
EXIT;
SQL
```

### 6.4 Image applicative, GHCR, acceptation des écarts

```bash
sudo docker pull \
  ghcr.io/sixgen-solutions/regional-core-banking-api@sha256:bb4abf5c6460b776cec77da12b514555325e466632aca13f66c5d11d671d4666
sudo docker image inspect \
  ghcr.io/sixgen-solutions/regional-core-banking-api@sha256:bb4abf5c6460b776cec77da12b514555325e466632aca13f66c5d11d671d4666 \
  --format 'USER={{.Config.User}} ARCH={{.Architecture}} REVISION={{index .Config.Labels "org.opencontainers.image.revision"}} ENTRYPOINT={{json .Config.Entrypoint}}'
```

**Observé :** `USER=` (pas de directive USER, exécution root), `ARCH=amd64`, `REVISION=52f31ec9441bd86facd65cb8e5de94638ec695c8`, entrypoint Java JAR. Différence avec baseline `6b5d66b…` limitée à ajout/variation du seed Oracle 003 selon comparaison GitHub disponible au moment du choix ; **écart d'image accepté explicitement**. La branche a ensuite avancé à `b187f0ca…`, donc toute future édition doit revérifier HEAD/contrat. Ne pas republier involontairement en assimilant tag `sha-52f31ec…` à `6b5d66b…`.

### 6.5 Démarrage Spring Boot, Flyway et incident Health SMTP

```bash
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps postgres customer-oracle
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml up -d --no-deps regional-core-banking-api
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps
sudo docker inspect regional-core-banking-mvp-regional-core-banking-api-1 \
  --format 'Status={{.State.Status}} ExitCode={{.State.ExitCode}} RestartCount={{.RestartCount}}'
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=120 regional-core-banking-api
curl --noproxy '*' --connect-timeout 5 --max-time 15 -sS -i http://127.0.0.1:9093/actuator/health
sudo docker port regional-core-banking-mvp-regional-core-banking-api-1
```

**Premier résultat :** API `running`, `RestartCount=0`; Java 21.0.13, Spring Boot 3.5.6, profil `production`, Tomcat 9092. Hikari PostgreSQL connecté. Flyway a créé son historique, **validé puis appliqué V1 « create confirmation store », V2 « create payment execution store », V3 « create payment event number reservation store »**, sans erreur. Port publié `9092/tcp -> 127.0.0.1:9093`. **Échec réel** : `/actuator/health` HTTP **503** `{"status":"DOWN"}`. Liveness et readiness indépendamment **200 UP** :

```bash
for endpoint in liveness readiness; do
  echo "=== $endpoint ==="
  curl --noproxy '*' --connect-timeout 5 --max-time 15 -sS -i \
    "http://127.0.0.1:9093/actuator/health/$endpoint"
done
```

**Cause précise observée dans logs :** `MailHealthIndicator: Mail health check failed`; `Couldn't connect to host, port: localhost, 587`; `Connection refused`. Comme `REGIONAL_OTP_EMAIL_ENABLED=false` mais le bean JavaMail existe, l'indicateur Actuator de santé SMTP restait actif. **Correction appliquée sur fichier VPS** uniquement : `management.health.mail.enabled=false`. Cette propriété désactive le healthcheck de Mail, **pas** l'envoi SMTP. 

```bash
sudo nano /opt/regional/config/application-production.yml
sudo grep -A 13 '^management:' /opt/regional/config/application-production.yml
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml \
  up -d --no-deps --force-recreate regional-core-banking-api
curl --noproxy '*' --connect-timeout 5 --max-time 15 -sS -i http://127.0.0.1:9093/actuator/health
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=60 regional-core-banking-api
```

**Résultat final constaté le 15/11/2026 :** HTTP **200**, `{"status":"UP","groups":["liveness","readiness"]}` ; PostgreSQL `healthy`, Oracle `healthy`, API `Up`. Flyway au redémarrage : `Successfully validated 3 migrations`, `Current version ...: 3`, `Schema ... up to date. No migration necessary.` SpringDoc avertit que Swagger UI et `/v3/api-docs` sont activés : un sujet de durcissement avant connectivité. `management.health.mail.enabled=false` reste en place pendant le sandbox Mailtrap pour ne pas rendre toute la banque indisponible si SMTP externe chute.

## 7. État courant figé à la passation

**Fonctionnel selon preuves fournies :** 3 conteneurs Regional actifs ; PostgreSQL + Oracle `healthy`; API Spring Boot répond HTTP 200 `UP` sur loopback ; SQL technique et migration V1–V3 validés ; seed Oracle de test 5 clients / 15 comptes vérifié par journaux SQL. Aucune preuve de paiement E2E, de remise d'OTP, de connectivité SMTP ou de connectivité SIXPAY → Regional. Les commandes ci-dessus ont été exécutées par l'opérateur dans les tours précédents, sauf là où elles sont explicitement marquées proposées / historiques ou illustratives.

**Non exécuté / non confirmé :** Mailtrap sur Contabo (identifiants SMTP vides dans le dernier état confirmé, email disabled), test TCP/STARTTLS depuis VPS et conteneur API, test OTP réel dans sandbox, contrôle SQL direct des comptes par requête proposée, sauvegardes/restauration RPO/RTO, réseau privé SIXPAY backend → Regional, filtrage firewall avec IP stable, auth inter-app/mTLS, observabilité long terme, durcissement nonroot, protections Swagger/OpenAPI, métriques de charge. L'ancien volume Faststart demeure archivé. Ne pas effectuer de paiements réels.

## 8. Suite opératoire contrôlée — Mailtrap SMTP Sandbox

La configuration **locale** partagée comportait `REGIONAL_OTP_EMAIL_ENABLED=true`, `sandbox.smtp.mailtrap.io:587`, `REGIONAL_OTP_EMAIL_TLS=true`, expéditeur de test `no-reply@regional-test.local`. **Ne pas reproduire dans la documentation le username/password partagés en clair précédemment ; renouveler le mot de passe SMTP via Mailtrap avant tout test VPS.** Conserver la clé HMAC déjà générée : ne pas copier l'ancienne clé locale d'exemple.

### 8.1 Prérequis et contrôles en lecture seule

```bash
sudo grep -E '^REGIONAL_OTP_(EMAIL|SMS)_ENABLED=|^REGIONAL_OTP_EMAIL_(HOST|PORT|TLS|SENDER)=' /opt/regional/config/.env
sudo stat -c '%n owner=%u:%g mode=%a bytes=%s' \
  /opt/regional/secrets/REGIONAL_OTP_EMAIL_USERNAME \
  /opt/regional/secrets/REGIONAL_OTP_EMAIL_PASSWORD
# Depuis hôte ; tests TCP/TLS uniquement, sans authentification
getent ahostsv4 sandbox.smtp.mailtrap.io
timeout 10 bash -c 'echo > /dev/tcp/sandbox.smtp.mailtrap.io/587' \
  && echo MAILTRAP_TCP_REACHABLE || echo MAILTRAP_TCP_FAILED
timeout 15 openssl s_client -starttls smtp \
  -connect sandbox.smtp.mailtrap.io:587 \
  -servername sandbox.smtp.mailtrap.io -brief < /dev/null
```

**Attendu :** DNS résolu, TCP accessible, STARTTLS négocié et certificat validé. **Non exécuté au 15/11.** Le test depuis l'hôte ne garantit pas l'egress du réseau `regional_access`; vérifier ensuite depuis un conteneur de test dans ce réseau **sans imprimer les secrets** et sans modifier la stack.

### 8.2 Rotation des credentials et secrets

Obtenir **de nouveaux identifiants SMTP** depuis Mailtrap Email Sandbox/Integration/SMTP. Écrire à travers un terminal interactif privilégié **sans echo ni historique**, avec permission `root:root 0600` et sans copier les valeurs en chat. Exemple de procédure à **valider/adapter** (remplace les deux fichiers, ne touche pas HMAC et DB) :

```bash
sudo bash <<'SCRIPT'
set -euo pipefail
umask 077
DIR=/opt/regional/secrets
read -r -p 'Mailtrap SMTP username: ' SMTP_USER
read -r -s -p 'Mailtrap SMTP password: ' SMTP_PASS
printf '\n'
test -n "$SMTP_USER" && test -n "$SMTP_PASS" || { echo 'EMPTY_SMTP_CREDENTIAL'; exit 1; }
printf '%s' "$SMTP_USER" > "$DIR/REGIONAL_OTP_EMAIL_USERNAME"
printf '%s' "$SMTP_PASS" > "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
chown root:root "$DIR/REGIONAL_OTP_EMAIL_USERNAME" "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
chmod 0600 "$DIR/REGIONAL_OTP_EMAIL_USERNAME" "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
unset SMTP_USER SMTP_PASS
echo MAILTRAP_SECRET_FILES_CONFIGURED
SCRIPT
sudo stat -c '%n owner=%u:%g mode=%a bytes=%s' /opt/regional/secrets/REGIONAL_OTP_EMAIL_*
```

**Attention technique :** les deux `read` au sein d'un `sudo bash <<'SCRIPT'` peuvent recevoir un EOF car le heredoc est l'entrée standard. **Pour une exécution fiable**, préférer `sudo bash -c '... read ... </dev/tty ...'` ou ouvrir `sudo -i` puis lire depuis `/dev/tty`, sans transmettre les identifiants dans les arguments du processus, dans l'historique ou par `echo`. Procédure robuste conseillée pour la nouvelle session :

```bash
sudo bash -c '
set -euo pipefail
umask 077
DIR=/opt/regional/secrets
read -r -p "Mailtrap SMTP username: " U </dev/tty
read -r -s -p "Mailtrap SMTP password: " P </dev/tty
printf "\n" >/dev/tty
[ -n "$U" ] && [ -n "$P" ] || exit 1
printf %s "$U" > "$DIR/REGIONAL_OTP_EMAIL_USERNAME"
printf %s "$P" > "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
chown root:root "$DIR/REGIONAL_OTP_EMAIL_USERNAME" "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
chmod 0600 "$DIR/REGIONAL_OTP_EMAIL_USERNAME" "$DIR/REGIONAL_OTP_EMAIL_PASSWORD"
unset U P
printf "MAILTRAP_SECRETS_WRITTEN\n"
'
```

**Éviter les interruptions pendant les deux écritures ;** pour une opération de production prévoir fichiers temporaires mode 0600 et remplacement atomique coordonné. Les secrets sont des fichiers montés : la recréation du conteneur doit être effectuée après mise à jour des secrets. Aucun test d'envoi ne doit utiliser les credentials anciennement publiés.

### 8.3 Configuration email sans modification de code

Après validation Mailtrap et décision d'activation, modifier uniquement `/opt/regional/config/.env` (ne pas insérer de username/password) :

```dotenv
REGIONAL_OTP_EMAIL_ENABLED=true
REGIONAL_OTP_EMAIL_HOST=sandbox.smtp.mailtrap.io
REGIONAL_OTP_EMAIL_PORT=587
REGIONAL_OTP_EMAIL_TLS=true
REGIONAL_OTP_EMAIL_SENDER=no-reply@regional-test.local
REGIONAL_OTP_SMS_ENABLED=false
```

**Conserver** `REGIONAL_OTP_HMAC_ACTIVE_KEY_VERSION=v1` et le fichier secret HMAC sans modification. Garder `management.health.mail.enabled=false` sur le MVP ; tester SMTP via un vrai envoi OTP, pas en dégradant `/health`. `spring.mail.*` et le `regional.confirmation.email.enabled` sont déjà déclarés dans `application-production.yml`.

```bash
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml \
  up -d --no-deps --force-recreate regional-core-banking-api
curl --noproxy '*' --connect-timeout 5 --max-time 15 -sS -i http://127.0.0.1:9093/actuator/health
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml logs --tail=100 regional-core-banking-api
```

**Critères d'acceptation :** API reste `UP` ; logs pas d'erreurs de chargement de secrets ; créer un challenge OTP **conformément aux endpoints réellement retenus du contrat approuvé**, avec un client MOCK ayant une adresse contrôlée dans `BKEMACLI`; vérifier le message dans Mailtrap Sandbox, et la vérification métier OTP dans le parcours autorisé. Ne jamais inventer un endpoint ou rejouer aveuglément un paiement dont l'issue est inconnue. Les configurations Gmail/SMTP bancaires réelles ne découlent pas de Mailtrap.

## 9. Chantier suivant — chemin privé SIXPAY backend → Regional

**Non réalisé.** Le backend SIXPAY est sur un réseau Docker distinct (`sixpay-connect-cloud_sixpay_frontend_access`, adresse observée `172.19.0.3`, gateway `172.19.0.1`) ; IP dynamique tant que non fixée. Une proposition de gateway privée a été étudiée : Caddy hôte limité à `172.19.0.1:19093` → `127.0.0.1:9093`, filtrage **uniquement** de l'IP backend SIXPAY, sans relier les réseaux ni exposer l'API au public. Cette adresse/port est une **architecture proposée**, pas un listener installé ni une connectivité vérifiée. Dépendance : choix validé d'IP SIXPAY persistante ou d'un mécanisme firewall compatible avec IP dynamique. Le script préparatoire `02_firewall_private_gateway.sh --dry-run` a seulement présenté les règles envisagées ; **aucune règle appliquée**.

Séquence future : (1) inventaire réseau/SIXPAY actuel en lecture seule ; (2) traiter en tâche **distincte** la stabilité IP du backend (toute modification SIXPAY nécessite permission explicite) ; (3) valider architecture gateway, modèle de menace, filtrage source/méthodes/chemins, Caddy, SELinux, firewalld / DOCKER-USER ; (4) conserver Regional sans publication Internet ; (5) tester connectivité TCP/HTTP privé depuis le **seul backend** et refus depuis les conteneurs non autorisés, puis les tests API contractuels ; (6) définir sécurité inter-app, OAuth2 Client Credentials/mTLS selon décision distincte applicable, pas d'invention de scopes ; (7) ne pas exécuter d'opérations financières réelles. Le user a explicitement séparé l'étude OAuth2/mTLS du MVP en cours ; ne pas rouvrir spontanément ce débat pendant l'activation Mailtrap.

Contrôles de découverte **lecture seule** à refaire lorsque ce chantier démarre :

```bash
sudo docker network ls
sudo docker inspect sixpay-connect-ldap-backend-1 \
  --format '{{json .NetworkSettings.Networks}}'  # contient adresses réseau, pas credentials
sudo ss -lntp | grep -E ':(9093|19093)\b' || true
sudo firewall-cmd --get-active-zones
sudo firewall-cmd --list-all
sudo iptables -S DOCKER-USER
sudo docker port regional-core-banking-mvp-regional-core-banking-api-1
```

Ne pas changer Caddy, Firewall, réseau ou `sixpay-connect` sur la seule base de ce runbook. Le projet Regional est indépendant de SIXPAY.

## 10. Reprise, rollback, garde-fous et décisions

- Les scripts `docker-entrypoint-initdb.d` (PostgreSQL/Oracle) s'exécutent sur **première initialisation** seulement. Ne pas « corriger » les données en supprimant un volume. `docker compose down -v`, `rm -rf /var/lib/regional/...`, `flyway clean`, migrations non approuvées : **interdits sans décision explicite**.
- En cas d'échec du seul service API, relever `ps`, `logs`, `docker inspect`, `/actuator/health` ; recréer uniquement ce service après revue. En cas d'échec DB, arrêter seulement le service concerné et conserver le volume pour diagnostic.
- Changer `.env`/secrets/config puis recréer **uniquement** `regional-core-banking-api` si les changements concernent l'API ; ne pas toucher aux DB saines.
- L'image Oracle 23.26.1 affiche un avertissement d'ancienneté : planifier une montée de version et sauvegarde/restauration distinctes ; ne pas l'effectuer pendant le test Mailtrap.
- Sécurité : le profil `production` signifie *runtime Contabo du MVP MOCK* et **non** disponibilité bancaire réelle. SpringDoc actif, API Java root, données de test possiblement personnelles, SMTP credentials précédemment exposés : risques connus. Backups, RPO/RTO, alerting, rotation et durcissement à planifier.
- Toute nouvelle modification du repository exige vérification HEAD : dernière révision lue `b187f0ca2e805712012755c966313ae50e0d7b50`, différente de la référence historique. **Ce document est livré hors repository pour revue**, pas committé.

## 11. Matrice de résultats et état Git

| Gate | Preuve observée | Statut |
|---|---|---|
| Staging fichiers | `STAGING FILES PRESENT` | PASS |
| Compose | `config --quiet`, exit 0 | PASS |
| Secrets Docker | Oracle, PostgreSQL, API `READABLE`, refus UID70 attendu | PASS (tests ciblés) |
| SELinux | Contexte conteneur sur volumes, pas d'AVC rapporté | PASS (contrôles ciblés) |
| PostgreSQL | Healthy, schema owner attendu | PASS |
| Oracle Faststart | ORA-00205, exit 205 | FAIL historique, archivé |
| Oracle slim | Healthy, bootstrap 001/002/003 | PASS |
| Data multi-account | 5 clients, 15 comptes, log SQL | PASS (preuve logs) |
| API startup | Tomcat 9092, production, HTTP 200 | PASS |
| Flyway | V1–V3 exécutées, puis validate sans migration au restart | PASS |
| Health SMTP auto | HTTP 503 initial | FAIL historique, corrigé |
| Global health après correction | HTTP 200 `UP` | PASS |
| SMTP Mailtrap depuis VPS | Pas encore exécuté | PENDING |
| OTP email sandbox | Pas encore exécuté | PENDING |
| SIXPAY → Regional private gateway | Pas encore exécuté | PENDING |
| Auth inter-app / mTLS | Workstream distinct | PENDING |
| GitHub write | Aucune écriture | NOT EXECUTED (volontaire) |

**Git final du périmètre de rédaction :** repository uniquement lu, aucun fichier créé/modifié/supprimé dans GitHub, aucun commit, push, PR, merge ni changement de branche. **VPS :** les commandes ci-dessus ont été exécutées par l'opérateur, pas par le rédacteur. **Statut opérationnel :** `MVP_RUNNING / PENDING_MAILTRAP_AND_PRIVATE_GATEWAY`. **Statut d'analyse du projet :** `INCOMPLETE_CONTEXT` pour les capacités non encore testées.
