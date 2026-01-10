@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  checkshp startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and CHECKSHP_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\lib\checkshp-0.1.0-original.jar;%APP_HOME%\lib\gt-api-34.0.jar;%APP_HOME%\lib\gt-main-34.0.jar;%APP_HOME%\lib\gt-shapefile-34.0.jar;%APP_HOME%\lib\gt-referencing-34.0.jar;%APP_HOME%\lib\gt-metadata-34.0.jar;%APP_HOME%\lib\gt-geojson-core-34.0.jar;%APP_HOME%\lib\gt-coverage-34.0.jar;%APP_HOME%\lib\gt-coverage-api-34.0.jar;%APP_HOME%\lib\gt-geotiff-34.0.jar;%APP_HOME%\lib\gt-epsg-hsql-34.0.jar;%APP_HOME%\lib\imageio-ext-cog-reader-2.0.0.jar;%APP_HOME%\lib\imageio-ext-cog-streams-2.0.0.jar;%APP_HOME%\lib\imageio-ext-cog-commons-2.0.0.jar;%APP_HOME%\lib\imageio-ext-tiff-2.0.0.jar;%APP_HOME%\lib\imageio-ext-geocore-2.0.0.jar;%APP_HOME%\lib\imageio-ext-streams-2.0.0.jar;%APP_HOME%\lib\imageio-ext-utilities-2.0.0.jar;%APP_HOME%\lib\rendered-image-browser-0.9.0.jar;%APP_HOME%\lib\bandselect-0.9.0.jar;%APP_HOME%\lib\rescale-0.9.0.jar;%APP_HOME%\lib\stats-0.9.0.jar;%APP_HOME%\lib\binarize-0.9.0.jar;%APP_HOME%\lib\format-0.9.0.jar;%APP_HOME%\lib\border-0.9.0.jar;%APP_HOME%\lib\nullop-0.9.0.jar;%APP_HOME%\lib\algebra-0.9.0.jar;%APP_HOME%\lib\utilities-0.9.0.jar;%APP_HOME%\lib\iterators-0.9.0.jar;%APP_HOME%\lib\imageread-0.9.0.jar;%APP_HOME%\lib\imagen-core-0.9.0.jar;%APP_HOME%\lib\jts-core-1.20.0.jar;%APP_HOME%\lib\si-units-2.1.jar;%APP_HOME%\lib\si-quantity-2.1.jar;%APP_HOME%\lib\indriya-2.2.jar;%APP_HOME%\lib\systems-common-2.1.jar;%APP_HOME%\lib\unit-api-2.2.jar;%APP_HOME%\lib\uom-lib-common-2.2.jar;%APP_HOME%\lib\commons-io-2.19.0.jar;%APP_HOME%\lib\commons-lang3-3.18.0.jar;%APP_HOME%\lib\guava-33.4.8-jre.jar;%APP_HOME%\lib\jackson-annotations-2.19.0.jar;%APP_HOME%\lib\jackson-databind-2.19.0.jar;%APP_HOME%\lib\jackson-core-2.19.0.jar;%APP_HOME%\lib\hsqldb-2.7.2.jar;%APP_HOME%\lib\ejml-all-0.43.1.jar;%APP_HOME%\lib\jai-imageio-core-1.4.0.jar;%APP_HOME%\lib\aircompressor-0.27.jar;%APP_HOME%\lib\jaxb-runtime-2.4.0-b180830.0438.jar;%APP_HOME%\lib\jaxb-api-2.4.0-b180830.0359.jar;%APP_HOME%\lib\javax.activation-api-1.2.0.jar;%APP_HOME%\lib\ejml-simple-0.43.1.jar;%APP_HOME%\lib\ejml-fsparse-0.43.1.jar;%APP_HOME%\lib\ejml-fdense-0.43.1.jar;%APP_HOME%\lib\ejml-dsparse-0.43.1.jar;%APP_HOME%\lib\ejml-ddense-0.43.1.jar;%APP_HOME%\lib\ejml-cdense-0.43.1.jar;%APP_HOME%\lib\ejml-zdense-0.43.1.jar;%APP_HOME%\lib\ejml-core-0.43.1.jar;%APP_HOME%\lib\txw2-2.4.0-b180830.0438.jar;%APP_HOME%\lib\istack-commons-runtime-3.0.7.jar;%APP_HOME%\lib\stax-ex-1.8.jar;%APP_HOME%\lib\FastInfoset-1.2.15.jar;%APP_HOME%\lib\ehcache-3.4.0.jar;%APP_HOME%\lib\guava-32.0.0-jre.jar;%APP_HOME%\lib\slf4j-api-1.7.7.jar;%APP_HOME%\lib\failureaccess-1.0.1.jar;%APP_HOME%\lib\listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar;%APP_HOME%\lib\jsr305-3.0.2.jar;%APP_HOME%\lib\checker-qual-3.33.0.jar;%APP_HOME%\lib\error_prone_annotations-2.18.0.jar;%APP_HOME%\lib\j2objc-annotations-2.8.jar


@rem Execute checkshp
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %CHECKSHP_OPTS%  -classpath "%CLASSPATH%" com.example.gcheckshp.gcheckshp %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable CHECKSHP_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%CHECKSHP_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
