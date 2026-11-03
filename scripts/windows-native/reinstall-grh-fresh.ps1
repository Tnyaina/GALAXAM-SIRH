param(
    [string]$Package = (Join-Path $PSScriptRoot 'dist\GRH-fresh-20261103'),
    [string]$Installation = 'D:\GALAXAM-GRH'
)
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath $Package).Path
$installationRoot = [IO.Path]::GetFullPath($Installation).TrimEnd('\')
$psql = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\psql.exe'
$database = 'sirh_galaxam'
$role = 'galaxam_prod_admin'
if ($installationRoot -ine 'D:\GALAXAM-GRH') { throw 'Ce parcours est limite a D:\GALAXAM-GRH.' }

foreach ($required in @(
    $psql,
    (Join-Path $packageRoot 'app\backend.jar'),
    (Join-Path $packageRoot 'app\web\index.html'),
    (Join-Path $packageRoot 'app\schema-manifest.json'),
    (Join-Path $packageRoot 'install-grh.ps1'),
    (Join-Path $packageRoot 'scripts\migrate-grh.ps1')
)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Fichier requis absent : $required" }
}
$ddl = @(1..6 | ForEach-Object {
    $found = @(Get-ChildItem -LiteralPath (Join-Path $packageRoot 'schema\bdd') -Filter ('{0:D2}_*.sql' -f $_) -File)
    if ($found.Count -ne 1) { throw "DDL canonique manquant ou ambigu : $_" }
    $found[0]
})
if (-not (Test-Path -LiteralPath $installationRoot -PathType Container)) {
    throw "Installation actuelle absente : $installationRoot"
}
foreach ($name in @('SIRH_PROD_DB_PASSWORD', 'SIRH_PROD_JWT_SECRET', 'SIRH_ADMIN_NAME', 'SIRH_ADMIN_EMAIL', 'SIRH_ADMIN_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'User'))) {
        throw "Variable utilisateur manquante : $name"
    }
}
if ([Environment]::GetEnvironmentVariable('SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED', 'User') -ne 'true') {
    throw 'SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED doit valoir true pour recréer le premier administrateur.'
}

Write-Host 'Cette opération EFFACE la base et les fichiers locaux de GRH.' -ForegroundColor Yellow
$answer = Read-Host "Tapez exactement $database pour confirmer"
if ($answer -cne $database) { throw 'Réinstallation annulée.' }
$secure = Read-Host 'Mot de passe PostgreSQL du compte postgres' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $superPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }

$oldInstallation = "$installationRoot-before-fresh-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
$resetSql = Join-Path $env:TEMP ("galaxam-fresh-$PID-" + [guid]::NewGuid().ToString('N') + '.sql')
$databaseReset = $false
try {
    $env:PGPASSWORD = $superPassword
    $probe = & $psql -w -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d postgres -Atc 'SELECT 1'
    if ($LASTEXITCODE -ne 0 -or $probe -ne '1') { throw 'Connexion administrateur PostgreSQL impossible. Rien n a ete efface.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $installationRoot 'scripts\stop-app.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Arret de GRH impossible. Rien n a ete efface.' }

    @(
        'DROP SCHEMA IF EXISTS public CASCADE;',
        "CREATE SCHEMA public AUTHORIZATION $role;",
        'CREATE EXTENSION IF NOT EXISTS btree_gist;',
        "SET ROLE $role;"
    ) | Set-Content -LiteralPath $resetSql -Encoding Ascii
    $arguments = @('-w', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '--single-transaction', '-h', '127.0.0.1', '-U', 'postgres', '-d', $database, '-f', $resetSql)
    foreach ($file in $ddl) { $arguments += @('-f', $file.FullName) }
    & $psql @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Recréation de la base échouée. La transaction SQL a été annulée.' }
    $databaseReset = $true
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue

    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packageRoot 'scripts\migrate-grh.ps1') -MigrationsDirectory (Join-Path $packageRoot 'schema\migrations')
    if ($LASTEXITCODE -ne 0) { throw 'Application des migrations échouée.' }

    Move-Item -LiteralPath $installationRoot -Destination $oldInstallation
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $packageRoot 'install-grh.ps1') -Destination $installationRoot -ExistingUploads (Join-Path $packageRoot 'aucun-upload-a-importer')
    if ($LASTEXITCODE -ne 0) { throw 'Installation des nouveaux fichiers échouée.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $installationRoot 'scripts\start-app.ps1') -NoBrowser
    if ($LASTEXITCODE -ne 0) { throw 'GRH ne démarre pas après installation.' }
    $oldResolved = [IO.Path]::GetFullPath($oldInstallation)
    if (-not $oldResolved.StartsWith('D:\GALAXAM-GRH-before-fresh-', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Chemin de nettoyage invalide.'
    }
    try { Remove-Item -LiteralPath $oldResolved -Recurse -Force }
    catch { Write-Warning "GRH fonctionne, mais l'ancienne installation reste a nettoyer : $oldResolved" }
    Write-Host "GRH réinstallé avec une base et des uploads neufs : $installationRoot" -ForegroundColor Green
} catch {
    if (-not $databaseReset) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $installationRoot 'scripts\start-app.ps1') -NoBrowser | Out-Null
    }
    throw
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    $superPassword = $null
    Remove-Item -LiteralPath $resetSql -ErrorAction SilentlyContinue
}
