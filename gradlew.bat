@rem
@rem DetergentMC Gradle wrapper script for Windows (Gradle 8.5).
@rem
@rem NOTE: minimal batch wrapper; see gradlew for why it was written by hand.
@rem It finds a Java runtime and hands over to the wrapper jar, which
@rem downloads the Gradle 8.5 distribution on first use.
@rem
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem Use JAVA_HOME when set, otherwise the `java` on PATH.
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set WRAPPER_JAR=%DIRNAME%gradle\wrapper\gradle-wrapper.jar

@rem Force IPv4 for the Gradle client JVM (loopback to the daemon;
@rem some sandboxed / proxied networks block IPv6-mapped loopback).
set DEFAULT_JVM_OPTS=-Djava.net.preferIPv4Stack=true

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
