#!/usr/bin/env bash
# Move one upstream client to a new Telegram release and report whether our
# patch series still applies.
#
#   scripts/sync-upstream.sh android v11.13.0
#   scripts/sync-upstream.sh ios                 # latest tag
#   scripts/sync-upstream.sh desktop v5.9.0
#
# Nothing is committed unless every patch applies cleanly.
set -euo pipefail

platform="${1:?usage: sync-upstream.sh <android|ios|desktop> [tag]}"
tag="${2:-}"

case "$platform" in
  android) submodule="vendor/telegram-android" ;;
  ios)     submodule="vendor/telegram-ios" ;;
  desktop) submodule="vendor/tdesktop" ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
patches="$root/patches/$platform"

if [ ! -d "$root/$submodule/.git" ] && [ ! -f "$root/$submodule/.git" ]; then
  echo "submodule $submodule is not initialised; run: git submodule update --init" >&2
  exit 1
fi

git -C "$root/$submodule" fetch --tags --force

if [ -z "$tag" ]; then
  tag="$(git -C "$root/$submodule" tag --sort=-v:refname | head -1)"
  echo "latest upstream tag: $tag"
fi

git -C "$root/$submodule" checkout --detach "$tag"
git -C "$root/$submodule" reset --hard "$tag"
git -C "$root/$submodule" clean -fdx

failed=()
shopt -s nullglob
for patch in "$patches"/*.patch; do
  if git -C "$root/$submodule" apply --check "$patch" 2>/dev/null; then
    printf '  ok      %s\n' "$(basename "$patch")"
  else
    printf '  FAILED  %s\n' "$(basename "$patch")"
    failed+=("$patch")
  fi
done
shopt -u nullglob

if [ ${#failed[@]} -gt 0 ]; then
  echo
  echo "$platform: ${#failed[@]} patch(es) no longer apply to $tag."
  echo "Fix each hook by hand in $submodule, then run:"
  for patch in "${failed[@]}"; do
    echo "  scripts/regen-patch.sh $platform $(basename "$patch" .patch)"
  done
  echo "See docs/UPSTREAM.md section 7."
  exit 1
fi

git -C "$root" add "$submodule"
echo
echo "$platform is ready on $tag; every patch applies. Commit the submodule bump."
