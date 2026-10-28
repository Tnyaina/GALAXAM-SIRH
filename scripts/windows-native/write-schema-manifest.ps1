param([Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$files = @(1..6 | ForEach-Object { Get-ChildItem (Join-Path $repo 'backend\conception\bdd') -Filter ('{0:D2}_*.sql' -f $_) })
$files += @(Get-ChildItem (Join-Path $repo 'docker\migrations') -Filter '*.sql' | Sort-Object Name)
if ($files.Count -lt 8) { throw 'Fichiers SQL canoniques incomplets.' }
$schema = @($files | ForEach-Object { [ordered]@{ path = $_.FullName.Substring($repo.Length + 1).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } })
@{ format = 'galaxam-schema-files-v1'; files = $schema } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Destination -Encoding UTF8
Write-Host "Manifest SQL : $Destination"
