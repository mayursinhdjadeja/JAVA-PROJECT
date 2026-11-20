@echo off
REM ============================================================================
REM Smart Project Planner - Build Script (Windows)
REM Compiles pure Java source files into backend/bin
REM ============================================================================

echo [BUILD] Creating output directory backend\bin...
if not exist "backend\bin" mkdir "backend\bin"

echo [BUILD] Locating Java Compiler (javac)...
set JAVAC_BIN=javac
where %JAVAC_BIN% >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    if exist "C:\Program Files\Java\latest\bin\javac.exe" (
        set JAVAC_BIN="C:\Program Files\Java\latest\bin\javac.exe"
    ) else (
        echo [ERROR] javac could not be found in PATH or standard Java directories.
        echo Please ensure JDK 17+ is installed and configured in PATH.
        exit /b 1
    )
)

echo [BUILD] Compiling Java source files...
%JAVAC_BIN% -encoding UTF-8 -d backend\bin backend\src\com\smartProjectPlanner\config\*.java backend\src\com\smartProjectPlanner\model\*.java backend\src\com\smartProjectPlanner\exception\*.java backend\src\com\smartProjectPlanner\util\*.java backend\src\com\smartProjectPlanner\dao\*.java backend\src\com\smartProjectPlanner\service\*.java backend\src\com\smartProjectPlanner\controller\*.java backend\src\com\smartProjectPlanner\*.java

if %ERRORLEVEL% EQU 0 (
    echo [BUILD SUCCESS] All classes compiled successfully into backend\bin!
) else (
    echo [BUILD FAILED] Compilation errors occurred.
    exit /b %ERRORLEVEL%
)
