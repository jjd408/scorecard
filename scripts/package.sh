#!/usr/bin/env bash
# Builds an installer for the system it runs on, with its own Java runtime, using jpackage:
# a .deb on Linux, a .dmg on macOS, an .msi on Windows (Git Bash). Run 'mvn package' first.
# Usage: scripts/package.sh VERSION   (numbers only, e.g. 1.0.0)
# The installer lands in target/installers. Used by .github/workflows/release.yml.
set -euo pipefail

VERSION="${1:?usage: scripts/package.sh VERSION}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR="$(ls target/scorecard-*.jar | head -1)"
INPUT=target/package-input
rm -rf "$INPUT" target/installers
mkdir -p "$INPUT"
# The jar's manifest finds the SQLite driver in lib/ beside it.
cp "$JAR" "$INPUT/"
cp -r target/lib "$INPUT/lib"

COMMON=(--name Scorecard --app-version "$VERSION" --vendor jjd408
    --description "Baseball scorecard: score games at-bat by at-bat"
    --input "$INPUT" --main-jar "$(basename "$JAR")" --main-class com.joe.scorecard.App
    --dest target/installers)

case "$(uname -s)" in
    Linux)
        jpackage "${COMMON[@]}" --type deb --icon packaging/scorecard.png \
            --linux-package-name scorecard --linux-shortcut --linux-menu-group Utility \
            --linux-deb-maintainer 26080013+jjd408@users.noreply.github.com \
            --linux-app-category utils
        ;;
    Darwin)
        # macOS wants an .icns, built from the PNG at the sizes it uses.
        ICONSET=target/scorecard.iconset
        rm -rf "$ICONSET" && mkdir -p "$ICONSET"
        for s in 16 32 128 256; do
            sips -z $s $s packaging/scorecard.png --out "$ICONSET/icon_${s}x${s}.png" >/dev/null
            sips -z $((s * 2)) $((s * 2)) packaging/scorecard.png --out "$ICONSET/icon_${s}x${s}@2x.png" >/dev/null
        done
        iconutil -c icns "$ICONSET" -o target/scorecard.icns
        jpackage "${COMMON[@]}" --type dmg --icon target/scorecard.icns \
            --mac-package-identifier com.joe.scorecard --mac-package-name Scorecard
        ;;
    MINGW* | MSYS* | CYGWIN*)
        # The upgrade ID stays the same for every version, so a new installer replaces the old one.
        jpackage "${COMMON[@]}" --type msi --icon packaging/scorecard.ico \
            --win-menu --win-menu-group Scorecard --win-shortcut --win-dir-chooser \
            --win-upgrade-uuid 577f63fb-6781-4995-90dd-08f88df5c8a5
        ;;
    *)
        echo "Unsupported system: $(uname -s)" >&2
        exit 1
        ;;
esac

ls -l target/installers
