#!/usr/bin/env bash
# Prepara el toolchain de RAZTI en Codespaces / devcontainer (C30).
# Versiones fijas: Clojure CLI 1.12.6.1673; pnpm según "packageManager" de package.json.
set -euo pipefail

CLOJURE_VERSION="1.12.6.1673"

if ! command -v clojure >/dev/null 2>&1; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/linux-install.sh" \
    "https://github.com/clojure/brew-install/releases/download/${CLOJURE_VERSION}/linux-install.sh"
  chmod +x "$tmp/linux-install.sh"
  sudo "$tmp/linux-install.sh"
  rm -rf "$tmp"
fi

corepack enable
pnpm install --frozen-lockfile --ignore-scripts

# Precarga por alias (ver CLAUDE.md): cada gate resuelve sus propias versiones.
clojure -P -M:test
clojure -P -M:cljfmt
clojure -P -M:run
clojure -P -T:build
pnpm exec shadow-cljs compile test

echo "Toolchain de RAZTI listo. Para jugar: pnpm exec shadow-cljs release app && clojure -M:run"
