#!/usr/bin/env bash
# Starts the Scorecard app. Used by the desktop and menu launchers.
JAR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/target/scorecard-1.0-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
    command -v notify-send >/dev/null && notify-send "Scorecard" "The app is not built yet: run 'mvn package' in ~/projects/scorecard"
    exit 1
fi
exec java -jar "$JAR" "$@"
