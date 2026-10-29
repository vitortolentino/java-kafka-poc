#!/usr/bin/env bash
# Hot reload inside Docker.
# The source lives on /mnt/c (NTFS): writes made by IntelliJ on Windows do not
# raise inotify events in WSL, so the source is polled instead. On change it
# recompiles into target/classes and Spring DevTools restarts the application.
# Changes to pom.xml require restarting the container (docker compose restart app).
set -uo pipefail

INTERVAL="${WATCH_INTERVAL:-2}"
DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

snapshot() {
  find src/main -type f -printf '%p %T@ %s\n' | sort | md5sum
}

mvn -q compile || echo "[hot-reload] initial compilation failed"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="$DEBUG_OPTS" &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null; wait "$APP_PID"' EXIT INT TERM

last=$(snapshot)
while kill -0 "$APP_PID" 2>/dev/null; do
  sleep "$INTERVAL"
  current=$(snapshot)
  if [[ "$current" != "$last" ]]; then
    last=$current
    echo "[hot-reload] change detected, recompiling..."
    if mvn -q -o compile; then
      echo "[hot-reload] compiled, DevTools will restart the application"
    else
      echo "[hot-reload] compilation failed, waiting for a fix"
    fi
  fi
done
