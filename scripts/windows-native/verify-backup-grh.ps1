param([Parameter(Mandatory)][string]$Archive)
$ErrorActionPreference = 'Stop'
$pgBin = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin'
$source = (Resolve-Path -LiteralPath $Archive).Path
$temp = Join-Path $env:TEMP ("galaxam-verify-" + [guid]::NewGuid().ToString('N'))
$db = 'galaxam_verify_' + [guid]::NewGuid().ToString('N').Substring(0, 12)
New-Item -ItemType Directory -Path $temp | Out-Null
$created = $false
try {
    & tar.exe -xf $source -C $temp database.dump uploads.tar manifest.json
    if ($LASTEXITCODE -ne 0) { throw 'Archive illisible.' }
    $manifest = Get-Content (Join-Path $temp 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.format -ne 'galaxam-grh-native-v1') { throw 'Format inconnu.' }
    if ((Get-FileHash (Join-Path $temp 'database.dump') -Algorithm SHA256).Hash -ne $manifest.databaseSha256) { throw 'Dump modifie.' }
    if ((Get-FileHash (Join-Path $temp 'uploads.tar') -Algorithm SHA256).Hash -ne $manifest.uploadsSha256) { throw 'Fichiers modifies.' }
    & tar.exe -tf (Join-Path $temp 'uploads.tar') > $null
    if ($LASTEXITCODE -ne 0) { throw 'Fichiers illisibles.' }
    $secure = Read-Host 'Mot de passe PostgreSQL postgres pour verification isolee' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    & (Join-Path $pgBin 'createdb.exe') -h 127.0.0.1 -U postgres $db
    if ($LASTEXITCODE -ne 0) { throw 'Creation de la base de verification impossible.' }
    $created = $true
    & (Join-Path $pgBin 'pg_restore.exe') --single-transaction --exit-on-error --no-owner -h 127.0.0.1 -U postgres -d $db (Join-Path $temp 'database.dump')
    if ($LASTEXITCODE -ne 0) { throw 'Restauration de test echouee.' }
    $count = & (Join-Path $pgBin 'psql.exe') -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d $db -Atc "SELECT count(*) FROM pg_tables WHERE schemaname='public'"
    if ($LASTEXITCODE -ne 0 -or [int]$count -eq 0) { throw 'Base restauree sans tables.' }
    Write-Host "Sauvegarde valide : $count tables restaurees dans une base temporaire."
} finally {
    if ($created) { & (Join-Path $pgBin 'dropdb.exe') -h 127.0.0.1 -U postgres --if-exists $db }
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $temp -Recurse -Force -ErrorAction SilentlyContinue
}
