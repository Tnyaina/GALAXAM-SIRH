param([Parameter(Mandatory)][string] $Archive)
. (Join-Path $PSScriptRoot 'common.ps1')
Assert-Docker
Assert-Config | Out-Null
Enter-Operation
$temp = Join-Path $StateDirectory ('restore-' + [guid]::NewGuid().ToString('N'))
$helper = 'galaxam-restore-' + [guid]::NewGuid().ToString('N')
try {
    $source = (Resolve-Path $Archive).Path
    # Extraire uniquement les trois noms attendus, jamais les chemins arbitraires de l'archive.
    New-Item -ItemType Directory -Path $temp | Out-Null
    & tar -xf $source -C $temp database.dump uploads.tar manifest.json
    if ($LASTEXITCODE -ne 0) { throw 'Archive incomplete.' }
    foreach ($name in @('database.dump','uploads.tar','manifest.json')) {
        if ((Get-Item (Join-Path $temp $name)).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Lien interdit dans une archive.' }
    }
    $manifest = Get-Content (Join-Path $temp 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.format -ne 'galaxam-web-local-v2' -or (Get-FileHash (Join-Path $temp 'database.dump')).Hash -ne $manifest.databaseSha256 -or (Get-FileHash (Join-Path $temp 'uploads.tar')).Hash -ne $manifest.uploadsSha256) { throw 'Archive invalide ou alteree.' }
    if ($manifest.commit -match '^[a-f0-9]{40,64}$') {
        if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git requis pour verifier la revision de cette archive.' }
        $current = & git -C $ProjectRoot rev-parse HEAD
        if ($LASTEXITCODE -ne 0 -or $current -ne $manifest.commit) { throw "Selectionner la revision $($manifest.commit), reconstruire les images, puis restaurer cette archive." }
    }
    $pg = Get-Container 'postgres'
    $backend = Get-Container 'backend'
    $image = (& docker inspect --format '{{.Image}}' $pg).Trim()
    Invoke-Docker -Arguments @('cp', (Join-Path $temp 'database.dump'), "${pg}:/tmp/galaxam-restore.dump")
    Invoke-Docker -Arguments @('exec', $pg, 'pg_restore', '--list', '/tmp/galaxam-restore.dump') | Out-Null
    Write-Host "Archive du $($manifest.createdAt), revision $($manifest.commit). Utiliser le code correspondant."
    if ((Read-Host 'Remplacer base et documents ? Tapez RESTAURER') -cne 'RESTAURER') { throw 'Restauration annulee.' }
    Stop-Web 'Restauration (ne pas redemarrer en cas d echec)'
    $safety = Join-Path (Split-Path $source) 'avant-restauration'
    New-Item -ItemType Directory -Path $safety -Force | Out-Null
    New-Backup $safety | Out-Null
    # Recréer une base vide elimine aussi les objets ajoutes apres la sauvegarde.
    Invoke-Docker -Arguments @('exec', $pg, 'dropdb', '-U', 'galaxam_admin', '--force', 'sirh_galaxam')
    Invoke-Docker -Arguments @('exec', $pg, 'createdb', '-U', 'galaxam_admin', '-O', 'galaxam_admin', 'sirh_galaxam')
    Invoke-Docker -Arguments @('exec', $pg, 'pg_restore', '-U', 'galaxam_admin', '-d', 'sirh_galaxam', '--single-transaction', '--no-owner', '--exit-on-error', '/tmp/galaxam-restore.dump')
    Invoke-Docker -Arguments @('create', '--name', $helper, '--volumes-from', $backend, '--entrypoint', 'sh', $image, '-c', 'set -eu; tar -tf /tmp/uploads.tar >/dev/null; find /app/uploads -mindepth 1 -maxdepth 1 -exec rm -rf {} \;; tar -xf /tmp/uploads.tar -C /app/uploads') | Out-Null
    Invoke-Docker -Arguments @('cp', (Join-Path $temp 'uploads.tar'), "${helper}:/tmp/uploads.tar")
    Invoke-Docker -Arguments @('start', '-a', $helper)
    if ((& docker inspect --format '{{.State.ExitCode}}' $helper) -ne '0') { throw 'Restauration des documents en echec.' }
    # Ne pas appliquer silencieusement de nouvelles migrations lors d'une restauration.
    Resume-Web
    Write-Host 'Restauration terminee. Verifier connexion, dossiers et documents.'
} catch {
    if (Test-Path $Marker) { Invoke-Compose -Arguments @('stop','frontend','backend') }
    throw
} finally {
    & docker rm -f $helper *> $null
    if ($pg) { & docker exec $pg rm -f /tmp/galaxam-restore.dump *> $null }
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
    Exit-Operation
}
