param([string] $BackupDirectory = [Environment]::GetEnvironmentVariable('SIRH_BACKUP_DIRECTORY', 'User'))
. (Join-Path $PSScriptRoot 'common.ps1')
Assert-Docker
Assert-Config | Out-Null
Enter-Operation
try {
    Assert-NoMaintenance
    if ([string]::IsNullOrWhiteSpace($BackupDirectory) -or -not (Test-Path $BackupDirectory -PathType Container)) { throw 'Support de sauvegarde indisponible : verifier SIRH_BACKUP_DIRECTORY.' }
    Stop-Web 'Sauvegarde'
    try { $archive = New-Backup $BackupDirectory }
    finally { Resume-Web }
    # Ne purger qu'apres succes de l'archive et du redemarrage.
    Get-ChildItem $BackupDirectory -Filter 'galaxam-*.tar' -File | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item -Force
    Write-Json @{ successAt=(Get-Date).ToString('o'); archive=$archive } (Join-Path $StateDirectory 'last-backup.json')
} finally { Exit-Operation }
