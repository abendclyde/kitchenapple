#!/bin/bash

cd "$(dirname "$0")"

echo "Baue Classpath..."
CP="target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)"

echo "Starte Applikation auf macOS (Apple Silicon)..."

# ÄNDERUNG: -XstartOnFirstThread wurde ENTFERNT!
# Wir nutzen nur noch die --add-opens Flags, die für moderne Java-Versionen wichtig sind.

java \
  --add-opens java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens java.desktop/sun.lwawt=ALL-UNNAMED \
  --add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED \
  -cp "$CP" \
  shapeviewer.ShapeViewerApp