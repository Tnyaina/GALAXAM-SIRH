param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$packaged = Test-Path (Join-Path $root 'app\backend.jar')
if (-not $packaged) { $root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$maintenanceMarker = Join-Path $root 'maintenance-required.txt'
if (Test-Path $maintenanceMarker) { throw "Restauration requise avant demarrage. Lire $maintenanceMarker" }
$logDir = Join-Path $PSScriptRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$launchMutex = New-Object Threading.Mutex($false, 'Local\GALAXAM_GRH_Start')
if (-not $launchMutex.WaitOne([TimeSpan]::FromMinutes(3))) { throw 'Un autre lancement GRH est en cours.' }

try {
    $service = Get-Service 'galaxam-postgresql-15' -ErrorAction Stop
    if ($service.Status -ne 'Running') {
        Start-Service $service.Name
        $service.WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
    }
    $dbPassword = [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')
    if ([string]::IsNullOrWhiteSpace($dbPassword)) { throw 'Configuration PROD absente : SIRH_PROD_DB_PASSWORD.' }
    $jwt = [Environment]::GetEnvironmentVariable('SIRH_PROD_JWT_SECRET', 'User')
    if ([string]::IsNullOrWhiteSpace($jwt)) { throw 'Configuration PROD absente : SIRH_PROD_JWT_SECRET.' }
    $jar = if ($packaged) { Join-Path $root 'app\backend.jar' } else { Join-Path $root 'backend\target\sirh-0.0.1-SNAPSHOT.jar' }
    $web = if ($packaged) { Join-Path $root 'app\web\index.html' } else { Join-Path $root 'frontend\dist\index.html' }
    if (-not (Test-Path $jar)) { throw "Backend non compile : $jar. Executer une fois la preparation documentee." }
    if (-not (Test-Path $web)) { throw "Frontend non compile : $web. Executer une fois la preparation documentee." }
    $port = '18089'
    $url = "http://127.0.0.1:$port/"
    $apiReady = $false
    try { $apiReady = (Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/ping' -TimeoutSec 2).status -eq 'UP' } catch {}
    $backendProcess = $null
    if ($apiReady) {
        $apiListener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        $apiProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($apiListener.OwningProcess)"
        if ($apiProcess.CommandLine -notmatch '(sirh-0\.0\.1-SNAPSHOT|backend)\.jar' -or $apiProcess.CommandLine -notmatch 'spring\.profiles\.active=prod') {
            throw 'Un autre backend utilise deja le port 8080. Arretez-le avant de lancer GALAXAM PROD.'
        }
    } else {
        $listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
        if ($listener) { throw 'Le port 8080 est occupe par une autre application ou un backend non pret.' }
        $env:SPRING_PROFILES_ACTIVE = 'prod'
        $env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/sirh_galaxam'
        $env:DATABASE_USERNAME = 'galaxam_prod_admin'
        $env:SIRH_PROD_DB_PASSWORD = $dbPassword
        $env:SIRH_PROD_JWT_SECRET = $jwt
        $env:SIRH_PROD_CORS_ORIGIN = "http://127.0.0.1:$port"
        foreach ($name in @('BOOTSTRAP_ADMIN_ENABLED', 'WEB_PORT')) {
            $value = [Environment]::GetEnvironmentVariable("SIRH_PROD_$name", 'User')
            if ($null -ne $value) { [Environment]::SetEnvironmentVariable("SIRH_PROD_$name", $value, 'Process') }
        }
        foreach ($name in @('NAME', 'EMAIL', 'PASSWORD')) {
            $value = [Environment]::GetEnvironmentVariable("SIRH_ADMIN_$name", 'User')
            if ($null -ne $value) { [Environment]::SetEnvironmentVariable("SIRH_ADMIN_$name", $value, 'Process') }
        }
        $java = 'C:\Program Files\GALAXAM\Toolchain\jdk-17.0.20.1+1\bin\java.exe'
        if (-not (Test-Path $java)) { $java = (Get-Command java.exe -ErrorAction Stop).Source }
        $backendProcess = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"'), '--spring.profiles.active=prod', '--galaxam.native.nio2=true') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'backend-app.log') -RedirectStandardError (Join-Path $logDir 'backend-app-error.log') -PassThru
    }
    $env:GALAXAM_WEB_PORT = $port
    $webReady = $false
    try { $webReady = (Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch {}
    if ($webReady) {
        $page = (Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2).Content
        if ($page -notmatch 'GALAXAM|/assets/') { throw "Le port web $port est utilise par une autre application." }
    } else {
        if (Get-NetTCPConnection -LocalPort ([int]$port) -State Listen -ErrorAction SilentlyContinue) { throw "Le port web $port est deja utilise." }
        $node = 'C:\Program Files\GALAXAM\Toolchain\node-v22.23.0-win-x64\node.exe'
        if (-not (Test-Path $node)) { $node = (Get-Command node.exe -ErrorAction Stop).Source }
        $webProcess = Start-Process -FilePath $node -ArgumentList ('"' + (Join-Path $PSScriptRoot 'web-server.mjs') + '"') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'web-app.log') -RedirectStandardError (Join-Path $logDir 'web-app-error.log') -PassThru
    }
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $api = Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/ping' -TimeoutSec 2
            $page = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2
            if ($api.status -eq 'UP' -and $page.StatusCode -eq 200) { if (-not $NoBrowser) { Start-Process $url }; Write-Host "GALAXAM pret : $url"; exit 0 }
        } catch {}
        if ($backendProcess -and $backendProcess.HasExited) { throw "Le backend s'est arrete. Consultez $logDir\backend-app.log" }
        if ($webProcess -and $webProcess.HasExited) { throw "Le serveur web s'est arrete. Consultez $logDir\web-app-error.log" }
        Start-Sleep -Seconds 2
    }
    throw "GALAXAM n'a pas demarre. Consultez les journaux dans $logDir"
} catch {
    Write-Host "Erreur : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    $launchMutex.ReleaseMutex()
    $launchMutex.Dispose()
}
