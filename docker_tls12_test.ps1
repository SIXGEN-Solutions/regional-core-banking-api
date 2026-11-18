
$PkiV2 = Join-Path $env:TEMP "regional-mtls-integration\pki-v2"

docker run --rm `
  -v "${PkiV2}:/pki:ro" `
  alpine:3.23 `
  sh -c 'apk add --no-cache curl ca-certificates >/dev/null && curl -v --tlsv1.2 --tls-max 1.2 --cacert /pki/regional-test-ca.crt --cert /pki/sixpay-client.crt --key /pki/sixpay-client.key --connect-to localhost:9443:host.docker.internal:9443 --noproxy localhost https://localhost:9443/actuator/health'

Write-Host "DOCKER_EXIT_CODE=$LASTEXITCODE"
