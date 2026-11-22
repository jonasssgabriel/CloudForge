@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Apache NetBeans\jdk"
set "PATH=%JAVA_HOME%\bin;C:\Program Files\Apache NetBeans\java\maven\bin;%PATH%"
cd /d "%~dp0..\ServidorCloudForge"
if not exist target\cp.txt (
  echo Rode 00-compilar-tudo.bat primeiro.
  pause
  exit /b 1
)
set /p CP=<target\cp.txt
java -Dstdout.encoding=UTF-8 -cp target\classes;%CP% com.mycompany.servidorcloudforge.ServidorCloudForge 50051 %*
pause
