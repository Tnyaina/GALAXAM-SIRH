param([string]$Output = (Join-Path $PSScriptRoot 'dist\GRH-package'))
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $repo 'backend\target\sirh-0.0.1-SNAPSHOT.jar'
$web = Join-Path $repo 'frontend\dist'
$icon = Join-Path $PSScriptRoot 'glx-logo.ico'
foreach ($required in @($jar, (Join-Path $web 'index.html'), $icon)) {
    if (-not (Test-Path $required)) { throw "Fichier manquant : $required. Executer prepare-app.ps1 d'abord." }
}
$outputPath = [IO.Path]::GetFullPath($Output)
if (Test-Path $outputPath) { throw "Paquet deja present : $outputPath. Choisir un autre dossier de sortie." }
New-Item -ItemType Directory -Path (Join-Path $outputPath 'app\web') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputPath 'scripts') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputPath 'schema\bdd') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $outputPath 'schema\migrations') -Force | Out-Null
Copy-Item -LiteralPath $jar -Destination (Join-Path $outputPath 'app\backend.jar')
Copy-Item -Path (Join-Path $web '*') -Destination (Join-Path $outputPath 'app\web') -Recurse
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'write-schema-manifest.ps1') -Destination (Join-Path $outputPath 'app\schema-manifest.json')
if ($LASTEXITCODE -ne 0) { throw 'Manifest SQL non cree.' }
$scripts = @('GALAXAM.bat', 'Arreter-GRH.bat', 'start-app.ps1', 'stop-app.ps1', 'web-server.mjs', 'backup-grh.ps1', 'restore-grh.ps1', 'verify-backup-grh.ps1', 'schedule-backup-grh.ps1', 'migrate-grh.ps1', 'initialize-prod.ps1', 'glx-logo.ico')
foreach ($name in $scripts) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $outputPath 'scripts') }
Copy-Item -Path (Join-Path $repo 'backend\conception\bdd\*.sql') -Destination (Join-Path $outputPath 'schema\bdd')
Copy-Item -Path (Join-Path $repo 'docker\migrations\*.sql') -Destination (Join-Path $outputPath 'schema\migrations')
Copy-Item -LiteralPath (Join-Path $repo 'docker\canonical.sha256') -Destination (Join-Path $outputPath 'schema\canonical.sha256')
Copy-Item -LiteralPath (Join-Path $repo 'docker\init-local.sql') -Destination (Join-Path $outputPath 'schema\init-local.sql')
foreach ($name in @('Installer-GRH.bat', 'install-grh.ps1', 'PAQUET-GRH.md')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $outputPath }
$zip = "$outputPath.zip"
if (Test-Path $zip) { throw "Archive deja presente : $zip" }
Compress-Archive -Path (Join-Path $outputPath '*') -DestinationPath $zip -CompressionLevel Optimal
Write-Host "Paquet GRH cree : $outputPath"
Write-Host "Archive a transmettre : $zip"
