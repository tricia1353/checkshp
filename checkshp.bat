@echo off
REM ============================================================================
REM checkshp Unified Wrapper Script
REM Used to call checkshp-0.1.0.jar, supports Check Mode and Intersect Mode
REM ============================================================================
REM Usage:
REM   Check Mode: checkshp.bat <shpPath> <detail|summary> <true|false> [targetCRS]
REM   Intersect Mode: checkshp.bat <shp1> intersect <shp2> [--merge-shp2] [--group-field <fieldName>]
REM ============================================================================

chcp 65001 >nul 2>&1

REM Get script directory
REM Use %~dp0 to get script directory and remove trailing backslash
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM If SCRIPT_DIR is empty, use alternative method with %~dp0
if not defined SCRIPT_DIR set "SCRIPT_DIR=%~dp0"

REM Set script directory as working directory
cd /d "%SCRIPT_DIR%" 2>nul
if errorlevel 1 (
    REM If cd fails, try using %~dp0 directly
    cd /d "%~dp0" 2>nul
    if errorlevel 1 (
        echo [ERROR] Cannot change to script directory
        echo Script path: %~f0
        echo Attempted: %SCRIPT_DIR%
        echo Current: %CD%
        exit /b 1
    )
    set "SCRIPT_DIR=%~dp0"
    if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
)

REM Set JAR file path (relative to script directory)
if "%SCRIPT_DIR:~-1%"=="\" (
    set "JAR_PATH=%SCRIPT_DIR%build\libs\checkshp-0.1.0.jar"
) else (
    set "JAR_PATH=%SCRIPT_DIR%\build\libs\checkshp-0.1.0.jar"
)

REM Check if JAR file exists
if not exist "%JAR_PATH%" (
    echo [ERROR] JAR file not found: %JAR_PATH%
    echo Please run 'gradlew.bat shadowJar' to build the JAR package
    exit /b 1
)

REM If no arguments provided, display usage
if "%~1"=="" (
    echo ============================================================================
    echo checkshp Tool - Usage
    echo ============================================================================
    echo.
    echo Check Mode:
    echo   checkshp.bat ^<shpPath^> ^<detail^|summary^> ^<true^|false^> [targetCRS]
    echo.
    echo   Parameters:
    echo     shpPath    - Shapefile path (required)
    echo     detail     - Output mode: "detail" or "summary"
    echo     true/false - Delete invalid geometries: "true" or "false"
    echo     targetCRS  - Target CRS (optional, for reprojection)
    echo.
    echo   Examples:
    echo     checkshp.bat "C:\data\city.shp" detail false
    echo     checkshp.bat "C:\data\city.shp" detail true
    echo     checkshp.bat "C:\data\city.shp" summary false "EPSG:4326"
    echo.
    echo ============================================================================
    echo Intersect Mode:
    echo   checkshp.bat ^<shp1^> intersect ^<shp2^> [--merge-shp2] [--group-field ^<fieldName^>]
    echo.
    echo   Parameters:
    echo     shp1         - First shapefile path (required)
    echo     intersect    - Keyword for intersect mode
    echo     shp2         - Second shapefile path (required)
    echo     --merge-shp2 - Optional: merge all features in shp2 before intersection
    echo     --group-field - Optional: group statistics by field name
    echo.
    echo   Examples:
    echo     checkshp.bat "C:\data\city.shp" intersect "C:\data\district.shp"
    echo     checkshp.bat "C:\data\city.shp" intersect "C:\data\district.shp" --merge-shp2
    echo     checkshp.bat "C:\data\city.shp" intersect "C:\data\district.shp" --group-field fieldName
    echo.
    echo ============================================================================
    exit /b 1
)

REM Check if Java is available
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found
    echo Please ensure Java 17 or higher is installed and added to system PATH
    exit /b 1
)

REM Determine mode: if 2nd argument is "intersect", then Intersect Mode
if /i "%~2"=="intersect" (
    REM ============================================================================
    REM Intersect Mode
    REM ============================================================================
    REM Parameter format: <shp1> intersect <shp2> [--merge-shp2] [--group-field <fieldName>]
    
    if "%~3"=="" (
        echo [ERROR] Intersect mode requires second shapefile path
        echo Usage: checkshp.bat ^<shp1^> intersect ^<shp2^> [--merge-shp2] [--group-field ^<fieldName^>]
        exit /b 1
    )
    
    echo [INFO] Mode: Intersect Statistics
    echo [INFO] Shapefile 1: %~1
    echo [INFO] Shapefile 2: %~3
    echo.
    
    REM Execute Java program, pass all arguments directly (%* preserves quotes in arguments)
    java -jar "%JAR_PATH%" %*
    
) else (
    REM ============================================================================
    REM Check Mode
    REM ============================================================================
    REM Parameter format: <shpPath> <detail|summary> <true|false> [targetCRS]
    
    if "%~2"=="" (
        echo [ERROR] Check mode requires output mode parameter (detail or summary)
        exit /b 1
    )
    
    if "%~3"=="" (
        echo [ERROR] Check mode requires delete flag (true or false)
        exit /b 1
    )
    
    echo [INFO] Mode: Geometry Check
    echo [INFO] Shapefile: %~1
    echo [INFO] Output mode: %~2
    echo [INFO] Delete invalid: %~3
    if not "%~4"=="" echo [INFO] Target CRS: %~4
    echo.
    
    REM Execute Java program, pass all arguments directly (%* preserves quotes in arguments)
    java -jar "%JAR_PATH%" %*
)

REM Check execution result
if errorlevel 1 (
    echo.
    echo [ERROR] Execution failed with error code: %ERRORLEVEL%
    exit /b %ERRORLEVEL%
) else (
    echo.
    echo [SUCCESS] Execution completed successfully
    exit /b 0
)
