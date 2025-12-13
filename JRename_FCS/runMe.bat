@echo off

REM --- Dossier du script ---
set "SCRIPT_DIR=%~dp0"

REM --- Jar en relatif ---
set "JAR_FILE=JRename_FCS-1.0-jar-with-dependencies.jar"

REM --- Candidats java.exe ---
set "JAVA_EXE="

REM jdk/jre locale à côté du script (./jdk/bin/java.exe)
set "JAVA_EXE=%SCRIPT_DIR%jdk-21\bin\java.exe"

:FoundJava

REM --- Vérifications ---
if not defined JAVA_EXE (
    echo [ERREUR] Impossible de trouver java.exe.
    echo Essayez l'une des options suivantes :
    echo  - Placer un JDK-21 portable dans ^"%SCRIPT_DIR%jdk-21^"
    exit /b 1
)


REM Vous pouvez ajouter des options JVM ici, ex: -Xms256m -Xmx1g
"%JAVA_EXE%" -jar "%SCRIPT_DIR%%JAR_FILE%"
echo "%JAVA_EXE%" -jar "%SCRIPT_DIR%%JAR_FILE%"

set "EXITCODE=%ERRORLEVEL%"

if %EXITCODE% neq 0 (
    echo [ERREUR] L'application s'est terminée avec le code: %EXITCODE%
) else (
    echo [OK] Exécution terminée avec succès.
)

exit /b %EXITCODE%