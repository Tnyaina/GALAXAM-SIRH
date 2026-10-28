param([ValidateSet('dev', 'prod')][string]$Mode)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$prefix = if ($Mode -eq 'dev') { 'SIRH_DEV_' } else { 'SIRH_PROD_' }
foreach ($name in @('DB_PASSWORD', 'JWT_SECRET', 'WEB_PORT', 'CORS_ORIGIN', 'BOOTSTRAP_ADMIN_ENABLED')) {
    $value = [Environment]::GetEnvironmentVariable("$prefix$name", 'User')
    if ($null -ne $value) { [Environment]::SetEnvironmentVariable("$prefix$name", $value, 'Process') }
}
foreach ($name in @('NAME', 'EMAIL', 'PASSWORD')) {
    $value = [Environment]::GetEnvironmentVariable("SIRH_ADMIN_$name", 'User')
    if ($null -ne $value) { [Environment]::SetEnvironmentVariable("SIRH_ADMIN_$name", $value, 'Process') }
}
foreach ($name in @('SPRING_DATASOURCE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD', 'SIRH_DEV_DB_URL', 'SIRH_PROD_DB_URL')) {
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
$env:SPRING_PROFILES_ACTIVE = if ($Mode -eq 'dev') { 'local' } else { 'prod' }
$logDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir "backend-$Mode.log"
Set-Location $root
& mvn.cmd -f backend\pom.xml spring-boot:run *> $log
exit $LASTEXITCODE
