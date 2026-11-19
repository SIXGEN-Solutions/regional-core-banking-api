# Regional Core Banking API — Lot 3 : deux modes de transport

Révision de départ : `feat/payment-orchestration-worflow` @ `59470c39361f8f9288ddcc57721ff1935e4ae2c2`.

## Contrat transport et authentification

L'OpenAPI Regional expose **mutualTLS** pour les appels SIXPAY → Regional.
Aucun `Authorization: Bearer` OAuth2 n'est requis sur ce flux. La confiance
CA X.509 ne suffit pas : Regional impose également l'empreinte SHA-256
approuvée du certificat client. Les contrôles métiers restent indépendants.

Les contrats du flux **partenaire → SIXPAY** ne sont pas concernés.
La modification de sécurité du contrat doit encore être ratifiée par les
responsables banque/sécurité ; ce lot n'invente aucune preuve d'approbation.

## Modes pris en charge

| Mode | ENABLE_MTLS | REGIONAL_MTLS_REQUIRED | Transport |
|---|---|---|---|
| Local Docker / mock isolé | false | false | HTTP local uniquement |
| Mode sécurisé | true | true | HTTPS TLSv1.3, certificat client obligatoire |

Les paramètres sont contrôlés par `RegionalMtlsStartupGuard` et
`RegionalMtlsClientCertificateFilter`. Le mode sécurisé ne doit jamais
retomber en HTTP, même lorsque le keystore ou le truststore est absent.

## Checkpoints négatifs / positifs

1. Certificat client de la CA approuvée et empreinte autorisée : TLS établi,
   autorisation technique possible ; vérifier ensuite le code HTTP.
2. Même CA, empreinte non approuvée : HTTP 403.
3. Sans certificat ou avec CA inconnue : handshake refusé.
4. HTTP clair sur port HTTPS ou TLS 1.2 (TLSv1.3 seul) : rejet.
5. `REGIONAL_MTLS_REQUIRED=true` avec `ENABLE_MTLS=false` : démarrage refusé.
6. Keystore ou truststore manquant en mode mTLS : démarrage refusé.

## Commandes proposées — à exécuter localement

```powershell
.\mvnw.cmd "-Dtest=RegionalMtlsStartupGuardTest,RegionalMtlsClientCertificateFilterTest,RegionalOpenApiMtlsOnlyContractTest,SwaggerUiCanonicalContractTest" test
.\mvnw.cmd verify
```

Pour l'HTTPS réel, employer une URL correspondant au SAN du certificat serveur
et des certificats **de test**, sans `curl -k` :

```bash
curl --tlsv1.3 --tls-max 1.3 --cacert regional-test-ca.crt \
  --cert sixpay-client.crt --key sixpay-client.key \
  https://localhost:9443/actuator/health
```

Un test Java MockMvc ou une inspection OpenAPI ne remplace pas une preuve de
handshake TLS sur socket réelle.
