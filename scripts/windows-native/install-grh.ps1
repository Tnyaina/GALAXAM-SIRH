param([string]$Destination = 'D:\GALAXAM-GRH', [string]$ExistingUploads = 'D:\GALAXAM-SIRH\uploads')
$ErrorActionPreference = 'Stop'
$source = $PSScriptRoot
$target = [IO.Path]::GetFullPath($Destination)
if ($target -ieq [IO.Path]::GetFullPath($source)) { throw 'Source et destination identiques.' }
if (Test-Path $target) { throw "Installation deja presente : $target. Aucune donnee ecrasee." }
foreach ($file in @('app\backend.jar', 'app\web\index.html', 'scripts\GALAXAM.bat', 'scripts\Arreter-GRH.bat', 'scripts\glx-logo.ico', 'scripts\initialize-prod.ps1', 'scripts\migrate-grh.ps1', 'schema\canonical.sha256', 'schema\bdd\01_schema_core_securite.sql')) {
    if (-not (Test-Path (Join-Path $source $file))) { throw "Paquet incomplet : $file" }
}
foreach ($file in @('C:\Program Files\GALAXAM\Toolchain\jdk-17.0.20.1+1\bin\java.exe', 'C:\Program Files\GALAXAM\Toolchain\node-v22.23.0-win-x64\node.exe', 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\pg_dump.exe')) {
    if (-not (Test-Path $file)) { throw "Outil global absent : $file" }
}
if (-not (Get-Service 'galaxam-postgresql-15' -ErrorAction SilentlyContinue)) { throw 'Service PostgreSQL GALAXAM absent.' }
$password = [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')
if ([string]::IsNullOrWhiteSpace($password)) { throw 'Configuration PROD absente.' }
$env:PGPASSWORD = $password
$tableCount = $null
$databaseReady = $false
try {
    try {
        $tableCount = & 'C:\Program Files\GALAXAM\Toolchain\postgresql-15.19\bin\psql.exe' -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U galaxam_prod_admin -d sirh_galaxam -Atc "SELECT count(*) FROM pg_tables WHERE schemaname='public'" 2>$null
        $databaseReady = ($LASTEXITCODE -eq 0 -and [int]$tableCount -gt 0)
    } catch { $databaseReady = $false }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
if (-not $databaseReady) {
    foreach ($name in @('SIRH_ADMIN_NAME', 'SIRH_ADMIN_EMAIL', 'SIRH_ADMIN_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'User'))) { throw "Variable requise pour le premier compte : $name" }
    }
    if ([Environment]::GetEnvironmentVariable('SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED', 'User') -ne 'true') { throw 'Activer SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED=true pour le premier compte administrateur.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $source 'scripts\initialize-prod.ps1') -CreateIfMissing
    if ($LASTEXITCODE -ne 0) { throw 'Initialisation de la base PROD echouee.' }
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $source 'scripts\stop-app.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Impossible d arreter l ancien lancement GRH.' }
New-Item -ItemType Directory -Path $target | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'app') -Destination $target -Recurse
Copy-Item -LiteralPath (Join-Path $source 'scripts') -Destination $target -Recurse
Copy-Item -LiteralPath (Join-Path $source 'schema') -Destination $target -Recurse
if (Test-Path $ExistingUploads -PathType Container) { Copy-Item -LiteralPath $ExistingUploads -Destination $target -Recurse }
else { New-Item -ItemType Directory -Path (Join-Path $target 'uploads') | Out-Null }
$desktop = [Environment]::GetFolderPath('Desktop')
$shell = New-Object -ComObject WScript.Shell
$launch = $shell.CreateShortcut((Join-Path $desktop 'GRH.lnk'))
$launch.TargetPath = Join-Path $target 'scripts\GALAXAM.bat'
$launch.WorkingDirectory = $target
$launch.IconLocation = (Join-Path $target 'scripts\glx-logo.ico') + ',0'
$launch.Description = 'Ouvrir GALAXAM GRH'
$launch.Save()
$stop = $shell.CreateShortcut((Join-Path $desktop ('Arr' + [char]0x00EA + 'ter GRH.lnk')))
$stop.TargetPath = Join-Path $target 'scripts\Arreter-GRH.bat'
$stop.WorkingDirectory = $target
$stop.IconLocation = "$env:SystemRoot\System32\shell32.dll,27"
$stop.Description = 'Arreter GALAXAM GRH'
$stop.Save()
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $target 'scripts\schedule-backup-grh.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Installation copiee, mais planification de la sauvegarde echouee.' }
Write-Host "GRH installe dans $target. Raccourcis du Bureau prets."
