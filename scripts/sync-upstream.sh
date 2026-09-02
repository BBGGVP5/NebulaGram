#!/usr/bin/env bash
# Move one upstream client to a newer release and report whether our patch
# series still applies.
#
#   scripts/sync-upstream.sh android              # newest commit on the tracked branch
#   scripts/sync-upstream.sh android 62b56a07     # a specific commit
#   scripts/sync-upstream.sh desktop v5.9.0       # or a tag, where upstream still tags
#
# Nothing is committed unless every patch applies cleanly.
#
# Note on refs: Telegram-Android stopped tagging releases after 9.7.6, so the
# only way to follow it is by commit on master. The clones are shallow, so we
# fetch exactly the ref we are asked for rather than the whole history.
set -euo pipefail

platform="${1:?usage: sync-upstream.sh <android|ios|desktop> [ref]}"
ref="${2:-}"

case "$platform" in
  android) submodule="vendor/telegram-android" ;;
  ios)     submodule="vendor/telegram-ios" ;;
  desktop) submodule="vendor/tdesktop" ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
patches="$root/patches/$platform"
tree="$root/$submodule"

if [ ! -e "$tree/.git" ]; then
  echo "submodule $submodule is not initialised; run: git submodule update --init --depth 1" >&2
  exit 1
fi

branch="$(git -C "$root" config -f .gitmodules --get "submodule.$submodule.branch" || echo master)"
before="$(git -C "$tree" rev-parse --short HEAD)"

if [ -n "$ref" ]; then
  echo "fetching $ref"
  git -C "$tree" fetch --depth 1 origin "$ref"
else
  echo "fetching the newest commit on $branch"
  git -C "$tree" fetch --depth 1 origin "$branch"
  ref=FETCH_HEAD
fi

git -C "$tree" checkout --detach --force FETCH_HEAD 2>/dev/null || git -C "$tree" checkout --detach --force "$ref"
git -C "$tree" reset --hard
git -C "$tree" clean -fdx

after="$(git -C "$tree" rev-parse --short HEAD)"
subject="$(git -C "$tree" log -1 --format='%s (%ad)' --date=short)"
echo "$platform: $before -> $after — $subject"

if [ "$before" = "$after" ]; then
  echo "already up to date"
fi

failed=()
shopt -s nullglob
for patch in "$patches"/*.patch; do
  if git -C "$tree" apply --check "$patch" 2>/dev/null; then
    printf '  ok      %s\n' "$(basename "$patch")"
  else
    printf '  FAILED  %s\n' "$(basename "$patch")"
    failed+=("$patch")
  fi
done
shopt -u nullglob

if [ ${#failed[@]} -gt 0 ]; then
  echo
  echo "$platform: ${#failed[@]} patch(es) no longer apply to $after."
  echo "Fix each hook by hand in $submodule, then run:"
  for patch in "${failed[@]}"; do
    echo "  scripts/regen-patch.sh $platform $(basename "$patch" .patch)"
  done
  echo "See docs/UPSTREAM.md section 7."
  exit 1
fi

git -C "$root" add "$submodule"
echo
echo "$platform is ready on $after; every patch applies. Commit the submodule bump."
