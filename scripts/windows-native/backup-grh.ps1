param([string]$Destination = 'D:\GALAXAM-Sauvegardes\GRH-native')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not (Test-Path (Join-Path $root 'app\backend.jar'))) { $root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$pgBin = 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin'
$password = [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($password)) { throw 'SIRH_PROD_DB_PASSWORD absent.' }
$mutex = New-Object Threading.Mutex($false, 'Local\GALAXAM_GRH_Backup')
if (-not $mutex.WaitOne(0)) { throw 'Une sauvegarde GRH est deja en cours.' }
$backendStopped = $false
$temp = $null
try {
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    $backend = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($backend) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($backend.OwningProcess)"
        if ($process.CommandLine -notmatch '(sirh-0\.0\.1-SNAPSHOT|backend)\.jar' -or $process.CommandLine -notmatch 'spring\.profiles\.active=prod') {
            throw 'Un autre backend utilise le port 8080. Sauvegarde annulee.'
        }
        Stop-Process -Id $backend.OwningProcess -Force
        $backendStopped = $true
        Start-Sleep -Seconds 2
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $temp = Join-Path $Destination (".grh-$stamp-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $temp | Out-Null
    $env:PGPASSWORD = $password
    $env:PGHOST = '127.0.0.1'
    $env:PGPORT = '5432'
    & (Join-Path $pgBin 'pg_dump.exe') -U galaxam_prod_admin -d sirh_galaxam -Fc -f (Join-Path $temp 'database.dump')
    if ($LASTEXITCODE -ne 0) { throw 'Export PostgreSQL echoue.' }
    & (Join-Path $pgBin 'pg_restore.exe') --list (Join-Path $temp 'database.dump') > $null
    if ($LASTEXITCODE -ne 0) { throw 'Catalogue PostgreSQL illisible.' }
    $uploads = Join-Path $root 'uploads'
    if (Test-Path $uploads -PathType Container) {
        & tar.exe -cf (Join-Path $temp 'uploads.tar') -C $root uploads
    } else {
        $empty = Join-Path $temp 'empty\uploads'
        New-Item -ItemType Directory -Force -Path $empty | Out-Null
        & tar.exe -cf (Join-Path $temp 'uploads.tar') -C (Join-Path $temp 'empty') uploads
    }
    if ($LASTEXITCODE -ne 0) { throw 'Archivage des fichiers echoue.' }
    $manifest = [ordered]@{
        format = 'galaxam-grh-native-v1'
        createdAt = (Get-Date).ToString('o')
        database = 'sirh_galaxam'
        databaseSha256 = (Get-FileHash (Join-Path $temp 'database.dump') -Algorithm SHA256).Hash
        uploadsSha256 = (Get-FileHash (Join-Path $temp 'uploads.tar') -Algorithm SHA256).Hash
    }
    $manifest | ConvertTo-Json | Set-Content (Join-Path $temp 'manifest.json') -Encoding UTF8
    $partial = Join-Path $Destination "grh-$stamp.tar.partial"
    $archive = Join-Path $Destination "grh-$stamp.tar"
    & tar.exe -cf $partial -C $temp database.dump uploads.tar manifest.json
    if ($LASTEXITCODE -ne 0) { throw 'Creation de l archive echouee.' }
    Move-Item -LiteralPath $partial -Destination $archive
    Write-Host "Sauvegarde GRH : $archive"
    Get-ChildItem $Destination -Filter 'grh-*.tar' -File | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item -Force
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if ($temp -and (Test-Path $temp)) { Remove-Item -LiteralPath $temp -Recurse -Force }
    try {
        if ($backendStopped) {
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-app.ps1') -NoBrowser
            if ($LASTEXITCODE -ne 0) { throw 'Sauvegarde creee mais backend non redemarre. Relancez GRH.' }
        }
    }
    finally { $mutex.ReleaseMutex(); $mutex.Dispose() }
}
