param([string]$Database = 'sirh_galaxam', [switch]$CreateIfMissing)
$ErrorActionPreference = 'Stop'
if ($Database -notmatch '^[a-z][a-z0-9_]*$') { throw 'Nom de base invalide.' }
$packageRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$packaged = Test-Path (Join-Path $packageRoot 'schema\bdd')
$root = if ($packaged) { $packageRoot } else { (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$ddlDirectory = if ($packaged) { Join-Path $root 'schema\bdd' } else { Join-Path $root 'backend\conception\bdd' }
$psql = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\psql.exe'
$rolePassword = [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($rolePassword)) { throw 'SIRH_PROD_DB_PASSWORD absent.' }
$superPassword = [Environment]::GetEnvironmentVariable('SIRH_PG_SUPERUSER_PASSWORD', 'Process')
if ([string]::IsNullOrWhiteSpace($superPassword)) {
    $secure = Read-Host 'Mot de passe PostgreSQL postgres' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $superPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$env:PGPASSWORD = $superPassword
try {
    $exists = & $psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d postgres -Atc "SELECT count(*) FROM pg_database WHERE datname='$Database'"
    if ($LASTEXITCODE -ne 0) { throw 'Connexion PostgreSQL superutilisateur impossible.' }
    if ($exists -eq '0') {
        if (-not $CreateIfMissing) { throw "Base absente : $Database" }
        & 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\createdb.exe' -h 127.0.0.1 -U postgres $Database
        if ($LASTEXITCODE -ne 0) { throw 'Creation de la base PROD echouee.' }
    }
    $tables = & $psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d $Database -Atc "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"
    if ($LASTEXITCODE -ne 0) { throw 'Connexion a la base PROD impossible.' }
    if ($tables -ne '0') { throw 'La base PROD contient deja des tables. Initialisation annulee sans changement.' }
    $escaped = $rolePassword.Replace("'", "''")
    "DO `$`$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'galaxam_prod_admin') THEN CREATE ROLE galaxam_prod_admin LOGIN; END IF; END `$`$; ALTER ROLE galaxam_prod_admin PASSWORD '$escaped';" | & $psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'Creation du role PROD echouee.' }
    $sql = Join-Path $env:TEMP "galaxam-init-prod-$PID.sql"
    try {
        @('ALTER SCHEMA public OWNER TO galaxam_prod_admin;', 'CREATE EXTENSION IF NOT EXISTS btree_gist;', 'SET ROLE galaxam_prod_admin;') | Set-Content -LiteralPath $sql -Encoding Ascii
        $files = @(1..6 | ForEach-Object { Get-ChildItem $ddlDirectory -Filter ('{0:D2}_*.sql' -f $_) })
        if ($files.Count -ne 6) { throw 'Les six DDL sont introuvables.' }
        $args = @('-X', '-q', '-v', 'ON_ERROR_STOP=1', '--single-transaction', '-h', '127.0.0.1', '-U', 'postgres', '-d', $Database, '-f', $sql)
        foreach ($file in $files) { $args += @('-f', $file.FullName) }
        & $psql @args
        if ($LASTEXITCODE -ne 0) { throw 'Initialisation DDL echouee ; transaction annulee.' }
    } finally { Remove-Item -LiteralPath $sql -ErrorAction SilentlyContinue }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'migrate-grh.ps1') -Database $Database
    if ($LASTEXITCODE -ne 0) { throw 'Six DDL charges, mais migrations initiales echouees.' }
    Write-Host "Base $Database initialisee avec six DDL et migrations versionnees."
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
