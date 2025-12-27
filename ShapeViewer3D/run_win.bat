@echo off
echo Compiling...
call mvn clean compile

echo Running on Windows...

call mvn exec:java -Dexec.mainClass="shapeviewer.ShapeViewerApp" --add-opens java.desktop/sun.awt=ALL-UNNAMED
pause