#!/bin/sh
# Minimal Gradle wrapper launcher.
# Uses gradle/wrapper/gradle-wrapper.jar when present (generate it with: gradle wrapper --gradle-version 9.2.1),
# otherwise falls back to a Gradle installation on PATH.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
if [ -f "$WRAPPER_JAR" ]; then
  exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
elif command -v gradle >/dev/null 2>&1; then
  echo "gradle-wrapper.jar not found - using Gradle from PATH." >&2
  exec gradle "$@"
else
  echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing and no 'gradle' command was found." >&2
  echo "Run 'gradle wrapper --gradle-version 9.2.1' once, or use the GitHub Actions workflow." >&2
  exit 1
fi
