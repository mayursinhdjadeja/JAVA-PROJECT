@echo off
REM ============================================================================
REM Smart Project Planner - Database Connection Test Script
REM ============================================================================

echo [TEST-DB] Compiling latest backend code...
call build.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [TEST-DB] Running MySQL Connection Diagnostic...
echo --------------------------------------------------------------------------------
java -cp "backend\bin;backend\lib\*" com.smartProjectPlanner.config.DatabaseConnector
echo --------------------------------------------------------------------------------
echo.
pause
