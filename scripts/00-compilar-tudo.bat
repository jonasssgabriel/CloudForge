@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Apache NetBeans\jdk"
set "PATH=%JAVA_HOME%\bin;C:\Program Files\Apache NetBeans\java\maven\bin;%PATH%"
cd /d "%~dp0.."
for %%P in (ServicoNomes ServidorCloudForge ClienteCloudForge) do (
  echo === Compilando %%P ===
  pushd %%P
  call mvn -q clean package
  if errorlevel 1 ( echo ERRO em %%P & popd & pause & exit /b 1 )
  call mvn -q dependency:build-classpath -Dmdep.outputFile=target\cp.txt
  popd
)
echo Tudo compilado.
pause
