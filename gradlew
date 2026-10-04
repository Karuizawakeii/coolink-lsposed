#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then JAVACMD="$JAVA_HOME/bin/java"; fi
exec "$JAVACMD" -Xmx2g -Dfile.encoding=UTF-8 -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
