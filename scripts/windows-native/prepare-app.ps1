$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$maven = 'C:\Program Files\GALAXAM\Toolchain\apache-maven-3.9.16\bin\mvn.cmd'
$npm = 'C:\Program Files\GALAXAM\Toolchain\node-v22.23.0-win-x64\npm.cmd'
$jdk = 'C:\Program Files\GALAXAM\Toolchain\jdk-17.0.20.1+1'
if (-not (Test-Path $maven) -or -not (Test-Path $npm) -or -not (Test-Path (Join-Path $jdk 'bin\java.exe'))) {
    throw 'JDK 17, Maven 3.9 et Node 22 doivent etre installes globalement.'
}
$env:JAVA_HOME = $jdk
$env:PATH = "$(Join-Path $jdk 'bin');$(Split-Path $npm);$env:PATH"
Push-Location (Join-Path $root 'backend')
try {
    & $maven -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Compilation backend echouee.' }
} finally { Pop-Location }
Push-Location (Join-Path $root 'frontend')
try {
    & $npm ci
    if ($LASTEXITCODE -ne 0) { throw 'Installation npm echouee.' }
    $env:VITE_API_BASE_URL = '/api/'
    & $npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Compilation frontend echouee.' }
} finally { Pop-Location }
Write-Host 'Application preparee. Lancez GALAXAM.bat ou le raccourci du Bureau.'
