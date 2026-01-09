@echo off
echo Compiling...
call mvn clean compile

call mvn exec:java
pause
