@echo off
rem Compiles and runs Jackaroo. Uses the portable JDK in .tools if present, otherwise java/javac on PATH
rem (which must be JDK 17+ with JavaFX, e.g. Azul Zulu "JDK FX").
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "JAVAC=javac"
set "JAVA=java"
for /d %%D in (".tools\zulu*") do (
    set "JAVAC=%%~fD\bin\javac.exe"
    set "JAVA=%%~fD\bin\java.exe"
)

if exist out rmdir /s /q out
mkdir out
(for /f "delims=" %%F in ('dir /s /b "src\application\*.java" "src\engine\*.java" "src\model\*.java" "src\exception\*.java"') do (
    set "f=%%F"
    echo "!f:\=/!"
)) > out\sources.txt

"%JAVAC%" -encoding UTF-8 -d out @out\sources.txt
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
xcopy /e /i /q /y "src\main\resources" "out" >nul

"%JAVA%" -cp out application.Main
