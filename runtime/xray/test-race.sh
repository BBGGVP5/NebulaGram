#!/usr/bin/env bash
set -euo pipefail
# Optional GO_BOOTSTRAP_DIR is for a verified test toolchain, not a dependency upgrade.
if ! command -v go >/dev/null; then
  root=${GO_BOOTSTRAP_DIR:-/tmp/nebulalink-go}
  mkdir -p "$root"
  curl --connect-timeout 30 --max-time 300 -fsSL https://go.dev/dl/go1.27.1.linux-amd64.tar.gz -o "$root/go.tar.gz"
  echo "63d339f0da5ab53635a56f2490a7984dfe12dfcff22ad749f63edaf590168445  $root/go.tar.gz" | sha256sum -c -
  tar -xzf "$root/go.tar.gz" -C "$root"
  export PATH="$root/go/bin:$PATH"
fi
cd "$(dirname "$0")/../.."
go version
go test -race -count=1 ./core/... ./runtime/xray/... ./bind/... -timeout 120s
