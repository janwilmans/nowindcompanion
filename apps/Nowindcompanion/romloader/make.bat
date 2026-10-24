@echo off
rem assembles the ROM loader and puts it where the app reads it, run after changing romloader.asm
pushd %~dp0
..\..\..\..\nowindlibraries\Release\sjasm.exe romloader.asm || goto :error
copy /y romloader.bin ..\app\src\main\assets\romloader.bin > nul
del romloader.bin
popd
exit /b 0
:error
popd
exit /b 1
