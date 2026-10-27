param(
    [Parameter(Mandatory = $true)][string]$PostgresArchive,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$PostgresSha256
)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw 'Ce build exige Windows x64.' }
if (-not [Environment]::Is64BitOperatingSystem -or -not [Environment]::Is64BitProcess) {
    throw 'Ce build exige Windows x64.'
}
$desktop = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $PSScriptRoot 'prepare-backend-windows.ps1')
& (Join-Path $PSScriptRoot 'prepare-postgresql-windows.ps1') -ArchivePath $PostgresArchive -Sha256 $PostgresSha256
Push-Location $desktop
try {
    Push-Location (Join-Path $desktop '..\frontend')
    try {
        $env:VITE_API_BASE_URL = '/api/'
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Build du frontend échoué.' }
    } finally { Pop-Location }
    & (Join-Path $desktop 'node_modules\.bin\tauri.cmd') build --bundles nsis
    if ($LASTEXITCODE -ne 0) { throw 'Build NSIS échoué.' }
} finally { Pop-Location }
$setups = @(Get-ChildItem (Join-Path $desktop 'src-tauri\target\release\bundle\nsis') -File -Filter '*-setup.exe')
if ($setups.Count -eq 0) { throw 'Aucun setup Windows produit.' }
$setups | ForEach-Object { Write-Host "Setup Windows prêt : $($_.FullName)" }
