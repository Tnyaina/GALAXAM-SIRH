$ErrorActionPreference = 'Stop'
try {
    foreach ($entry in @(
        @{ Port = 18089; Pattern = 'web-server\.mjs'; Name = 'interface web' },
        @{ Port = 8080; Pattern = '(sirh-0\.0\.1-SNAPSHOT|backend)\.jar'; Name = 'backend' }
    )) {
        $listener = Get-NetTCPConnection -LocalPort $entry.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $listener) { continue }
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
        if ($process.CommandLine -notmatch $entry.Pattern) {
            throw "Port $($entry.Port) utilise par un autre programme. Aucun arret effectue sur ce processus."
        }
        Stop-Process -Id $listener.OwningProcess -Force
        Write-Host "$($entry.Name) arrete."
    }
    Write-Host 'GRH est arrete. PostgreSQL reste actif en arriere-plan.'
} catch {
    Write-Host "Erreur : $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
