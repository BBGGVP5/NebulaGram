#!/usr/bin/env bash
# Build the NebulaLink core for one target.
#
#   scripts/build-core.sh android     -> build/nebulalink.aar
#   scripts/build-core.sh ios         -> build/NebulaLink.xcframework   (macOS only)
#   scripts/build-core.sh desktop     -> build/libnebulalink.{dll,so,dylib}
#
# Android and iOS go through gomobile; the desktop uses a plain c-shared build,
# which is why the desktop fork needs no Go toolchain knowledge at all — it just
# links a library and a header.
set -euo pipefail

target="${1:?usage: build-core.sh <android|ios|desktop>}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/build"
mkdir -p "$out"
cd "$root/bind"   # the bindings module links the core and the engines

ensure_gomobile() {
  if ! command -v gomobile >/dev/null 2>&1; then
    echo "installing gomobile"
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest
  fi
  gomobile init
}

case "$target" in
  android)
    : "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME must point at an installed NDK}"
    ensure_gomobile
    gomobile bind -target=android -androidapi 21 \
      -ldflags "-s -w" \
      -o "$out/nebulalink.aar" ./mobile
    ;;
  ios)
    if [ "$(uname -s)" != "Darwin" ]; then
      echo "the iOS framework can only be built on macOS" >&2
      exit 1
    fi
    ensure_gomobile
    gomobile bind -target=ios,iossimulator \
      -ldflags "-s -w" \
      -o "$out/NebulaLink.xcframework" ./mobile
    ;;
  desktop)
    case "$(uname -s)" in
      Darwin)          lib="libnebulalink.dylib" ;;
      MINGW*|MSYS*|CYGWIN*) lib="libnebulalink.dll" ;;
      *)               lib="libnebulalink.so" ;;
    esac
    CGO_ENABLED=1 go build -buildmode=c-shared -ldflags "-s -w" \
      -o "$out/$lib" ./cabi
    echo "header: ${out}/${lib%.*}.h"
    ;;
  *)
    echo "unknown target: $target" >&2
    exit 2
    ;;
esac

echo "built $target core into $out"
