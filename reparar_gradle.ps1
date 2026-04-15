# Crear estructura de carpetas de Gradle
New-Item -ItemType Directory -Force -Path "gradle/wrapper"

# Crear el archivo gradlew (para Linux/GitHub Actions)
$gradlewContent = @"
#!/usr/bin/env sh
exec "./gradle/wrapper/gradle-wrapper.jar" "$@"
"@
Set-Content -Path "gradlew" -Value $gradlewContent -Encoding UTF8

# Crear el archivo gradlew.bat (para Windows)
$gradlewBatContent = @"
@rem Gradle Wrapper script for Windows
@if "%DEBUG%" == "" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
"@
Set-Content -Path "gradlew.bat" -Value $gradlewBatContent -Encoding UTF8

# Crear el archivo de propiedades del Wrapper
$propsContent = @"
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"@
Set-Content -Path "gradle/wrapper/gradle-wrapper.properties" -Value $propsContent -Encoding UTF8

Write-Host "✅ Archivos de Gradle generados correctamente." -ForegroundColor Green