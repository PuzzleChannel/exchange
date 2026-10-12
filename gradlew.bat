@echo off
setlocal
set "APP_HOME=%~dp0"
java -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" com.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
