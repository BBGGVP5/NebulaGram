#!/usr/bin/env bash
# Prepare a buildable tree for one platform: pristine upstream + our overlay +
# our patch series. The upstream submodule is reset first, so this is always
# reproducible and never accumulates half-applied state.
#
#   scripts/apply-overlay.sh android
set -euo pipefail

platform="${1:?usage: apply-overlay.sh <android|ios|desktop>}"

case "$platform" in
  android) submodule="vendor/telegram-android" ;;
  ios)     submodule="vendor/telegram-ios" ;;
  desktop) submodule="vendor/tdesktop" ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
overlay="$root/platform/$platform/overlay"
patches="$root/patches/$platform"
tree="$root/$submodule"

echo "resetting $submodule to its pinned commit"
git -C "$tree" reset --hard
git -C "$tree" clean -fdx

if [ -d "$overlay" ]; then
  echo "copying overlay files"
  # -a keeps the directory layout; overlay paths mirror the upstream tree.
  cp -a "$overlay/." "$tree/"
fi

shopt -s nullglob
for patch in "$patches"/*.patch; do
  echo "applying $(basename "$patch")"
  git -C "$tree" apply --3way "$patch"
done
shopt -u nullglob

echo "$platform tree is ready at $submodule"
