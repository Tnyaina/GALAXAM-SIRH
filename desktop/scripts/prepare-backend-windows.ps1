param()

$ErrorActionPreference = 'Stop'
$desktop = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $desktop '..')).Path
$bundle = Join-Path $desktop 'bundle'
$runtime = Join-Path $bundle 'runtime'
$jdk = $env:JAVA_HOME
if (-not $jdk) { throw 'JAVA_HOME doit désigner un JDK Temurin 17 Windows x64.' }
$release = Join-Path $jdk 'release'
$jlink = Join-Path $jdk 'bin\jlink.exe'
if (-not (Test-Path $release) -or -not (Test-Path $jlink)) { throw 'JDK Windows avec jlink introuvable.' }
$metadata = Get-Content $release -Raw
if ($metadata -notmatch 'JAVA_VERSION="17\.' -or $metadata -notmatch 'IMPLEMENTOR="Eclipse Adoptium"') {
    throw 'Le build exige un JDK Eclipse Temurin 17.'
}
Push-Location (Join-Path $repo 'backend')
try {
    & mvn.cmd -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Compilation Spring Boot échouée.' }
} finally { Pop-Location }
New-Item -ItemType Directory -Force $bundle | Out-Null
Copy-Item (Join-Path $repo 'backend\target\sirh-0.0.1-SNAPSHOT.jar') (Join-Path $bundle 'backend.jar') -Force
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
$modules = 'java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.localedata,jdk.security.auth,jdk.security.jgss,jdk.unsupported,jdk.zipfs'
& $jlink --module-path (Join-Path $jdk 'jmods') --add-modules $modules --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtime
if ($LASTEXITCODE -ne 0) { throw 'Création du runtime Java échouée.' }
Get-ChildItem (Join-Path $runtime 'bin') -File | Where-Object Name -ne 'java.exe' | Remove-Item -Force
$legal = Join-Path $runtime 'legal'
if (-not (Test-Path (Join-Path $runtime 'bin\java.exe')) -or -not (Test-Path (Join-Path $legal 'java.base\LICENSE'))) {
    throw 'Runtime Java ou licences incomplets.'
}
$licenses = Join-Path $bundle 'runtime-licenses.zip'
if (Test-Path $licenses) { Remove-Item $licenses -Force }
Compress-Archive -Path $legal -DestinationPath $licenses
Remove-Item $legal -Recurse -Force
Write-Host "JAR Spring et runtime Temurin Windows préparés dans $bundle"
