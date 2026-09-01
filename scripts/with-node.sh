#!/usr/bin/env bash
# Puts nvm's node on PATH when the caller (el daemon de Gradle, por ejemplo) no lo tiene.
set -euo pipefail

if ! command -v npm >/dev/null; then
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  if [[ -s "$NVM_DIR/nvm.sh" ]]; then
    set +eu
    # nvm.sh llama a `head` sin calificar: si hay un `head` de perl (LWP) antes en
    # el PATH, escupe su usage sin fallar. Ese ruido no va al log del build.
    . "$NVM_DIR/nvm.sh" >/dev/null 2>&1
    set -euo pipefail
  fi
fi

if ! command -v npm >/dev/null; then
  echo "npm no está en PATH. Instalá Node 22+ (nvm) y reintentá." >&2
  exit 1
fi

exec "$@"
