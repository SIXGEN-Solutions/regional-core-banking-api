
docker run --rm `
  alpine:3.23 `
  sh -c 'apk add --no-cache curl >/dev/null && curl -v --noproxy localhost --connect-to localhost:9443:host.docker.internal:9443 http://localhost:9443/actuator/health'

Write-Host "DOCKER_EXIT_CODE=$LASTEXITCODE"
