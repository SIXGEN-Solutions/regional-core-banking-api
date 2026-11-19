# PROMPT DE REPRISE — Regional Core Banking API / Contabo / Mailtrap / SIXPAY

> **À copier intégralement comme premier message dans une nouvelle conversation, avec l'accès GitHub si disponible.** Ce document est une consigne de travail, non une demande de réinstallation. Date de référence des observations : **15 novembre 2026**.

---

Agis en tant qu'**architecte logiciel senior, analyste fonctionnel bancaire, tech lead Java/Spring Boot, ingénieur DevOps / SRE et expert Linux/Docker/SELinux**. Je poursuis la mise en service de **Regional Core Banking API** de La Régionale, hébergée sur mon VPS Contabo à côté de SIXPAY. **Ne recommence pas les travaux déjà réalisés**. Commence par des contrôles ciblés de l'état présent, puis avance graduellement vers **(A) simulation d'envoi OTP Mailtrap**, ensuite **(B) connectivité strictement privée du backend SIXPAY vers Regional**, sans faire de modification non autorisée.

## 0. Règles de gouvernance impératives

- **Repository implémentation unique :** `SIXGEN-Solutions/regional-core-banking-api`. Branche historiquement sélectionnée : `feat/payment-orchestration-worflow` @ `6b5d66b54f9eb46b942e1a43a04614b721c29637`. Au contrôle de rédaction, la branche a avancé à **`b187f0ca2e805712012755c966313ae50e0d7b50`**. Avant une éventuelle écriture Git, revérifie le HEAD et demande mon approbation si la cible est ambiguë ou a changé. **Analyse, runbook, prompt et configuration proposées ≠ autorisation de commit/push/PR/merge/branch/release.**
- **Consumer de référence uniquement :** `SIXGEN-Solutions/sixpay-connect`, **lecture seule**, sauf si je lance explicitement une tâche de modification SIXPAY distincte. Ne traite jamais Regional comme un composant interne SIXPAY.
- **Modes :** GitHub lecture seule ; VPS opéré manuellement par `sixpayadmin` via SSH et `sudo`. Tu ne peux pas prétendre avoir exécuté une commande sur le VPS : je fournirai les sorties. Énoncer repository/ref, mode, statut du contexte à chaque analyse.
- **Contrat :** contrat OpenAPI Regional approuvé uniquement comme source canonique ; aucun endpoint/statut/idempotence/OTP/procédure bancaire inventé ; pas d'import des DTO SIXPAY ; aucune manipulation financière non fondée. Informatique bancaire réelle Informix/Amplitude hors périmètre : Oracle Free est **MOCK synthétique**.
- **Sécurité :** secrets jamais en chat/GitHub/`.env`/logs ; aucun PAT/OTP/mot de passe SMTP en clair ; rotation des credentials Mailtrap partagés par erreur précédemment ; conserver les secrets de DB et HMAC déjà créés. `SPRING_CONFIG_LOCATION` cible le fichier runtime ; `configtree:/run/secrets/` monte les secrets. **Ne pas désactiver SELinux, ni ouvrir Regional au public.** OAuth2/mTLS est une piste de travail distincte : ne pas inventer d'implémentation, scopes, certificat ni politique.
- **Changement contrôlé :** commandes une étape à la fois, distinguer lecture seule, modification VPS, modification Git. Ne pas toucher PostgreSQL/Oracle sains ; ne jamais exécuter `docker compose down -v`, `rm -rf` sur volumes, `flyway clean` ou rejouer une opération financière dont l'issue est inconnue.

## 1. État réel atteint sur Contabo — ne pas reprendre depuis zéro

