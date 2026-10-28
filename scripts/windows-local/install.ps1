param([string] $BackupDirectory = [Environment]::GetEnvironmentVariable('SIRH_BACKUP_DIRECTORY', 'User'))
. (Join-Path $PSScriptRoot 'common.ps1')
Assert-Docker
if ($env:OS -ne 'Windows_NT') { throw 'Installateur reserve a Windows. Voir le guide pour les commandes Docker.' }
Enter-Operation
try {
    Assert-NoMaintenance
    if (-not [Environment]::GetEnvironmentVariable('SIRH_PROD_DB_PASSWORD', 'User')) {
        $name = Read-Host 'Nom du premier administrateur [Tatamo Michele]'
        if ([string]::IsNullOrWhiteSpace($name)) { $name = 'Tatamo Michele' }
        $email = Read-Host 'Adresse e-mail du premier administrateur [daf@galaxam.com]'
        if ([string]::IsNullOrWhiteSpace($email)) { $email = 'daf@galaxam.com' }
        if ($email -notmatch '^[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$') { throw 'Adresse e-mail invalide.' }
        $secure = Read-Host 'Mot de passe administrateur (lettres, chiffres et ! _ + . - ; minimum 12 caracteres)' -AsSecureString
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
        if ($password.Length -lt 12 -or $password -cnotmatch '[A-Z]' -or $password -cnotmatch '[a-z]' -or $password -notmatch '[0-9]' -or $password -notmatch '^[A-Za-z0-9!_+.#-]+$') { throw 'Mot de passe invalide.' }
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try {
            $bytes = New-Object byte[] 64
            $rng.GetBytes($bytes)
            $jwt = [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
            $rng.GetBytes($bytes)
            $db = [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
        } finally { $rng.Dispose() }
        foreach ($pair in @(@('SIRH_PROD_DB_PASSWORD', $db), @('SIRH_PROD_JWT_SECRET', $jwt), @('SIRH_ADMIN_NAME', $name),
            @('SIRH_ADMIN_PASSWORD', $password), @('SIRH_ADMIN_EMAIL', $email), @('SIRH_PROD_WEB_PORT', '8088'),
            @('SIRH_PROD_CORS_ORIGIN', 'http://localhost:8088,http://127.0.0.1:8088'), @('SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED', 'true'))) {
            [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'User')
            [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
        }
        $password=$null
    }
    $config = Assert-Config
    if ([string]::IsNullOrWhiteSpace($BackupDirectory)) { throw 'Definir SIRH_BACKUP_DIRECTORY ou fournir -BackupDirectory.' }
    if (-not (Test-Path $BackupDirectory -PathType Container)) { throw 'Creer le dossier sur le support de sauvegarde, puis relancer.' }
    $backupPath = (Resolve-Path $BackupDirectory).Path
    $probe = Join-Path $backupPath ('.write-' + [guid]::NewGuid().ToString('N'))
    [IO.File]::WriteAllText($probe, 'test'); Remove-Item $probe
    Invoke-Compose -Arguments @('up','-d','--build')
    Wait-Web
    # Le port HTTP peut repondre avant la fin du seeder : verifier la base aussi.
    $pg = Get-Container 'postgres'
    $adminReady = $false
    for ($i=0; $i -lt 30; $i++) {
        $count = Invoke-Docker -Arguments @('exec', $pg, 'psql', '-X', '-U', 'galaxam_admin', '-d', 'sirh_galaxam', '-At', '-v', 'ON_ERROR_STOP=1', '-c', "SELECT count(*) FROM utilisateur u JOIN ref_role r ON r.id_role=u.id_role WHERE r.code_role='ROLE_ADMIN' AND u.est_actif=true")
        if ([int]$count -gt 0) { $adminReady=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $adminReady) { throw 'Aucun administrateur actif. Verifier le bootstrap avant de remettre le PC.' }
    # Garder le bootstrap idempotent actif : un volume PostgreSQL vierge après
    # une réinstallation pourra recréer le premier administrateur. Le seeder
    # ne modifie jamais un administrateur actif déjà présent.
    Stop-Web 'Finalisation installation'
    New-Backup $backupPath | Out-Null
    Resume-Web
    Write-Json @{ project=$config.name; databaseVolume=$config.volumes.pg_data.name; filesVolume=$config.volumes.business_files.name } (Join-Path $StateDirectory 'installation.json')
    $ps = (Get-Command powershell.exe).Source
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 5) -ExecutionTimeLimit (New-TimeSpan -Hours 4)
    $backupScript = Join-Path $PSScriptRoot 'backup.ps1'
    $action = New-ScheduledTaskAction -Execute $ps -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`""
    Register-ScheduledTask -TaskName 'GALAXAM SIRH - sauvegarde quotidienne' -Action $action -Trigger (New-ScheduledTaskTrigger -Daily -At '19:00') -Principal $principal -Settings $settings -Force | Out-Null
    $startupScript = Join-Path $PSScriptRoot 'start.ps1'
    $action = New-ScheduledTaskAction -Execute $ps -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$startupScript`""
    Register-ScheduledTask -TaskName 'GALAXAM SIRH - demarrage' -Action $action -Trigger (New-ScheduledTaskTrigger -AtLogOn -User $identity) -Principal $principal -Settings $settings -Force | Out-Null
    $url = 'http://localhost:' + $config.services.frontend.ports[0].published
    [IO.File]::WriteAllText((Join-Path ([Environment]::GetFolderPath('Desktop')) 'GALAXAM SIRH.url'), "[InternetShortcut]`r`nURL=$url`r`n")
    Write-Host 'Installation terminee. Tester la connexion puis un redemarrage Windows.'
} finally { Exit-Operation }
