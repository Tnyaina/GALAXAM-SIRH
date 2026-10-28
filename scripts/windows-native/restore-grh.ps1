param([Parameter(Mandatory)][string]$Archive)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path (Join-Path $root 'app\backend.jar'))) { $root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$pgBin = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin'
$source = (Resolve-Path -LiteralPath $Archive).Path
$temp = Join-Path $env:TEMP ("galaxam-restore-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    & tar.exe -xf $source -C $temp database.dump uploads.tar manifest.json
    if ($LASTEXITCODE -ne 0) { throw 'Archive illisible.' }
    $manifest = Get-Content (Join-Path $temp 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.format -ne 'galaxam-grh-native-v1' -or $manifest.database -ne 'sirh_galaxam') { throw 'Format de sauvegarde GRH invalide.' }
    if ((Get-FileHash (Join-Path $temp 'database.dump') -Algorithm SHA256).Hash -ne $manifest.databaseSha256) { throw 'Empreinte de la base invalide.' }
    if ((Get-FileHash (Join-Path $temp 'uploads.tar') -Algorithm SHA256).Hash -ne $manifest.uploadsSha256) { throw 'Empreinte des fichiers invalide.' }
    & (Join-Path $pgBin 'pg_restore.exe') --list (Join-Path $temp 'database.dump') > $null
    if ($LASTEXITCODE -ne 0) { throw 'Dump PostgreSQL illisible.' }
    $entries = @(& tar.exe -tf (Join-Path $temp 'uploads.tar'))
    if ($LASTEXITCODE -ne 0) { throw 'Archive des fichiers illisible.' }
    foreach ($entry in $entries) {
        if ($entry -notmatch '^uploads(/|$)' -or $entry -match '(^|/)\.\.(/|$)' -or $entry.Contains(':')) {
            throw 'Chemin dangereux dans l archive des fichiers.'
        }
    }
    $answer = Read-Host 'La restauration remplace les donnees actuelles. Tapez RESTAURER pour continuer'
    if ($answer -cne 'RESTAURER') { Write-Host 'Annule.'; exit 1 }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'backup-grh.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Sauvegarde de securite echouee. Restauration annulee.' }
    $secure = Read-Host 'Mot de passe du superutilisateur PostgreSQL postgres' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    $backend = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($backend) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($backend.OwningProcess)"
        if ($process.CommandLine -notmatch '(sirh-0\.0\.1-SNAPSHOT|backend)\.jar' -or $process.CommandLine -notmatch 'spring\.profiles\.active=prod') { throw 'Autre backend sur le port 8080.' }
        Stop-Process -Id $backend.OwningProcess -Force
        Start-Sleep -Seconds 2
    }
    & (Join-Path $pgBin 'pg_restore.exe') --clean --if-exists --single-transaction --exit-on-error --no-owner -h 127.0.0.1 -U postgres -d sirh_galaxam (Join-Path $temp 'database.dump')
    if ($LASTEXITCODE -ne 0) { throw 'Restauration PostgreSQL echouee. Backend laisse arrete.' }
    $grant = 'GRANT USAGE, CREATE ON SCHEMA public TO galaxam_prod_admin; GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO galaxam_prod_admin; GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO galaxam_prod_admin;'
    $grant | & (Join-Path $pgBin 'psql.exe') -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d sirh_galaxam
    if ($LASTEXITCODE -ne 0) { throw 'Droits PostgreSQL non retablis. Backend laisse arrete.' }
    $restored = Join-Path $temp 'restored'
    New-Item -ItemType Directory -Path $restored | Out-Null
    & tar.exe -xf (Join-Path $temp 'uploads.tar') -C $restored
    if ($LASTEXITCODE -ne 0) { throw 'Extraction des fichiers echouee. Backend laisse arrete.' }
    $uploads = Join-Path $root 'uploads'
    $previous = Join-Path $root ('.uploads-before-restore-' + [guid]::NewGuid().ToString('N'))
    if (Test-Path $uploads) { Move-Item -LiteralPath $uploads -Destination $previous }
    try { Move-Item -LiteralPath (Join-Path $restored 'uploads') -Destination $uploads }
    catch {
        if (Test-Path $previous) { Move-Item -LiteralPath $previous -Destination $uploads }
        throw
    }
    if (Test-Path $previous) { Remove-Item -LiteralPath $previous -Recurse -Force }
    $marker = Join-Path $root 'maintenance-required.txt'
    if (Test-Path $marker) { Remove-Item -LiteralPath $marker }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-app.ps1') -NoBrowser
    if ($LASTEXITCODE -ne 0) { throw 'Restauration faite mais backend non redemarre.' }
    Write-Host "Restauration GRH terminee : $source"
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
