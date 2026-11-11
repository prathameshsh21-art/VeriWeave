@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for VeriWeave - Locked to Approved Stack Java 17
@REM ----------------------------------------------------------------------------
@echo off
setlocal

if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
)

if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

set "MAVEN_CMD="
if exist "%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd" (
    set "MAVEN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd"
) else (
    where mvn >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set "MAVEN_CMD=mvn"
    )
)

if "%MAVEN_CMD%"=="" (
    echo Error: Maven executable not found.
    exit /b 1
)

call "%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%

