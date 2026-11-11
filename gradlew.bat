@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (set JAVACMD=%JAVA_HOME%\bin\java.exe) else (set JAVACMD=java.exe)
if exist "%WRAPPER_JAR%" (
  "%JAVACMD%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  echo gradle-wrapper.jar not found - using Gradle from PATH. 1>&2
  gradle %*
  exit /b %ERRORLEVEL%
)
echo ERROR: gradle\wrapper\gradle-wrapper.jar is missing and no 'gradle' command was found. 1>&2
echo Run "gradle wrapper --gradle-version 9.2.1" once, or use the GitHub Actions workflow. 1>&2
exit /b 1
