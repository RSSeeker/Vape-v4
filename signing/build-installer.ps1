param(
    [string]$CertificatePath = "$PSScriptRoot/../.signing/OpenVape-codesign.cer",
    [string]$OutDirectory = "$PSScriptRoot/dist",
    [string]$CscPath = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $CertificatePath)) {
    throw "certificate not found: $CertificatePath"
}
if (-not (Test-Path $CscPath)) {
    throw "C# compiler not found: $CscPath"
}

New-Item -ItemType Directory -Force -Path $OutDirectory | Out-Null

$certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
    (Resolve-Path $CertificatePath).Path)
$base64 = [Convert]::ToBase64String($certificate.RawData)

$templatePath = Join-Path $PSScriptRoot "InstallVapeCert.cs"
$source = [System.IO.File]::ReadAllText($templatePath)
$source = $source.Replace("%CERT_BASE64%", $base64)

$generatedSource = Join-Path $OutDirectory "InstallVapeCert.generated.cs"
[System.IO.File]::WriteAllText($generatedSource, $source, (New-Object System.Text.UTF8Encoding($false)))

$outputPath = Join-Path $OutDirectory "OpenVape-Cert.exe"
$manifestPath = Join-Path $PSScriptRoot "installer.manifest"

& $CscPath /nologo /target:winexe /optimize+ /platform:anycpu `
    "/win32manifest:$manifestPath" `
    "/out:$outputPath" `
    /r:System.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Security.dll `
    $generatedSource
if ($LASTEXITCODE -ne 0) {
    throw "csc failed with exit code $LASTEXITCODE"
}

# Sign the installer itself as well, so it carries the same publisher identity.
$signtool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin" -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName "x64\signtool.exe" } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert -ErrorAction SilentlyContinue |
    Where-Object { $_.Subject -eq "CN=OpenVape" -and $_.HasPrivateKey } |
    Select-Object -First 1
if ($signtool -and $cert) {
    Write-Host "[installer] signing $outputPath"
    & $signtool sign /fd SHA256 /sha1 $($cert.Thumbprint) /tr http://timestamp.digicert.com /td SHA256 $outputPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "signtool sign failed for installer" }
} else {
    Write-Host "[installer] signtool or certificate unavailable; installer left unsigned"
}

Write-Host "[installer] built $outputPath"
