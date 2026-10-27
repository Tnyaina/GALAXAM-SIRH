param([Parameter(Mandatory)][string] $Ref, [Parameter(Mandatory)][string] $BackupDirectory)
. (Join-Path $PSScriptRoot 'common.ps1')
Assert-Docker
Assert-Config | Out-Null
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git for Windows requis pour les mises a jour.' }
if ($Ref -notmatch '^v[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$') { throw 'Utiliser un tag de livraison, par exemple v1.0.1.' }
Enter-Operation
try {
    Assert-NoMaintenance
    $dirty = & git -C $ProjectRoot status --porcelain
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Depot modifie : conserver les changements avant mise a jour. Ne pas forcer.' }
    & git -C $ProjectRoot fetch origin --tags
    if ($LASTEXITCODE -ne 0) { throw 'Impossible de recuperer les livraisons.' }
    $target = & git -C $ProjectRoot rev-parse --verify "refs/tags/$Ref^{commit}"
    if ($LASTEXITCODE -ne 0) { throw 'Tag introuvable.' }
    $previous = & git -C $ProjectRoot rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Revision actuelle inconnue.' }
    if ($previous -eq $target) { Write-Host 'Cette revision est deja installee.'; return }
    if (-not (Test-Path $BackupDirectory -PathType Container)) { throw 'Support de sauvegarde indisponible.' }
    Stop-Web "Mise a jour vers $Ref"
    $archive = New-Backup $BackupDirectory
    Write-Json @{ previous=$previous; target=$target; tag=$Ref; backup=$archive; startedAt=(Get-Date).ToString('o') } (Join-Path $StateDirectory 'last-update.json')
    & git -C $ProjectRoot switch --detach $target
    if ($LASTEXITCODE -ne 0) { throw 'Changement de revision impossible.' }
    # La base et les volumes ne sont jamais supprimes par une mise a jour.
    Assert-Config | Out-Null
    Invoke-Compose -Arguments @('build','backend','frontend')
    Invoke-Compose -Arguments @('run','--rm','--no-deps','migrate')
    Resume-Web
    Write-Host "Mise a jour $Ref terminee. Sauvegarde precedente : $archive"
} catch {
    if (Test-Path $Marker) { Invoke-Compose -Arguments @('stop','frontend','backend') }
    throw
} finally { Exit-Operation }
