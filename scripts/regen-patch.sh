#!/usr/bin/env bash
# Regenerate one patch from the current state of an upstream tree, after a hook
# was repaired by hand.
#
#   scripts/regen-patch.sh android 0001-application-loader-init
set -euo pipefail

platform="${1:?usage: regen-patch.sh <android|ios|desktop> <patch-name>}"
name="${2:?usage: regen-patch.sh <android|ios|desktop> <patch-name>}"

case "$platform" in
  android) submodule="vendor/telegram-android" ;;
  ios)     submodule="vendor/telegram-ios" ;;
  desktop) submodule="vendor/tdesktop" ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/patches/$platform/$name.patch"

# Only tracked upstream files belong in a patch: overlay files are untracked in
# the submodule and must never leak into the series.
git -C "$root/$submodule" diff -- . > "$out"

if [ ! -s "$out" ]; then
  echo "no changes to capture; $out would be empty" >&2
  rm -f "$out"
  exit 1
fi

echo "wrote $out ($(grep -c '^@@' "$out") hunk(s))"
echo "remember to update patches/$platform/HOOKS.md"
