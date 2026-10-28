param(
    [Parameter(Mandatory = $true)][string]$PostgresArchive,
    [Parameter(Mandatory = $true)][ValidatePattern('^[0-9a-fA-F]{64}$')][string]$PostgresSha256,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'dist')
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
$runtime = Join-Path $output 'runtime'
$postgres = Join-Path $output 'postgresql'
$jdk = $env:JAVA_HOME
if (-not [Environment]::Is64BitOperatingSystem -or -not [Environment]::Is64BitProcess) { throw 'Windows x64 requis.' }
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\jlink.exe'))) { throw 'JAVA_HOME doit désigner un JDK 17 Windows x64 avec jlink.' }
$release = Get-Content (Join-Path $jdk 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="17\.') { throw 'Java 17 requis.' }
$archive = (Resolve-Path $PostgresArchive).Path
if ((Get-FileHash $archive -Algorithm SHA256).Hash -ine $PostgresSha256) { throw 'Empreinte PostgreSQL incorrecte.' }
if (Test-Path $output) { throw "Le dossier de sortie existe déjà : $output" }
New-Item -ItemType Directory -Path $output | Out-Null

Push-Location (Join-Path $repo 'backend')
try {
    & mvn.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du backend échouée.' }
} finally { Pop-Location }
Copy-Item (Join-Path $repo 'backend\target\sirh-0.0.1-SNAPSHOT.jar') (Join-Path $output 'backend.jar')

Push-Location (Join-Path $repo 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'Installation des dépendances frontend échouée.' }
    $env:VITE_API_BASE_URL = '/api/'
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du frontend échouée.' }
} finally { Pop-Location }
Copy-Item (Join-Path $repo 'frontend\dist') (Join-Path $output 'web') -Recurse

$modules = 'java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs'
& (Join-Path $jdk 'bin\jlink.exe') --module-path (Join-Path $jdk 'jmods') --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Création du runtime Java échouée.' }

$staging = Join-Path $output 'postgresql-staging'
Expand-Archive -LiteralPath $archive -DestinationPath $staging
$candidates = @(Get-ChildItem $staging -Recurse -File -Filter 'postgres.exe' | Where-Object { $_.Directory.Name -eq 'bin' })
if ($candidates.Count -ne 1) { throw 'Archive PostgreSQL ambiguë ou invalide.' }
$pgRoot = $candidates[0].Directory.Parent.FullName
$pgVersion = & (Join-Path $pgRoot 'bin\postgres.exe') --version
if ($LASTEXITCODE -ne 0 -or $pgVersion -notmatch 'PostgreSQL\) 15\.') { throw "PostgreSQL 15 requis : $pgVersion" }
foreach ($name in @('postgres.exe', 'initdb.exe', 'pg_ctl.exe', 'pg_isready.exe', 'psql.exe', 'pg_dump.exe', 'pg_restore.exe')) {
    if (-not (Test-Path (Join-Path $pgRoot "bin\$name"))) { throw "Binaire PostgreSQL manquant : $name" }
}
New-Item -ItemType Directory -Path $postgres | Out-Null
foreach ($name in @('bin', 'lib', 'share')) { Copy-Item (Join-Path $pgRoot $name) (Join-Path $postgres $name) -Recurse }
Remove-Item -LiteralPath $staging -Recurse -Force
Write-Host "Paquet web local préparé : $output"
