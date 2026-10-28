param([Parameter(Mandatory)][ValidateSet('dev', 'prod')][string]$Mode)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$db = if ($Mode -eq 'dev') { 'sirh_galaxam_dev' } else { 'sirh_galaxam' }
$role = if ($Mode -eq 'dev') { 'galaxam_dev_admin' } else { 'galaxam_prod_admin' }
$prefix = if ($Mode -eq 'dev') { 'SIRH_DEV_' } else { 'SIRH_PROD_' }
$rolePassword = [Environment]::GetEnvironmentVariable("${prefix}DB_PASSWORD", 'User')
if ([string]::IsNullOrWhiteSpace($rolePassword)) { throw "Variable utilisateur ${prefix}DB_PASSWORD absente." }
$pgBin = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin'
if (-not (Test-Path (Join-Path $pgBin 'psql.exe'))) {
    $psql = (Get-Command psql.exe -ErrorAction Stop).Source
    $pgBin = Split-Path $psql
}
$psql = Join-Path $pgBin 'psql.exe'
$dump = Join-Path $pgBin 'pg_dump.exe'
$ddl = 1..6 | ForEach-Object { Get-ChildItem (Join-Path $root 'backend\conception\bdd') -Filter ('{0:D2}_*.sql' -f $_) }
if ($ddl.Count -ne 6) { throw 'Les six DDL canoniques sont introuvables.' }
$answer = Read-Host "La base $db sera EFFACEE. Tapez exactement $db pour continuer"
if ($answer -cne $db) { Write-Host 'Annule.'; exit 1 }
$secure = Read-Host 'Mot de passe du superutilisateur PostgreSQL postgres' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
$env:PGHOST = '127.0.0.1'
$env:PGPORT = '5432'
try {
    $exists = & $psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname = '$db'"
    if ($LASTEXITCODE -ne 0) { throw 'Connexion PostgreSQL impossible.' }
    if ($exists -ne '1') { throw "La base $db n'existe pas. Creez-la d'abord dans pgAdmin." }
    if ($Mode -eq 'prod') {
        $backupDir = Join-Path $PSScriptRoot 'backups'
        New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
        $backup = Join-Path $backupDir ("$db-" + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump')
        & $dump -Fc -U postgres -d $db -f $backup
        if ($LASTEXITCODE -ne 0) { Remove-Item -LiteralPath $backup -ErrorAction SilentlyContinue; throw 'Sauvegarde PROD echouee. Aucune suppression effectuee.' }
        Write-Host "Sauvegarde PROD : $backup"
    }
    $listeners = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        $ownerPid = $listener.OwningProcess
        $proc = Get-CimInstance Win32_Process -Filter "ProcessId=$ownerPid"
        if ($proc.CommandLine -notmatch 'mg\.galaxam\.sirh|backend[\\/]target|sirh.*\.jar') {
            throw "Le port 8080 est utilise par un autre programme (PID $ownerPid). Arretez-le manuellement."
        }
        Stop-Process -Id $ownerPid -Force
        Write-Host "Backend GALAXAM arrete (PID $ownerPid)."
    }
    $escaped = $rolePassword.Replace("'", "''")
    $roleSql = "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$role') THEN CREATE ROLE $role LOGIN; END IF; END `$`$; ALTER ROLE $role PASSWORD '$escaped';"
    $roleSql | & $psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'Preparation du role PostgreSQL echouee.' }
    $resetSql = Join-Path $env:TEMP ("galaxam-reset-$Mode-$PID.sql")
    try {
        @("DROP SCHEMA IF EXISTS public CASCADE;", "CREATE SCHEMA public AUTHORIZATION $role;", 'CREATE EXTENSION IF NOT EXISTS btree_gist;', "SET ROLE $role;") | Set-Content -LiteralPath $resetSql -Encoding Ascii
        $args = @('-X', '-v', 'ON_ERROR_STOP=1', '--single-transaction', '-U', 'postgres', '-d', $db, '-f', $resetSql)
        foreach ($file in $ddl) { $args += @('-f', $file.FullName) }
        & $psql @args
        if ($LASTEXITCODE -ne 0) { throw 'Un DDL a echoue. La transaction a annule la remise a zero.' }
    } finally { Remove-Item -LiteralPath $resetSql -ErrorAction SilentlyContinue }
    $runner = Join-Path $PSScriptRoot 'run-backend.ps1'
    $child = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $runner + '"'), '-Mode', $Mode) -WindowStyle Hidden -PassThru
    Write-Host "Six DDL recharges dans $db. Backend $Mode demarre (PID $($child.Id))."
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try { $reply = Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/ping' -TimeoutSec 2; if ($reply.status -eq 'UP') { $ready = $true; break } } catch {}
        if ($child.HasExited) { break }
    }
    if (-not $ready) { throw "Backend pas encore pret. Voir $PSScriptRoot\logs\backend-$Mode.log" }
    Write-Host 'Backend pret : http://127.0.0.1:8080/api/auth/ping'
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
