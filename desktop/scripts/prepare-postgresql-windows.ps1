param(
    [Parameter(Mandatory = $true)][string]$ArchivePath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$Sha256
)

$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path $ArchivePath).Path
$actual = (Get-FileHash -Algorithm SHA256 $archive).Hash
if ($actual -ine $Sha256) { throw "Empreinte SHA-256 PostgreSQL incorrecte : $actual" }
$desktop = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$destination = Join-Path $desktop 'bundle\postgresql'
$staging = Join-Path $desktop 'bundle\postgresql.windows.pending'
if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
New-Item -ItemType Directory -Force $staging | Out-Null
try {
    Expand-Archive -LiteralPath $archive -DestinationPath $staging
    $candidates = @(Get-ChildItem $staging -Recurse -File -Filter 'postgres.exe' | Where-Object { $_.Directory.Name -eq 'bin' })
    if ($candidates.Count -ne 1) { throw 'Archive PostgreSQL ambiguë ou sans postgres.exe.' }
    $root = $candidates[0].Directory.Parent.FullName
    foreach ($name in @('postgres.exe', 'initdb.exe', 'pg_ctl.exe', 'pg_isready.exe', 'psql.exe', 'pg_dump.exe', 'pg_restore.exe')) {
        if (-not (Test-Path (Join-Path $root "bin\$name"))) { throw "Binaire PostgreSQL manquant : $name" }
    }
    foreach ($relative in @('lib', 'share', 'share\extension\btree_gist.control', 'server_license.txt', 'commandlinetools_3rd_party_licenses.txt')) {
        if (-not (Test-Path (Join-Path $root $relative))) { throw "Ressource PostgreSQL manquante : $relative" }
    }
    if (Test-Path $destination) { Remove-Item $destination -Recurse -Force }
    New-Item -ItemType Directory -Force $destination | Out-Null
    foreach ($name in @('bin', 'lib', 'share', 'server_license.txt', 'commandlinetools_3rd_party_licenses.txt')) {
        Copy-Item (Join-Path $root $name) (Join-Path $destination $name) -Recurse -Force
    }
    Write-Host "PostgreSQL Windows vérifié et préparé dans $destination"
} finally {
    if (Test-Path $staging) { Remove-Item $staging -Recurse -Force }
}
