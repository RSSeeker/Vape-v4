param(
    [string]$BuildDir = "build/native",
    [string]$Version = "",
    [string]$Subject = "CN=OpenVape",
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) {
    if (-not $Quiet) { Write-Host "[sign] $msg" }
}

# Resolve the injector version from the dist folder when not supplied.
if ([string]::IsNullOrWhiteSpace($Version)) {
    $candidate = Get-ChildItem -Path (Join-Path $BuildDir "dist") -Filter "Vape-v*.exe" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($candidate -and $candidate.Name -match '^Vape-v(.+)\.exe$') { $Version = $Matches[1] }
}

$dllPath = Join-Path $BuildDir "dist/Vape-v4.21Native.dll"
$exePath = Join-Path $BuildDir "dist/Vape-v$Version.exe"

if (-not (Test-Path $dllPath)) { throw "missing native dll: $dllPath" }
if (-not (Test-Path $exePath)) { throw "missing injector exe: $exePath" }

# Locate signtool.exe from the Windows SDK (prefer the newest x64 build).
$signtool = $null
$sdkRoot = "C:\Program Files (x86)\Windows Kits\10\bin"
if (Test-Path $sdkRoot) {
    $signtool = Get-ChildItem -Path $sdkRoot -Directory |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName "x64\signtool.exe" } |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}
if (-not $signtool) {
    $cmd = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($cmd) { $signtool = $cmd.Source }
}
if (-not $signtool) {
    Write-Step "signtool.exe not found; skipping Authenticode signing"
    exit 0
}

# Locate the code-signing certificate in the current user's personal store.
# NOTE: Get-ChildItem's -CodeSigningCert switch only exists in PowerShell 7+, so the
# certificate is looked up by subject + private key to stay PS 5.1 compatible.
$cert = Get-ChildItem Cert:\CurrentUser\My -ErrorAction SilentlyContinue |
    Where-Object { $_.Subject -eq $Subject -and $_.HasPrivateKey } |
    Sort-Object NotAfter -Descending |
    Select-Object -First 1
if (-not $cert) {
    Write-Step "code signing certificate '$Subject' not present; skipping Authenticode signing"
    exit 0
}
Write-Step "using certificate $($cert.Thumbprint) ($($cert.Subject))"

function Invoke-Sign($file) {
    if (-not (Test-Path $file)) { throw "missing file to sign: $file" }
    Write-Step "signing $file"
    & $signtool sign /fd SHA256 /sha1 $($cert.Thumbprint) /tr http://timestamp.digicert.com /td SHA256 $file | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "signtool sign failed for $file" }
}
$timeoutMs = 120000

# 1) Sign the product DLL first, so the loader embeds already-signed bytes.
Invoke-Sign $dllPath

# 2) Rebuild the GUI loader so its embedded RCDATA resource picks up the signed DLL.
Write-Step "re-embedding signed DLL into the injector"
& cmake --build $BuildDir --config Release --target Vape421LoaderGui | Out-Null
if ($LASTEXITCODE -ne 0) { throw "rebuilding Vape421LoaderGui failed" }

# 3) Sign the single-file injector after the final link.
Invoke-Sign $exePath

# 4) Verify both signatures (chain trust depends on the local root store).
foreach ($file in @($dllPath, $exePath)) {
    & $signtool verify /pa $file | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Step "verification failed for $file (certificate may not be trusted locally)"
    } else {
        Write-Step "verified $file"
    }
}

Write-Step "done"
