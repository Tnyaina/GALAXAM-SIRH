. (Join-Path $PSScriptRoot 'common.ps1')
Assert-Docker
$c = Assert-Config
Invoke-Compose -Arguments @('ps','-a')
if (Test-Path $Marker) { Write-Warning 'Maintenance inachevee : ne pas redemarrer avant diagnostic.'; Get-Content $Marker }
if (Test-Path (Join-Path $StateDirectory 'last-backup.json')) { Get-Content (Join-Path $StateDirectory 'last-backup.json') }
if ($env:OS -eq 'Windows_NT') {
    Get-ScheduledTask -TaskName 'GALAXAM SIRH*' -ErrorAction SilentlyContinue | Get-ScheduledTaskInfo | Select-Object TaskName,LastRunTime,LastTaskResult,NextRunTime
}
Wait-Web
