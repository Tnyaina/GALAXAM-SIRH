. (Join-Path $PSScriptRoot 'common.ps1')
Enter-Operation
try {
    Assert-NoMaintenance
    $available = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        try { Assert-Docker; $available=$true; break } catch { Start-Sleep -Seconds 10 }
    }
    if (-not $available) { throw 'Docker Desktop indisponible apres 5 minutes.' }
    Assert-Config | Out-Null
    # Demarrage quotidien des conteneurs installes, sans migration ni compilation.
    Invoke-Compose -Arguments @('start', 'postgres')
    $pg = Get-Container 'postgres'
    $pgReady = $false
    for ($i=0; $i -lt 60; $i++) {
        if ((& docker inspect --format '{{.State.Health.Status}}' $pg) -eq 'healthy') { $pgReady=$true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $pgReady) { throw 'PostgreSQL ne devient pas sain apres le demarrage.' }
    Invoke-Compose -Arguments @('start', 'backend', 'frontend')
    Wait-Web
} finally { Exit-Operation }