**VPS :** `vmi3671192.contaboserver.net`, AlmaLinux 10.2, SELinux Enforcing, firewalld et Caddy déjà présents, Docker Compose v5.7.0, ~8 vCPU AMD EPYC, 23 GiB RAM (~20 libres à l'inventaire), ~287 GiB disque libre, pas de swap (MVP accepté). Les capacités sont des observations historiques, pas des mesures récentes.

**Compose project :** `regional-core-banking-mvp`, compose `/opt/regional/compose/compose.yml`, `.env` `/opt/regional/config/.env`, config Spring `/opt/regional/config/application-production.yml`; trois services **déjà démarrés** et validés :

| Service | État confirmé au dernier contrôle | Image / réseau |
|---|---|---|
| `postgres` | Up `(healthy)` ; DB `regional_core_banking` ; schéma `core_banking` propriété `regional_core_banking_app` | `postgres:16-alpine`; `regional_private` seulement ; aucun port hôte |
| `customer-oracle` | Up `(healthy)` ; bootstrap scripts 001/002/003 terminés ; `FREEPDB1` | `gvenzl/oracle-free:23.26.1-slim`; `regional_private` seulement ; aucun port hôte |
| `regional-core-banking-api` | Up ; Spring Boot 3.5.6/Java 21 ; profil `production` ; **`GET http://127.0.0.1:9093/actuator/health` -> HTTP 200 `UP`** | GHCR par digest ; réseaux `regional_private` + `regional_access`, port conteneur `9092` vers host loopback `9093` |

**Source image API déployée :**

```text
ghcr.io/sixgen-solutions/regional-core-banking-api@sha256:bb4abf5c6460b776cec77da12b514555325e466632aca13f66c5d11d671d4666
OCI revision: 52f31ec9441bd86facd65cb8e5de94638ec695c8
```

L'écart avec le commit de référence `6b5d66b...` a été **expressément accepté** pour ce MVP. `Dockerfile` expose 8080 à titre indicatif mais Spring écoute effectivement sur `SERVER_PORT=9092`. Image API lancée par root (pas de `USER` défini), durcissement à planifier séparément.

**Source Oracle non-Faststart :** `gvenzl/oracle-free:23.26.1-slim`, digest vérifié `sha256:3c7f96bc7da254da1f4223809b3762705f3117fb999a92d27a41b2f39966f2d7`. Le tag `23.26.1-slim` est actuellement dans `.env` (pas le digest). Ne pas revenir à `23.26.1-slim-faststart` : cette image a échoué sous volume persistant avec `ORA-00205`, redémarrages 14 fois, exit 205. Volume incomplet **archivé et conservé** sous `/var/lib/regional/oracle/data-failed-faststart-20261115`. Un volume Oracle sain neuf a été créé.

**Données MOCK Oracle :** seed `003_customer_multi_account_seed.sql` **variante Contabo volontairement conservée**, SHA-256 `50aaef068d73c64ce2219c940a8d7e7c42d72362e91e19dedaa661b66c91a173`. Diffère de GitHub de cinq paires `NID/NOMREST` ; IDs `CLI` et associations restent identiques. Ne pas remplacer ce script. Cinq clients CLI `0801001` à `0801005`, 1/2/3/4/5 comptes, 15 comptes au total (9 actifs, 6 inactifs). Scripts 001/002 identiques à GitHub.

**PostgreSQL technique :** premier bootstrap `CREATE DATABASE`, `CREATE SCHEMA`, `core_banking`. Flyway a **appliqué V1, V2, V3** au premier démarrage de l'API, validés au redémarrage et `No migration necessary`; ne jamais rejouer manuellement les migrations.

**SELinux/droits :** `/opt/regional` root:root 0750, `/opt/regional/secrets` root:root 0700, scripts SQL root:root 0644, secrets PostgreSQL et HMAC root:root 0600, secret Oracle root:54321 0640 ; volumes PostgreSQL UID 70 mode 0700 et Oracle UID 54321 mode 0750 ; `:Z` sur montages. Vérifications de lecture des trois secrets indispensables via Docker réussies. Derniers `ausearch` sans AVC.

**Sept secrets** montés `/run/secrets/` :

```text
REGIONAL_TECHNICAL_DB_PASSWORD (non vide, ne pas changer)
ORACLE_APP_PASSWORD (non vide, ne pas changer)
REGIONAL_OTP_HMAC_KEYS (v1, non vide, ne pas changer)
REGIONAL_OTP_EMAIL_USERNAME (vide au dernier état vérifié)
REGIONAL_OTP_EMAIL_PASSWORD (vide au dernier état vérifié)
REGIONAL_OTP_SMS_USERNAME (vide, SMS désactivé)
REGIONAL_OTP_SMS_PASSWORD (vide, SMS désactivé)
```

**État Email** au dernier contrôle : `REGIONAL_OTP_EMAIL_ENABLED=false`, `REGIONAL_OTP_EMAIL_HOST=` vide, port 587, TLS STARTTLS=true ; SMS désactivé. Au démarrage initial API, `/actuator/health` répondait **503 `DOWN`** car le `MailHealthIndicator` essayait localhost:587 et obtenait `Connection refused`, tandis que `/actuator/health/liveness` et `/readiness` répondaient 200 `UP`. **Correctif VPS déjà appliqué** dans `application-production.yml` : `management.health.mail.enabled=false`, suivi de recréation de **l'API uniquement** ; `/actuator/health` est maintenant **HTTP 200 `{"status":"UP","groups":["liveness","readiness"]}`**. Ne pas annuler ce correctif pendant l'activation Mailtrap : il désactive uniquement le healthcheck SMTP, **pas l'envoi Email OTP**. SpringDoc `/v3/api-docs` et Swagger UI encore activés (durcissement futur).

**État SIXPAY :** cinq conteneurs préexistants (backend, frontend, LDAP, Keycloak, postgres) laissés intacts. Le backend `sixpay-connect-ldap-backend-1` a été observé à `172.19.0.3` sur le réseau `sixpay-connect-cloud_sixpay_frontend_access`, gateway `172.19.0.1` ; **IP potentiellement dynamique**, à revérifier. API Regional n'est connectée à aucun réseau SIXPAY. Pas de Caddy/firewall gateway privé actif à ce stade.

**Journal intégral et historique des commandes :** le document compagnon `REGIONAL_CONTABO_MVP_INSTALLATION_EXECUTION_RUNBOOK_2026-11-15.md` doit être lu pour les commandes exactes, les modes d'échec historiques et les décisions validées. L'ancien runbook GitHub `documentation/runbooks/REGIONAL_CONTABO_MVP_DEPLOYMENT.md` était rédigé en mode « PREPARATION ONLY », ne pas le confondre avec l'exécution réellement observée.

## 2. Objectif immédiat : Mailtrap Email Sandbox — simuler l'envoi OTP depuis Contabo

Je veux **réutiliser la configuration SMTP Mailtrap de mon développement local**, mais jamais les identifiants déjà collés dans une précédente conversation. **Exige leur rotation via Mailtrap avant tout usage.** Ne pas reprendre les anciens credentials ni la clé HMAC `v1` d'exemple locale. Le fichier HMAC persistant VPS actuel demeure inchangé.

Configuration **non secrète** cible, après vérifications :

```dotenv
REGIONAL_OTP_EMAIL_ENABLED=true
REGIONAL_OTP_EMAIL_HOST=sandbox.smtp.mailtrap.io
REGIONAL_OTP_EMAIL_PORT=587
REGIONAL_OTP_EMAIL_TLS=true
REGIONAL_OTP_EMAIL_SENDER=no-reply@regional-test.local
REGIONAL_OTP_SMS_ENABLED=false
```

Le `sender` est une adresse d'exemple locale : vérifier qu'elle convient au sandbox Mailtrap et à la logique Java. Les credentials actualisés seront stockés dans `/opt/regional/secrets/REGIONAL_OTP_EMAIL_USERNAME` et `...EMAIL_PASSWORD`, **jamais** dans `.env`, Git, la ligne de commande ou les logs. Le service hors profil `local` résout l'adresse via le Core Banking (`BKEMACLI`) selon `documentation/runbooks/REGIONAL_PRODUCTION_EMAIL_OTP_ACTIVATION.md` (GitHub) ; le destinataire fixe `test-recipient` de `application-local.yml` n'est **pas** appliqué au profil `production` sur Contabo.

### Ordre des opérations demandé

**Phase M0 — État et analyse sans écriture.** Relire `documentation/runbooks/REGIONAL_PRODUCTION_EMAIL_OTP_ACTIVATION.md`, `deploy/contabo/application-production.yml`, les classes `EmailConfirmationDeliveryAdapter`, `BankingConfirmationRecipientAdapter`, la config `ConfirmationConfiguration` et l'OpenAPI Regional **à la révision explicitement observée de l'image ou à une révision dont les différences sont vérifiées**. Identifier l'endpoint officiel de création/consultation/vérification OTP, la charge utile, les préconditions et la stratégie idempotente ; NE RIEN INVENTER. Vérifier que les clients de test de `BKEMACLI` ont une adresse sûre et que la boîte Mailtrap utilisée permet de lire les messages. Ne divulguer ni PII réelles ni OTP dans les sorties partagées.

```bash
sudo docker compose --env-file /opt/regional/config/.env -f /opt/regional/compose/compose.yml ps
curl --noproxy '*' -sS -i http://127.0.0.1:9093/actuator/health
sudo grep -E '^REGIONAL_OTP_(EMAIL|SMS)_ENABLED=|^REGIONAL_OTP_EMAIL_(HOST|PORT|TLS|SENDER)=' /opt/regional/config/.env
sudo stat -c '%n owner=%u:%g mode=%a bytes=%s' \
 /opt/regional/secrets/REGIONAL_OTP_EMAIL_USERNAME \
 /opt/regional/secrets/REGIONAL_OTP_EMAIL_PASSWORD
sudo grep -A 13 '^management:' /opt/regional/config/application-production.yml
```

**Phase M1 — DNS/TCP/STARTTLS, lecture seule.** Vérifier si le VPS peut sortir vers Mailtrap. Test de l'hôte d'abord, puis du réseau Docker `regional_access` si nécessaire ; ne pas créer de service SMTP ni exposer de port entrant. Vérifier les limitations du fournisseur VPS, DNS et firewall en cas d'échec. Les tests hôte **ne prouvent pas** l'accès conteneur.

```bash
getent ahostsv4 sandbox.smtp.mailtrap.io
timeout 10 bash -c 'echo > /dev/tcp/sandbox.smtp.mailtrap.io/587' \
 && echo MAILTRAP_TCP_REACHABLE || echo MAILTRAP_TCP_FAILED
timeout 15 openssl s_client -starttls smtp \
 -connect sandbox.smtp.mailtrap.io:587 \
 -servername sandbox.smtp.mailtrap.io -brief < /dev/null
```

**Phase M2 — Rotation et injection de secrets.** Après renouvellement Mailtrap, ne jamais afficher les valeurs. Vérifier que les fichiers existent, owner root:root, mode 0600. Recommander une lecture interactive **depuis `/dev/tty`** plutôt qu'un `read` dans un heredoc qui consomme l'entrée standard. Procédure proposée :

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
echo MAILTRAP_SECRETS_WRITTEN
'
```

**Phase M3 — Activer sans rebuild.** Changer **seulement** les variables non secrètes Email dans `/opt/regional/config/.env` ; ne pas changer les secrets HMAC/DB, les volumes, les réseaux, PostgreSQL/Oracle. Laisser `management.health.mail.enabled=false` pour le MVP. Vérifier Compose `config --quiet`; recréer uniquement le service API :

```bash
sudo nano /opt/regional/config/.env
sudo docker compose --env-file /opt/regional/config/.env \
 -f /opt/regional/compose/compose.yml config --quiet
sudo docker compose --env-file /opt/regional/config/.env \
 -f /opt/regional/compose/compose.yml \
 up -d --no-deps --force-recreate regional-core-banking-api
sudo docker compose --env-file /opt/regional/config/.env \
 -f /opt/regional/compose/compose.yml ps
curl --noproxy '*' -sS -i http://127.0.0.1:9093/actuator/health
sudo docker compose --env-file /opt/regional/config/.env \
 -f /opt/regional/compose/compose.yml logs --tail=100 regional-core-banking-api
```

**Phase M4 — Scénario fonctionnel OTP sûr.** Construire le scénario **à partir des endpoints OpenAPI Regional existants**, du code effectivement déployé et du profil `production`, en incluant les en-têtes/identifiants réellement requis ; client Oracle MOCK et adresse contrôlée `BKEMACLI`; vérifier le challenge, le message Mailtrap, la vérification OTP, l'absence de fuite secret/OTP dans les logs, les cas négatifs (SMTP indisponible, adresse absente, timeout) en **environnement mock exclusivement**. Ne pas simuler une création de paiement par hasard pour déclencher OTP ; ne pas rejouer un paiement au résultat inconnu. En cas de blocage réseau ou de rejet Mailtrap, diagnostiquer sans abaisser TLS ni changer firewall sans autorisation. Fournir les preuves de chaque étape et un rollback ciblé `REGIONAL_OTP_EMAIL_ENABLED=false` + recréation API (sans rotation HMAC).

**Critères de fin M4 :** santé HTTP 200 maintenue ; authentification SMTP de test réalisée sans divulgation ; réception OTP dans Mailtrap ; vérification fonctionnelle conforme au contrat ; services DB et SIXPAY inchangés. Ne pas déclarer succès avant sorties d'exécution.

## 3. Chantier annoncé ensuite : connexion privée SIXPAY backend → Regional

Ce chantier **n'est pas fait** et ne doit **pas** être commencé par défaut avant clôture/accord explicite. Le besoin est que **seul le backend SIXPAY** consomme les endpoints Regional utiles, de manière privée sur le **même VPS**, sans exposer Regional à Internet ni connecter directement les réseaux Regional à ceux de SIXPAY. Caddy hôte déjà présent. Proposition historique **à analyser et approuver, non à appliquer directement** :

```text
SIXPAY backend (network sixpay-connect-cloud_sixpay_frontend_access)
  source IP observée : 172.19.0.3 (non garantie permanente)
  gateway réseau : 172.19.0.1
      -> host Caddy gateway 172.19.0.1:19093
      -> proxy local 127.0.0.1:9093
      -> Regional Core Banking API container:9092
```

**Préconditions** : valider stabilité IP backend SIXPAY (option IPAM static Docker Compose, ou autre mécanisme contrôlé) dans un **chantier explicitement autorisé SIXPAY** ; vérifier CIDR, routage, Docker bridge, Caddy, firewalld, DOCKER-USER/iptables, SELinux `httpd_can_network_connect`, méthode/route allowlist et séparation des réseaux, authentification inter-app (chantier distinct). L'IP `172.19.0.3` n'est **pas une identité fiable**, elle peut changer au recréation. Ne pas réutiliser les certificats, secrets ou politiques SIXPAY sans décision. Ne pas supposer une protection source-IP suffisante à elle seule pour une API bancaire réelle.

**Une préparation antérieure `02_firewall_private_gateway.sh --dry-run` n'a PAS appliqué de règles.** Ne pas annoncer de Caddy listener actif ni de flux backend-SIXPAY fonctionnel. Les noms DNS / TLS / OAuth2 et mTLS demeurent des décisions distinctes non approuvées pour la production bancaire. Aucune ouverture publique et pas d'interconnexion directe des réseaux Docker.

Commandes de découverte sans mutation, **à refaire** après Mailtrap :

```bash
sudo docker network ls
sudo docker inspect sixpay-connect-ldap-backend-1 --format '{{json .NetworkSettings.Networks}}'
sudo ss -lntp | grep -E ':(9093|19093)\b' || true
sudo firewall-cmd --get-active-zones
sudo firewall-cmd --list-all
sudo iptables -S DOCKER-USER
sudo docker port regional-core-banking-mvp-regional-core-banking-api-1
```

Attendu actuellement : `127.0.0.1:9093` seulement pour Regional et **aucun** port gateway `19093` si non configuré. Concevoir un protocole de tests positif (backend autorisé) et négatif (frontend/SIXPAY autres conteneurs interdits + accès externe impossible), audit et rollback. Les changements dans `sixpay-connect` sont **read-only** sans autorisation distincte explicite.

## 4. Incidents historiques déjà résolus : ne pas refaire

- Permissions : premier `install` sans `sudo` dans `/opt/regional/bootstrap/postgres` -> `Permission denied`; corrigé `sudo install` ; schéma SQL 194 octets créé.
- Noms scripts Oracle initdb erronés puis corrigés (3 noms canoniques), indent `healthcheck:` YAML réparé, `docker compose config --quiet` exit 0.
- Staging initial `MISSING SECRET` x7 -> 7 fichiers présents, test de lecture Oracle/PostgreSQL/API réussi. Refus lecture UID70 du secret PostgreSQL attendu ; entrypoint root a initialisé le cluster.
- Seed Contabo différent SHA GitHub : cinq données client modifiées seulement ; **variante Contabo expressément acceptée et conservée**.
- Faststart Oracle `ORA-00205`, exit 205, 14 retries, volume partiel conservé ; passage non-Faststart `gvenzl/oracle-free:23.26.1-slim` avec nouveau volume -> Oracle `healthy`, scripts OK.
- API premier `GET /actuator/health` HTTP 503, `MailHealthIndicator` localhost:587 connection refused ; `management.health.mail.enabled=false` appliqué sur VPS -> HTTP 200 `UP`, liveness/readiness UP.
- GHCR PAT : login/pull par digest puis logout ; fichier `/root/.docker/config.json` existant mais pas d'entrée `ghcr.io` explicite au contrôle. **Ne pas effacer root Docker config**.
- Le `grep` CRLF sur seed n'a rien retourné ; différence SQL textuelle et **non** problème d'encodage.

## 5. Premiers actes attendus de l'assistant dans la nouvelle conversation

1. **Accuser réception du baseline** : constater PostgreSQL/Oracle/API opérationnels avec dernier health `UP`, pas de nouvelle installation, pas de nouveaux secrets DB/HMAC, pas d'intervention SIXPAY.
2. **Confirmer la révision GitHub actuelle en lecture seule** et lire les fichiers nécessaires **sans mise à jour Git**. Confronter l'image déployée `52f31ec…` au code utilisé pour construire les requêtes OTP ; si divergence, signaler.
3. **Proposer M0/M1** : vérification de l'état Email actuel et de la connectivité DNS/SMTP depuis le VPS, avec commandes minimales, résultats attendus et critères de diagnostic.
4. **Préparer M2/M3/M4** : rotation des credentials Mailtrap, secrets interactifs sans fuite, `.env` nonsecret, API seule recréée, tests de mail et OTP basés sur le contrat, preuve de réception.
5. **Annoncer ensuite le lot SIXPAY-private-gateway**, en soulignant la dépendance IP backend et les permissions séparées ; ne pas appliquer de règles ni changer Caddy/SIXPAY sans accord.
6. **Rapporter chaque étape** : commandes réellement faites, résultat observé, statut `PASS/FAIL/PENDING`, impact SQL/volume, sécurité, Git state. Ne pas conclure à un succès SMTP ou SIXPAY avant preuve réelle.

**Dernier état opérationnel confirmé au 15/11/2026 :** trois services Regional fonctionnels ; endpoint HTTP 200 UP ; Mailtrap pas encore configuré/testé ; SIXPAY privé non connecté. **Statut `READY_FOR_MAILTRAP_CONFIGURATION` (état opérationnel de projet, non statut de validation bancaire).**
