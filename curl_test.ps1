$PkiV2 = Join-Path $env:TEMP "regional-mtls-integration\pki-v2"

$SecurePassword = Read-Host "Mot de passe du PKCS12 client SIXPAY" -AsSecureString

$PasswordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePassword)

try {
    $ClientPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($PasswordPtr)

    curl.exe -v `
      --tlsv1.3 --tls-max 1.3 `
      --ssl-no-revoke `
      --cacert (Join-Path $PkiV2 "regional-test-ca.crt") `
      --cert-type P12 `
      --cert (Join-Path $PkiV2 "sixpay-client.p12") `
      --pass $ClientPassword `
      https://localhost:9443/actuator/health

    Write-Host "CURL_EXIT_CODE=$LASTEXITCODE"
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($PasswordPtr)
    Remove-Variable ClientPassword -ErrorAction SilentlyContinue
}