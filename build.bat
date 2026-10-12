@echo off
setlocal
cd /d "%~dp0"

set "JAVA_VER="
for /f "tokens=2 delims=\"" %%V in ('java -version 2^>^&1 ^| findstr /i /c:"version"') do set "JAVA_VER=%%V"

if not defined JAVA_VER (
  echo ERROR: Java is not available in PATH.
  echo Install JDK 25 and make sure java.exe is available in PATH.
  exit /b 1
)

for /f "tokens=1 delims=." %%V in ("%JAVA_VER%") do set "JAVA_MAJOR=%%V"

if not defined JAVA_MAJOR (
  echo ERROR: Could not determine Java version.
  java -version
  exit /b 1
)

if %JAVA_MAJOR% LSS 25 (
  echo ERROR: This project requires JDK 25 or newer for Minecraft 26.2.
  java -version
  exit /b 1
)

call "%~dp0gradlew.bat" build --console=plain
if errorlevel 1 (
  echo.
  echo BUILD FAILED.
  exit /b %ERRORLEVEL%
)

echo.
echo BUILD SUCCESSFUL. JAR files are in build\libs\
