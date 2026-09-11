REGIONAL CORE BANKING API
HANDOFF — Repository autonome, OpenAPI contract-first et starter pack
Source SIXPAY observée : main @ b6da7db33432cb81997cc293b21080dd46fdcc14
Repository cible : SIXGEN-Solutions/regional-core-banking-api
# Identification
# 1. Décision structurante mise à jour
regional-core-banking-api est une API bancaire autonome de La Régionale. Elle ne doit pas être conçue comme un serveur propre à SIXPAY. SIXPAY est un consommateur de cette API, au même titre que de futures applications internes ou partenaires autorisées.
Le contrat OpenAPI Regional est propriétaire du nouveau repository. Il ne s'appuie pas sur les modèles Java/DTO SIXPAY et ceux-ci ne constituent pas son modèle canonique.
En revanche, les clients Core Banking déjà présents dans SIXPAY constituent une preuve de compatibilité consommateur. La V1 initiale du contrat Regional doit donc vérifier qu'elle reste compatible, pour les opérations conservées, avec les requêtes/réponses réellement émises et attendues par SIXPAY au niveau wire/protocole : méthode/path, headers, JSON, statuts HTTP, codes métier, idempotence et recovery.
La compatibilité ne signifie pas que les noms de classes Java, packages, modèles de domaine ou structures internes du serveur Regional doivent reproduire ceux de SIXPAY.
# 2. Principes contractuels
Le nouveau contrat canonique sera contracts/openapi/regional-core-banking-api-v1.yaml.
Les cinq contrats Amplitude existants de SIXPAY sont copiés comme REFERENCE_ONLY pour préserver les décisions et interfaces déjà approuvées côté consommateur.
Le registre SIXPAY reste autoritatif uniquement pour le statut des contrats du repository SIXPAY. Le nouveau repository devra définir sa propre gouvernance contractuelle.
Aucun DTO/model/port/adapter/client SIXPAY n'est modifié par ce chantier.
Aucun endpoint ou schéma Regional n'est généré tant que le contrat Regional correspondant n'est pas approuvé.
Les placeholders actuels, notamment les payloads fournisseur encore ouverts dans le contrat Payment Event, doivent être résolus à partir de preuves bancaires approuvées avant génération serveur.
# 3. Capacités initiales à reprendre comme baseline fonctionnelle
Customer / Account Verification.
Payment Confirmation / OTP.
Payment Event / execution-time funds controls / recovery.
Payment execution context lookups lorsque réellement nécessaires à la V1.
Accounting Entries T1.
End-of-Day / TFJ lookup.
Client sortant TFJ vers le webhook SIXPAY lorsque cette intégration est retenue.
TRESOR PAY reste hors du contrat serveur Core Banking Regional.
# 4. Architecture du nouveau repository
Une seule application Spring Boot déployable, organisée par capacité, avec la direction de dépendance :
api -> application -> domain <- infrastructure.
Le modèle OpenAPI, le modèle de domaine, le modèle Amplitude et la représentation Informix restent distincts. Toutes les dépendances à Amplitude/Informix sont confinées aux adapters infrastructure/amplitude.
Le repository ne reprend pas mécaniquement la topologie Maven multi-module de SIXPAY. Il crée ses propres conventions, gates et documentation.
# 5. Source pack autonome pour un nouveau projet ChatGPT
Le nouveau projet ChatGPT doit recevoir au minimum :
ce handoff ;
REGIONAL_CORE_BANKING_API_PROJECT_INSTRUCTIONS.md ;
REGIONAL_CORE_BANKING_API_ARCHITECTURE.md ;
REGIONAL_CORE_BANKING_API_IMPLEMENTATION_PLAN.md ;
le draft contracts/openapi/regional-core-banking-api-v1.yaml ;
REGIONAL_CORE_BANKING_API_STARTER_MANIFEST.txt ;
les cinq contrats Amplitude SIXPAY et le CONTRACT_REGISTRY.yaml en reference/sixpay/ ;
les documents d'architecture et runbooks listés dans le manifest ;
un export ciblé des clients/DTO/mappers/tests SIXPAY utilisés pour les tests de compatibilité, sans copier tout le codebase.
# 6. Règle de classification des documents copiés
Les documents copiés depuis SIXPAY dans le nouveau projet ne deviennent pas automatiquement des sources autoritatives Regional.
Classification recommandée :
REGIONAL_CANONICAL : fichiers créés et approuvés dans le nouveau repository.
SIXPAY_REFERENCE : contrats/docs/code issus de SIXPAY pour contexte et compatibilité.
BANK_REFERENCE : informations/procédures/mapping fournis par La Régionale.
HISTORICAL : jamais utilisé comme spécification active.
DEFERRED : jamais utilisé pour générer le MVP.
Chaque fichier copié doit conserver son chemin source et le SHA SIXPAY d'origine dans un manifest.
# 7. OpenAPI draft fourni dans le starter pack
Un workspace regional-core-banking-api-v1.yaml est préparé avec :
identité Regional propre ;
statut DRAFT / PENDING_APPROVAL / REFERENCE_ONLY / codeGenerationAllowed: false ;
chemins fonctionnels déjà supportés par les contrats SIXPAY actifs comme points de travail ;
schémas volontairement non figés (PendingRegionalSchema) afin d'éviter de prétendre que les modèles SIXPAY constituent les modèles du serveur ;
mention explicite que la compatibilité SIXPAY doit être vérifiée avant validation V1.
Ce draft n'est pas prêt pour génération.
# 8. Lots recommandés
R0 : créer le nouveau repository + importer le starter pack.
R1 : définir/valider les schémas et détails du contrat Regional V1 ; construire la matrice de compatibilité SIXPAY.
R2 : bootstrap Spring Boot/Maven/structure.
R3 : génération API boundary + CI/gates.
R4 : Customer/Account.
R5 : Payment Confirmation/OTP.
R6 : Payment Execution/Context/Recovery.
R7 : Accounting T1.
R8 : TFJ/End-of-Day.
R9 : sandbox, sécurité, Informix et déploiement.
# 9. Décisions encore nécessaires
outil/version OpenAPI Generator et périmètre généré ;
accès Informix exact : JDBC/procédures/services, comptes techniques et transactions ;
mappings bancaires exacts et champs provider ;
mécanisme réel d'OTP/SMS derrière La Régionale ;
OAuth2 issuer/token endpoint, scopes Regional, mTLS, certificats/trust stores et allowlists ;
éventuelle base technique propre pour idempotence/audit/recovery ;
stratégie de versionnement et release du nouveau repository ;
statut final de chaque endpoint/contexte avant approbation V1.
# 10. Prompt de démarrage du nouveau projet ChatGPT
@GitHub
Repository cible : SIXGEN-Solutions/regional-core-banking-api.
Agis en tant qu'architecte logiciel, analyste fonctionnel et ingénieur backend Java/Spring.
Commence par lire REGIONAL_CORE_BANKING_API_PROJECT_INSTRUCTIONS.md, puis le présent handoff, l'architecture, l'implementation plan et le starter manifest.
Le produit est une API bancaire autonome de La Régionale, réutilisable par SIXPAY et d'autres applications.
Règle contractuelle :
le contrat OpenAPI Regional appartient à ce repository ;
il n'est pas dérivé des DTO/models Java SIXPAY ;
SIXPAY est un consommateur et ses clients existants servent uniquement de baseline de compatibilité wire/protocole pour la V1 ;
ne modifie jamais SIXPAY depuis ce repository ;
n'invente ni endpoints, ni champs, ni statuts, ni mapping Informix sans source approuvée.
Premier objectif : R1 — finaliser le contrat OpenAPI Regional V1 à partir des capacités approuvées, des preuves bancaires disponibles et de la matrice de compatibilité SIXPAY, sans génération de code tant que le contrat n'est pas explicitement approuvé.
# Statut final
HANDOFF READY — REGIONAL_CORE_BANKING_API AUTONOMOUS REPOSITORY STARTER PACK PREPARED.
