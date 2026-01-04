@echo off
echo Compiling...
call mvn clean compile

echo Running on Windows...
call mvn exec:java
pause
