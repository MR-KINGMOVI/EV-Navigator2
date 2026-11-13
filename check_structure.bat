@echo off
echo Checking project structure...
dir /s /b "%~dp0settings.gradle.kts"
dir /s /b "%~dp0core\roadprofile"
dir /s /b "%~dp0core\domain"
echo Project structure check completed.
pause
