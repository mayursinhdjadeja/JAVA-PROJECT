@echo off
REM ============================================================================
REM Smart Project Planner - Run Script (Windows)
REM Starts the built-in JDK HttpServer on port 8080
REM ============================================================================

if not exist "backend\bin\com\smartProjectPlanner\SmartProjectPlannerApp.class" (
    echo [NOTICE] Compiled binaries not found. Running build.bat first...
    call build.bat
    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)

echo [RUN] Setting up Classpath...
set CP=backend\bin;backend\lib\*;backend\lib\mysql-connector-j-26.10.0.jar

echo [RUN] Launching Smart Project Planner Application...
echo [RUN] Server will be available at http://localhost:8080/
java -cp "%CP%" com.smartProjectPlanner.SmartProjectPlannerApp %*
