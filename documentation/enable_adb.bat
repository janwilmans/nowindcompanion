@echo off
:: this script enables wifi-debugging on pre-android-11 phones

:: copy this script to the adb.exe directory 
:: for example %USERPROFILE%\AppData\Local\Android\Sdk\platform-tools
echo Connect Phone using USB
pause

adb devices
adb tcpip 5555

echo Now disconnect the Phone from USB
pause
@echo on
adb connect 192.168.1.82:5555
@echo TCP/IP debugging enabled
