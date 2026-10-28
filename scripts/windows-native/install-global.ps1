param()

$ErrorActionPreference = 'Stop'
Start-Transcript -Path (Join-Path $PSScriptRoot 'install-global.log') -Append | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Ouvrir PowerShell en administrateur puis relancer ce script.'
}

$base = $PSScriptRoot
$downloads = Join-Path $base 'downloads'
$destination = Join-Path $env:ProgramFiles 'GALAXAM\Toolchain'
$items = @(
    @{ Archive = 'temurin-17.0.20.1+1.zip'; Algorithm = 'SHA256'; Hash = 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0'; Source = 'jdk-17.0.20.1+1'; Target = 'jdk-17.0.20.1+1' },
    @{ Archive = 'apache-maven-3.9.16-bin.zip'; Algorithm = 'SHA512'; Hash = 'ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3'; Source = 'apache-maven-3.9.16'; Target = 'apache-maven-3.9.16' },
    @{ Archive = 'node-v22.23.0-win-x64.zip'; Algorithm = 'SHA256'; Hash = '425a5bd68cc95e8eb16bcccd0a75081b48983fc6a26f67126bd4d6c7198231e8'; Source = 'node-v22.23.0-win-x64'; Target = 'node-v22.23.0-win-x64' },
    @{ Archive = 'postgresql-15.19-5-windows-x64-binaries.zip'; Algorithm = 'SHA256'; Hash = '49e0690194d9fe2bb4d7d6587fdddaa85a3eaaafe2fd5480e0916fcb25d0143e'; Source = 'pgsql'; Target = 'postgresql-15.19' }
)

foreach ($item in $items) {
    $archive = Join-Path $downloads $item.Archive
    $target = Join-Path $destination $item.Target
    if (-not (Test-Path -LiteralPath $archive)) { throw "Archive absente : $($item.Archive)" }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $item.Algorithm).Hash -ine $item.Hash) { throw "Empreinte incorrecte : $($item.Archive)" }
    if (Test-Path -LiteralPath $target) { throw "Installation déjà présente, aucun écrasement : $target" }
}

New-Item -ItemType Directory -Path $destination -Force | Out-Null
foreach ($item in $items) {
    $target = Join-Path $destination $item.Target
    $staging = Join-Path $destination ('.pending-' + [guid]::NewGuid().ToString('N'))
    try {
        Expand-Archive -LiteralPath (Join-Path $downloads $item.Archive) -DestinationPath $staging
        $source = Join-Path $staging $item.Source
        if (-not (Test-Path -LiteralPath $source)) { throw "Racine absente dans l'archive : $($item.Source)" }
        Move-Item -LiteralPath $source -Destination $target
    } finally {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
    }
    Write-Host "Installé : $target"
}

$jdk = Join-Path $destination 'jdk-17.0.20.1+1'
$paths = @(
    (Join-Path $jdk 'bin'),
    (Join-Path $destination 'apache-maven-3.9.16\bin'),
    (Join-Path $destination 'node-v22.23.0-win-x64'),
    (Join-Path $destination 'postgresql-15.19\bin')
)
$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$segments = @($machinePath -split ';' | Where-Object { $_ })
foreach ($path in $paths) {
    if (-not ($segments | Where-Object { $_.TrimEnd('\') -ieq $path.TrimEnd('\') })) { $segments += $path }
}
[Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'Machine')
[Environment]::SetEnvironmentVariable('Path', ($segments -join ';'), 'Machine')
Write-Host 'JAVA_HOME et PATH système configurés. Ouvrir un nouveau terminal pour les utiliser.'
Write-Host 'PostgreSQL est installe comme outil ; aucune base ni service ne sont crees.'
