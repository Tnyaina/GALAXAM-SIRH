$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$StateDirectory = Join-Path $ProjectRoot '.maintenance'
$Marker = Join-Path $StateDirectory 'maintenance.json'
$ComposeFiles = @('--project-directory', $ProjectRoot, '-f', (Join-Path $ProjectRoot 'docker-compose.web.yml'))

# Les taches planifiees recuperent explicitement la configuration du compte Windows.
foreach ($name in @('SIRH_PROD_DB_PASSWORD', 'SIRH_PROD_JWT_SECRET', 'SIRH_ADMIN_NAME', 'SIRH_ADMIN_EMAIL', 'SIRH_ADMIN_PASSWORD', 'SIRH_PROD_WEB_PORT', 'SIRH_PROD_CORS_ORIGIN', 'SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if (-not [string]::IsNullOrEmpty($value)) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
}

function Invoke-Docker([string[]] $Arguments) {
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker a echoue (commande : $($Arguments[0])). Consultez le journal ci-dessus." }
}
function Invoke-Compose([string[]] $Arguments) {
    Invoke-Docker -Arguments (@('compose') + $ComposeFiles + $Arguments)
}
function Get-Config {
    $json = & docker compose @ComposeFiles config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Configuration de production invalide.' }
    return ($json -join "`n" | ConvertFrom-Json)
}
function Assert-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Installer Docker Desktop, puis ouvrir un nouveau PowerShell.' }
    & docker info --format '{{.OSType}}' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Demarrer Docker Desktop en mode conteneurs Linux.' }
    if ((& docker info --format '{{.OSType}}') -ne 'linux') { throw 'Docker doit utiliser les conteneurs Linux.' }
    if (-not (Get-Command tar -ErrorAction SilentlyContinue)) { throw 'tar.exe requis (inclus dans Windows 10/11 recent).' }
}
function Assert-Config {
    $c = Get-Config
    $identityFile = Join-Path $StateDirectory 'installation.json'
    if (Test-Path $identityFile) {
        $identity = Get-Content $identityFile -Raw | ConvertFrom-Json
        if ($identity.project -ne $c.name -or $identity.databaseVolume -ne $c.volumes.pg_data.name -or $identity.filesVolume -ne $c.volumes.business_files.name) { throw 'Identite de l installation modifiee : restaurer les noms de projet et volumes d origine.' }
    }
    $e = $c.services.backend.environment
    try { $jwtBytes = [Convert]::FromBase64String([string]$e.JWT_SECRET) } catch { $jwtBytes = [Text.Encoding]::UTF8.GetBytes([string]$e.JWT_SECRET) }
    if ($jwtBytes.Length -lt 64) { throw 'SIRH_PROD_JWT_SECRET doit contenir au moins 64 octets.' }
    if ([string]$e.DATABASE_PASSWORD -eq '' -or ([string]$e.DATABASE_PASSWORD).Length -lt 16) { throw 'Mot de passe PostgreSQL : au moins 16 caracteres.' }
    $port = [string]$c.services.frontend.ports[0].published
    if ($port -notmatch '^\d+$' -or [int]$port -lt 1024 -or [int]$port -gt 65535) { throw 'Port web : nombre entre 1024 et 65535.' }
    $origins = ([string]$e.CORS_ORIGIN).Split(',')
    foreach ($origin in $origins) {
        $uri = $null
        if (-not [Uri]::TryCreate($origin, [UriKind]::Absolute, [ref]$uri) -or $origin.EndsWith('/') -or $uri.AbsolutePath -ne '/' -or $uri.Query -or $uri.Fragment -or $uri.UserInfo -or $origin.Contains('*') -or $uri.Scheme -notin @('http','https')) { throw "Origine CORS invalide : $origin" }
        if ($uri.Scheme -eq 'http' -and $uri.Host -notin @('localhost','127.0.0.1')) { throw 'HTTP autorise uniquement sur localhost/127.0.0.1. Utiliser HTTPS pour le reseau.' }
    }
    if ($origins -notcontains "http://localhost:$port") { throw "Ajouter http://localhost:$port aux origines CORS pour les controles locaux." }
    if ([string]$e.BOOTSTRAP_ADMIN_ENABLED -eq 'true') {
        $p = [string]$e.GALAXAM_DEFAULT_ADMIN_PASSWORD
        if ($p.Length -lt 8 -or $p -cnotmatch '[A-Z]' -or $p -cnotmatch '[a-z]' -or $p -notmatch '[0-9]' -or -not $e.GALAXAM_DEFAULT_ADMIN_EMAIL) { throw 'Identite ou mot de passe du premier administrateur invalide.' }
    }
    return $c
}
function Get-Container([string] $Service) {
    $ids = @(& docker compose @ComposeFiles ps -a -q $Service)
    if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1 -or -not $ids[0]) { throw "Conteneur $Service introuvable ou ambigu." }
    return [string]$ids[0]
}
function Write-Json($Value, [string] $Path) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
}
function Enter-Operation {
    New-Item -ItemType Directory -Path $StateDirectory -Force | Out-Null
    try { $script:OperationLock = [IO.File]::Open((Join-Path $StateDirectory 'operation.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Une autre operation de maintenance est en cours.' }
    Start-Transcript -Path (Join-Path $StateDirectory ('operation-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')) -Append | Out-Null
}
function Exit-Operation {
    Stop-Transcript -ErrorAction SilentlyContinue | Out-Null
    if ($script:OperationLock) { $script:OperationLock.Dispose() }
}
function Assert-NoMaintenance {
    if (Test-Path $Marker) { throw "Maintenance inachevee : lire $Marker et le guide avant de reprendre." }
}
function Stop-Web([string] $Reason) {
    Write-Json @{ reason=$Reason; startedAt=(Get-Date).ToString('o') } $Marker
    Invoke-Compose -Arguments @('stop', 'frontend', 'backend')
}
function Wait-Web {
    $c = Get-Config
    $url = 'http://localhost:' + $c.services.frontend.ports[0].published
    for ($i=0; $i -lt 60; $i++) {
        try {
            $r = Invoke-RestMethod "$url/api/auth/ping" -TimeoutSec 4
            if ($r.status -eq 'UP') { Write-Host "Application disponible : $url"; return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw 'Application indisponible apres attente : consulter les journaux Docker.'
}
function Resume-Web {
    Invoke-Compose -Arguments @('up', '-d', '--no-deps', 'backend')
    # Nginx doit redemarrer pour resoudre la nouvelle adresse du backend.
    Invoke-Compose -Arguments @('up', '-d', '--no-deps', '--force-recreate', 'frontend')
    Wait-Web
    Remove-Item $Marker -Force -ErrorAction SilentlyContinue
}
function New-Backup([string] $Directory) {
    if (-not (Test-Path $Marker)) { throw 'Arreter le web avant la sauvegarde.' }
    if (-not (Test-Path $Directory -PathType Container)) { throw 'Dossier de sauvegarde indisponible. Verifier le support externe.' }
    $target = (Resolve-Path $Directory).Path
    $temp = Join-Path $StateDirectory ('backup-' + [guid]::NewGuid().ToString('N'))
    $helper = 'galaxam-files-' + [guid]::NewGuid().ToString('N')
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $archive = Join-Path $target "galaxam-$stamp.tar"
    $pg = Get-Container 'postgres'
    $backend = Get-Container 'backend'
    $pgImage = (& docker inspect --format '{{.Image}}' $pg).Trim()
    try {
        New-Item -ItemType Directory -Path $temp | Out-Null
        Invoke-Docker -Arguments @('exec', $pg, 'pg_dump', '-U', 'galaxam_admin', '-d', 'sirh_galaxam', '-Fc', '-f', '/tmp/galaxam-backup.dump')
        Invoke-Docker -Arguments @('exec', $pg, 'pg_restore', '--list', '/tmp/galaxam-backup.dump') | Out-Null
        Invoke-Docker -Arguments @('cp', "${pg}:/tmp/galaxam-backup.dump", (Join-Path $temp 'database.dump'))
        # Archive dans le conteneur : conserve fichiers caches et permissions, sans montage Windows.
        Invoke-Docker -Arguments @('create', '--name', $helper, '--volumes-from', "${backend}:ro", '--entrypoint', 'sh', $pgImage, '-c', 'tar -cf /tmp/uploads.tar -C /app/uploads .') | Out-Null
        Invoke-Docker -Arguments @('start', '-a', $helper)
        if ((& docker inspect --format '{{.State.ExitCode}}' $helper) -ne '0') { throw 'Archivage des documents en echec.' }
        Invoke-Docker -Arguments @('cp', "${helper}:/tmp/uploads.tar", (Join-Path $temp 'uploads.tar'))
        $c = Get-Config
        $commit = 'unknown'
        if (Get-Command git -ErrorAction SilentlyContinue) { $commit = & git -C $ProjectRoot rev-parse HEAD 2>$null }
        Write-Json @{ format='galaxam-web-local-v2'; createdAt=(Get-Date).ToString('o'); commit=$commit;
            backendImage=(& docker inspect --format '{{.Image}}' $backend).Trim();
            databaseSha256=(Get-FileHash (Join-Path $temp 'database.dump')).Hash;
            uploadsSha256=(Get-FileHash (Join-Path $temp 'uploads.tar')).Hash } (Join-Path $temp 'manifest.json')
        & tar -cf "$archive.partial" -C $temp database.dump uploads.tar manifest.json
        if ($LASTEXITCODE -ne 0) { throw 'Creation archive en echec.' }
        Move-Item "$archive.partial" $archive
        Write-Host "Sauvegarde terminee : $archive"
        return $archive
    } finally {
        & docker rm -f $helper *> $null
        & docker exec $pg rm -f /tmp/galaxam-backup.dump *> $null
        Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
