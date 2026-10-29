#!/usr/bin/env bash
# Installs the installer that scripts/package.sh built, starts Scorecard, and checks that it comes up: it must
# create its database and still be running a few seconds later. It also looks for the window and takes a
# screenshot where the system allows, into target/smoke. For the Linux and macOS build machines in
# .github/workflows/release.yml (it installs system-wide); Windows uses scripts/smoke-test.ps1.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT=target/smoke
mkdir -p "$OUT"
DB="$HOME/.scorecard/scorecard.db"

# Waits up to a minute for the app to create its database.
wait_for_db() {
    for _ in $(seq 1 60); do
        [[ -f "$DB" ]] && return 0
        sleep 1
    done
    echo "FAILED: the app did not create its database at $DB" >&2
    return 1
}

case "$(uname -s)" in
    Linux)
        sudo apt-get install -y -qq xvfb xdotool imagemagick >/dev/null
        sudo apt-get install -y -qq "$ROOT/$(ls target/installers/*.deb | head -1)"
        Xvfb :99 -screen 0 1280x900x24 &
        export DISPLAY=:99
        sleep 2
        /opt/scorecard/bin/Scorecard >"$OUT/app.log" 2>&1 &
        APP=$!
        wait_for_db
        sleep 5
        kill -0 "$APP" || { echo "FAILED: Scorecard exited" >&2; cat "$OUT/app.log"; exit 1; }
        echo "Window: $(xdotool search --name '^Scorecard' getwindowname 2>/dev/null || echo 'not found')"
        import -window root "$OUT/linux.png"
        kill "$APP"
        ;;
    Darwin)
        MOUNT=/Volumes/ScorecardSmoke
        hdiutil attach "$(ls target/installers/*.dmg | head -1)" -nobrowse -mountpoint "$MOUNT" >/dev/null
        cp -R "$MOUNT/Scorecard.app" /Applications/
        hdiutil detach "$MOUNT" >/dev/null
        open /Applications/Scorecard.app
        wait_for_db
        sleep 5
        pgrep -f Scorecard.app/Contents/MacOS/Scorecard >/dev/null || { echo "FAILED: Scorecard exited" >&2; exit 1; }
        echo "Windows: $(osascript -e 'tell application "System Events" to get name of every window of process "Scorecard"' 2>&1 || true)"
        screencapture -x "$OUT/macos.png" || echo "No screenshot: screencapture failed"
        pkill -f Scorecard.app/Contents/MacOS/Scorecard
        ;;
    *)
        echo "Use scripts/smoke-test.ps1 on Windows" >&2
        exit 1
        ;;
esac

echo "Smoke test passed: Scorecard installed, started, and created $DB"
