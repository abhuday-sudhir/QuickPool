#!/usr/bin/env bash
# Start the backend with .env actually loaded.
#
# Spring Boot does NOT read .env files — application.yml resolves ${MAPS_SERVER_KEY} and
# ${FIREBASE_CREDENTIALS} from the process environment. Launching `java -jar` from a plain
# shell therefore gets the empty defaults, and both failures are quiet: no route line drawn,
# no pushes delivered. This script is the one command that is always right.
#
# Usage:  sh run.sh [port]        (default 8081)
set -euo pipefail
cd "$(dirname "$0")"

PORT="${1:-8081}"
JAR="target/quickpool-0.0.1-SNAPSHOT.jar"

if [ -f .env ]; then
    # set -a exports everything the file defines; without it `source` only sets shell
    # variables, which Spring never sees.
    set -a
    # shellcheck disable=SC1091
    . ./.env
    set +a
else
    echo "warning: no .env found — Directions and push will be disabled" >&2
fi

if [ ! -f "$JAR" ]; then
    echo "$JAR not found — building. Always 'clean': Maven leaves stale .class files behind"
    echo "when a source file is deleted, and a leftover @Service becomes a duplicate-bean"
    echo "startup failure that looks nothing like its cause."
    sh mvnw -q -DskipTests clean package
fi

exec java -jar "$JAR" --server.port="$PORT"
