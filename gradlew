#!/bin/sh
#
# DetergentMC Gradle wrapper script (Gradle 8.5).
#
# NOTE: this file was written by hand because the `gradle wrapper` task
# could not validate the distribution URL in the build sandbox
# (its HEAD request is rejected by the sandbox proxy). It is a minimal
# POSIX wrapper: it finds a Java runtime and hands over to the wrapper
# jar, which downloads the Gradle 8.5 distribution on first use.
# On a normal machine, replace it with the output of
# `gradle wrapper --gradle-version 8.5` if you prefer the stock script.

# Resolve the directory this script lives in.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Use JAVA_HOME when set, otherwise the `java` on PATH.
if [ -n "$JAVA_HOME" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
else
    JAVA_EXE="java"
fi

if ! command -v "$JAVA_EXE" >/dev/null 2>&1; then
    echo "ERROR: No Java runtime found. Set JAVA_HOME to a JDK 17+ installation." >&2
    exit 1
fi

# The wrapper jar bootstraps the Gradle distribution.
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Force IPv4 for the Gradle client JVM: it talks to the Gradle daemon over
# loopback TCP, and some sandboxed / proxied networks block Java's
# IPv6-mapped loopback connections. Harmless on a normal machine.
DEFAULT_JVM_OPTS="-Djava.net.preferIPv4Stack=true"

# shellcheck disable=SC2086
exec "$JAVA_EXE" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
