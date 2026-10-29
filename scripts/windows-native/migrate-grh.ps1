param(
    [string]$Database = 'sirh_galaxam',
    [string]$MigrationsDirectory,
    [string]$Username = 'galaxam_prod_admin'
)
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$packaged = Test-Path (Join-Path $packageRoot 'schema\canonical.sha256')
$root = if ($packaged) { $packageRoot } else { (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
if (-not $MigrationsDirectory) { $MigrationsDirectory = if ($packaged) { Join-Path $root 'schema\migrations' } else { Join-Path $root 'docker\migrations' } }
$directory = (Resolve-Path -LiteralPath $MigrationsDirectory).Path
if ($Database -notmatch '^[a-z][a-z0-9_]*$') { throw 'Nom de base invalide.' }
if ($Username -ne 'galaxam_prod_admin') { throw 'Role PostgreSQL non autorise pour ce parcours.' }
$password = [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($password)) { throw 'SIRH_PROD_DB_PASSWORD absent.' }
$psql = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\psql.exe'
if (-not (Test-Path $psql)) { throw 'psql PostgreSQL 15 introuvable.' }

# Les six DDL canoniques restent figures ; une evolution passe par migrations/.
$canonicalFile = if ($packaged) { Join-Path $root 'schema\canonical.sha256' } else { Join-Path $root 'docker\canonical.sha256' }
$canonical = Get-Content $canonicalFile
foreach ($line in $canonical) {
    if ($line -notmatch '^([0-9a-f]{64})\s+/(docker/init-local\.sql|bdd-schemas/[0-9]{2}_[A-Za-z0-9_]+\.sql)$') { throw "Entree canonique invalide : $line" }
    $expected = $Matches[1]
    $relative = $Matches[2]
    $file = if ($relative -eq 'docker/init-local.sql') {
        if ($packaged) { Join-Path $root 'schema\init-local.sql' } else { Join-Path $root 'docker\init-local.sql' }
    } else {
        if ($packaged) { Join-Path $root ('schema\bdd\' + [IO.Path]::GetFileName($relative)) } else { Join-Path $root ('backend\conception\bdd\' + [IO.Path]::GetFileName($relative)) }
    }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ine $expected) { throw "DDL canonique modifie : $relative" }
}

$files = @(Get-ChildItem $directory -Filter '*.sql' -File | Sort-Object Name)
$versions = @{}
foreach ($file in $files) {
    if ($file.Name -cnotmatch '^([0-9]{4})_[A-Za-z0-9_-]+\.sql$') { throw "Nom de migration invalide : $($file.Name)" }
    $version = $Matches[1]
    if ($versions.ContainsKey($version)) { throw "Numero de migration en double : $version" }
    $versions[$version] = $true
}
for ($i = 0; $i -lt $files.Count; $i++) {
    if ($files[$i].Name.Substring(0, 4) -ne ('{0:D4}' -f ($i + 1))) { throw 'Migrations non consecutives depuis 0001.' }
}

$sql = New-Object System.Collections.Generic.List[string]
$sql.Add('BEGIN;')
$sql.Add('SELECT pg_advisory_xact_lock(19760427);')
$sql.Add('CREATE TABLE IF NOT EXISTS public.galaxam_schema_history (version text PRIMARY KEY, checksum text NOT NULL, applied_at timestamptz NOT NULL DEFAULT now());')
$sql.Add('CREATE TEMP TABLE supplied_migrations (version text PRIMARY KEY) ON COMMIT DROP;')
$sql.Add('CREATE UNIQUE INDEX ON supplied_migrations ((left(version,4)));')
foreach ($file in $files) { $sql.Add("INSERT INTO supplied_migrations VALUES ('$($file.Name)');") }
$sql.Add('DO $$ BEGIN IF EXISTS (SELECT 1 FROM public.galaxam_schema_history h LEFT JOIN supplied_migrations s USING(version) WHERE s.version IS NULL) THEN RAISE EXCEPTION ''Migration deja appliquee absente de la livraison''; END IF; END $$;')
foreach ($file in $files) {
    $name = $file.Name
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $path = $file.FullName.Replace('\', '/')
    $sql.Add("DO `$`$ BEGIN IF EXISTS (SELECT 1 FROM public.galaxam_schema_history WHERE version='$name' AND checksum <> '$hash') THEN RAISE EXCEPTION 'Migration modifiee : $name'; END IF; IF NOT EXISTS (SELECT 1 FROM public.galaxam_schema_history WHERE version='$name') AND EXISTS (SELECT 1 FROM public.galaxam_schema_history WHERE version > '$name') THEN RAISE EXCEPTION 'Migration hors ordre : $name'; END IF; END `$`$;")
    $sql.Add("SELECT NOT EXISTS (SELECT 1 FROM public.galaxam_schema_history WHERE version='$name') AS apply_migration \gset")
    $sql.Add('\if :apply_migration')
    $sql.Add("\i '$path'")
    $sql.Add("INSERT INTO public.galaxam_schema_history(version, checksum) VALUES ('$name', '$hash');")
    $sql.Add('\endif')
}
$sql.Add('COMMIT;')
$temp = Join-Path $env:TEMP ("galaxam-migrate-$PID-" + [guid]::NewGuid().ToString('N') + '.sql')
$sql | Set-Content -LiteralPath $temp -Encoding Ascii
$env:PGPASSWORD = $password
$previousClientEncoding = $env:PGCLIENTENCODING
$env:PGCLIENTENCODING = 'UTF8'
try {
    & $psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U $Username -d $Database -f $temp
    if ($LASTEXITCODE -ne 0) { throw "Migrations annulees pour $Database. La transaction a ete annulee." }
    Write-Host "Migrations verifiees et appliquees pour $Database."
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if ($null -eq $previousClientEncoding) { Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue }
    else { $env:PGCLIENTENCODING = $previousClientEncoding }
    Remove-Item -LiteralPath $temp -ErrorAction SilentlyContinue
}
