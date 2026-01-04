#!/bin/bash

cd "$(dirname "$0")"

echo "Kompiliere Änderungen..."
# Dieser Befehl hat gefehlt: Er baut den Java-Code neu!
mvn clean compile

echo "Baue Classpath..."
CP="target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout)"

echo "Starte Applikation auf macOS (Apple Silicon)..."

java \
  --add-opens java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens java.desktop/sun.lwawt=ALL-UNNAMED \
  --add-opens java.desktop/sun.lwawt.macosx=ALL-UNNAMED \
  -cp "$CP" \
  kitchenmaker.KitchenApp