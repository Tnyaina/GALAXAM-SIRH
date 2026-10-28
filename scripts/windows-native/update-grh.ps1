param([string]$Installation = 'D:\GALAXAM-GRH', [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$installed = (Resolve-Path -LiteralPath $Installation).Path
$app = Join-Path $installed 'app'
$scripts = Join-Path $installed 'scripts'
$manifestPath = Join-Path $app 'schema-manifest.json'
foreach ($required in @((Join-Path $app 'backend.jar'), (Join-Path $app 'web\index.html'), (Join-Path $scripts 'backup-grh.ps1'), $manifestPath)) {
    if (-not (Test-Path $required)) { throw "Installation incomplete : $required" }
}
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.format -ne 'galaxam-schema-files-v1' -or @($manifest.files).Count -lt 8) { throw 'Manifest SQL invalide.' }
$expectedPaths = @($manifest.files | ForEach-Object { $_.path })
$actualFiles = @(1..6 | ForEach-Object { Get-ChildItem (Join-Path $repo 'backend\conception\bdd') -Filter ('{0:D2}_*.sql' -f $_) })
$migrationFiles = @(Get-ChildItem (Join-Path $repo 'docker\migrations') -Filter '*.sql' | Sort-Object Name)
$actualFiles += $migrationFiles
$actualPaths = @($actualFiles | ForEach-Object { $_.FullName.Substring($repo.Length + 1).Replace('\', '/') })
foreach ($file in $manifest.files) {
    if ($actualPaths -notcontains $file.path) { throw "Fichier SQL livre precedemment manquant : $($file.path)" }
    $candidate = Join-Path $repo ($file.path.Replace('/', '\'))
    if ((Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash -ne $file.sha256) { throw "Fichier SQL livre precedemment modifie : $($file.path)." }
}
$newPaths = @($actualPaths | Where-Object { $expectedPaths -notcontains $_ })
if (@($newPaths | Where-Object { $_ -notlike 'docker/migrations/*' }).Count -gt 0) { throw 'Un DDL canonique a ete ajoute. Utiliser une migration versionnee.' }
Write-Host "Precontrole SQL : $($newPaths.Count) nouvelle(s) migration(s)."
if ($CheckOnly) { Write-Host 'Verification terminee. Aucun fichier ni donnee modifie.'; exit 0 }

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'prepare-app.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Compilation echouee. Installation intacte.' }
$jar = Join-Path $repo 'backend\target\sirh-0.0.1-SNAPSHOT.jar'
$web = Join-Path $repo 'frontend\dist'
if (-not (Test-Path $jar) -or -not (Test-Path (Join-Path $web 'index.html'))) { throw 'Artefacts compiles manquants.' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$stage = Join-Path $installed "updates\$stamp"
if (-not [IO.Path]::GetFullPath($stage).StartsWith(([IO.Path]::GetFullPath($installed).TrimEnd('\') + '\'), [StringComparison]::OrdinalIgnoreCase)) { throw 'Chemin de mise a jour invalide.' }
New-Item -ItemType Directory -Path (Join-Path $stage 'new-app\web') -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'new-app\backend.jar')
Copy-Item -Path (Join-Path $web '*') -Destination (Join-Path $stage 'new-app\web') -Recurse
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'write-schema-manifest.ps1') -Destination (Join-Path $stage 'new-app\schema-manifest.json')
if ($LASTEXITCODE -ne 0) { throw 'Nouveau manifest SQL non cree.' }
$wasRunning = $false
try { $wasRunning = (Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/ping' -TimeoutSec 2).status -eq 'UP' } catch {}
$backupDir = 'D:\GALAXAM-Sauvegardes\GRH-native'
$previousBackup = Get-ChildItem $backupDir -Filter 'grh-*.tar' -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'backup-grh.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Sauvegarde echouee. Installation intacte.' }
$newBackup = Get-ChildItem $backupDir -Filter 'grh-*.tar' -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $newBackup -or ($previousBackup -and $newBackup.FullName -eq $previousBackup.FullName)) { throw 'Nouvelle archive introuvable. Installation intacte.' }
Write-Host "Sauvegarde avant mise a jour : $($newBackup.FullName)"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'stop-app.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Arret GRH echoue. Installation intacte.' }
try {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'migrate-grh.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Migration SQL echouee.' }
} catch {
    if ($wasRunning) { & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'start-app.ps1') -NoBrowser | Out-Null }
    throw "Mise a jour annulee avant remplacement du code. $($_.Exception.Message) Sauvegarde : $($newBackup.FullName)"
}
$previousApp = Join-Path $stage 'previous-app'
$failedApp = Join-Path $stage 'failed-app'
$swapped = $false
try {
    Move-Item -LiteralPath $app -Destination $previousApp
    try { Move-Item -LiteralPath (Join-Path $stage 'new-app') -Destination $app; $swapped = $true }
    catch { Move-Item -LiteralPath $previousApp -Destination $app; throw }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'start-app.ps1') -NoBrowser
    if ($LASTEXITCODE -ne 0) { throw 'Nouvelle version indisponible apres demarrage.' }
    Write-Host "Mise a jour reussie. Ancienne version conservee dans $previousApp"
} catch {
    $reason = $_.Exception.Message
    if ($swapped) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'stop-app.ps1') | Out-Null
        Move-Item -LiteralPath $app -Destination $failedApp
        Move-Item -LiteralPath $previousApp -Destination $app
    }
    if ($newPaths.Count -eq 0) {
        if ($wasRunning) { & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $scripts 'start-app.ps1') -NoBrowser | Out-Null }
        throw "Mise a jour annulee, anciens fichiers retablis. Motif : $reason. Sauvegarde : $($newBackup.FullName)"
    }
    $marker = Join-Path $installed 'maintenance-required.txt'
    "Restaurer la base depuis $($newBackup.FullName) avant de redemarrer GRH. Motif : $reason" | Set-Content -LiteralPath $marker -Encoding UTF8
    throw "Nouveau code indisponible apres migration SQL. Anciens fichiers retablis mais GRH laisse arrete. Restaurer la base depuis $($newBackup.FullName) avant de redemarrer. Motif : $reason"
}
