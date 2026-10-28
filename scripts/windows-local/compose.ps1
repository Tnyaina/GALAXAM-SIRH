param(
    [ValidateSet('prod', 'dev')][string] $Mode = 'prod',
    [ValidateSet('up', 'stop', 'ps', 'logs', 'config', 'build')][string] $Action = 'up'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if ($Mode -eq 'prod') {
    $file = Join-Path $root 'docker-compose.web.yml'
    $required = @('SIRH_PROD_DB_PASSWORD', 'SIRH_PROD_JWT_SECRET', 'SIRH_ADMIN_NAME',
        'SIRH_ADMIN_EMAIL', 'SIRH_ADMIN_PASSWORD', 'SIRH_PROD_WEB_PORT', 'SIRH_PROD_CORS_ORIGIN', 'SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED')
} else {
    $file = Join-Path $root 'docker-compose.dev.yml'
    $required = @('SIRH_DEV_DB_PASSWORD', 'SIRH_DEV_JWT_SECRET', 'SIRH_ADMIN_NAME',
        'SIRH_ADMIN_EMAIL', 'SIRH_ADMIN_PASSWORD', 'SIRH_DEV_WEB_PORT', 'SIRH_DEV_BOOTSTRAP_ADMIN_ENABLED')
}
foreach ($name in $required) {
    $value = [Environment]::GetEnvironmentVariable($name, 'User')
    if ([string]::IsNullOrEmpty($value)) { throw "Variable utilisateur Windows absente : $name" }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
$compose = @('compose', '--project-directory', $root, '-f', $file)
switch ($Action) {
    up { & docker @compose up -d --build --wait }
    stop { & docker @compose stop }
    ps { & docker @compose ps }
    logs { & docker @compose logs --tail 100 }
    config { & docker @compose config --quiet }
    build { & docker @compose build }
}
if ($LASTEXITCODE -ne 0) { throw "Commande Compose $Mode en echec : $Action" }
