docker run --rm -v "${UnknownPki}:/pki:ro" alpine:3.23 sh -c 'apk add --no-cache openssl >/dev/null && openssl x509 -in /pki/untrusted-client.crt -noout -subject -issuer -ext extendedKeyUsage && openssl verify -x509_strict -purpose sslclient -CAfile /pki/unknown-ca.crt /pki/untrusted-client.crt'

Write-Host "DOCKER_EXIT_CODE=$LASTEXITCODE"