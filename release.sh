#!/usr/bin/env bash
# Baut die signierte Release-Version und legt sie als dist/TresenWerk-<Version>.apk ab.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f keystore.properties ]; then
    echo "keystore.properties fehlt – siehe ANLEITUNG.md, Abschnitt 'Release-Version für Tester'." >&2
    exit 1
fi
./gradlew --console=plain assembleRelease
version=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/TresenWerk-$version.apk"
echo "Fertig: dist/TresenWerk-$version.apk"
