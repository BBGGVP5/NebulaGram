#!/usr/bin/env bash
# Put the Telegram API credentials and the fork's package name into a prepared
# upstream tree, right before the build.
#
#   TELEGRAM_APP_ID=1234567 TELEGRAM_APP_HASH=abc... scripts/inject-keys.sh android
#
# This is a build-time substitution, never a committed patch: the tree under
# vendor/ is an artifact, so the keys live in your shell or in CI secrets and
# never reach git. Run it after scripts/apply-overlay.sh.
set -euo pipefail

platform="${1:?usage: inject-keys.sh <android>}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

case "$platform" in
  android) tree="$root/vendor/telegram-android" ;;
  *) echo "unknown platform: $platform" >&2; exit 2 ;;
esac

app_id="${TELEGRAM_APP_ID:-}"
app_hash="${TELEGRAM_APP_HASH:-}"
package="${APP_PACKAGE:-app.nebulagram.messenger}"

if [ -z "$app_id" ] || [ -z "$app_hash" ]; then
  cat >&2 <<'USAGE'
TELEGRAM_APP_ID and TELEGRAM_APP_HASH must be set.

Get them once at https://my.telegram.org -> API development tools.
See docs/BUILD-ANDROID.md for the walkthrough.
USAGE
  exit 1
fi

# The repository ships Telegram's own credentials as a placeholder. They are
# reserved for the official client and a build using them cannot sign in, so
# refusing them here saves a confusing "sign in failed" much later.
if [ "$app_id" = "4" ] || [ "$app_hash" = "014b35b6184100b085b0d0572f9b5103" ]; then
  echo "these are Telegram's own placeholder credentials; register your own app" >&2
  exit 1
fi
if ! printf '%s' "$app_id" | grep -Eq '^[0-9]+$'; then
  echo "TELEGRAM_APP_ID must be a number, got: $app_id" >&2
  exit 1
fi
if ! printf '%s' "$app_hash" | grep -Eq '^[0-9a-f]{32}$'; then
  echo "TELEGRAM_APP_HASH must be 32 hex characters" >&2
  exit 1
fi

build_vars="$tree/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
properties="$tree/gradle.properties"
for file in "$build_vars" "$properties"; do
  if [ ! -f "$file" ]; then
    echo "missing $file; run scripts/apply-overlay.sh $platform first" >&2
    exit 1
  fi
done

# sed rather than a scripting language: this runs on a CI image, on a developer's
# Linux box and inside Git Bash on Windows, and none of them agree on which
# python is on PATH.
sed -i -E "s/public static int APP_ID = [0-9]+;/public static int APP_ID = ${app_id};/" "$build_vars"
sed -i -E "s/public static String APP_HASH = \"[^\"]*\";/public static String APP_HASH = \"${app_hash}\";/" "$build_vars"

# A fork must not claim the official package name: it would clash with the real
# Telegram on the device and the two could not be installed side by side.
sed -i -E "s/^APP_PACKAGE=.*/APP_PACKAGE=${package}/" "$properties"

# Verify rather than trust: an upstream rename would otherwise produce a build
# that looks fine and cannot sign in.
grep -q "public static int APP_ID = ${app_id};" "$build_vars" ||
  { echo "BuildVars.java no longer declares APP_ID the way we expect" >&2; exit 1; }
grep -q "public static String APP_HASH = \"${app_hash}\";" "$build_vars" ||
  { echo "BuildVars.java no longer declares APP_HASH the way we expect" >&2; exit 1; }
grep -q "^APP_PACKAGE=${package}$" "$properties" ||
  { echo "gradle.properties no longer declares APP_PACKAGE" >&2; exit 1; }

# Push notifications: Telegram's servers deliver through Firebase, and they use
# the FCM service-account credentials registered for your api_id on
# my.telegram.org. That means a fork needs its own Firebase project, its own
# google-services.json listing this package, and that service account uploaded
# to my.telegram.org — see docs/BUILD-ANDROID.md.
#
# When such a file is present in the overlay it is already in place by now and
# lists the right package, so nothing below changes it. Otherwise we patch
# Telegram's file just enough for the Google Services plugin to accept the build:
# the app will run, but push will not arrive.
# Probe rather than just look up the name: on Windows "python3" resolves to a
# Microsoft Store stub that exits instead of running anything.
python_bin=""
for candidate in python3 python; do
  if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c "import json" >/dev/null 2>&1; then
    python_bin="$candidate"
    break
  fi
done
if [ -z "$python_bin" ]; then
  echo "a working python is required to adjust google-services.json" >&2
  exit 1
fi

for services in $(find "$tree" -maxdepth 2 -name google-services.json); do
  "$python_bin" - "$services" "$package" <<'PYTHON'
import json
import sys

path, package = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as handle:
    config = json.load(handle)

clients = config.get("client") or []
if not clients:
    sys.exit(f"{path} has no client entries")

def package_of(client):
    return client.get("client_info", {}).get("android_client_info", {}).get("package_name")

if not any(package_of(client) == package for client in clients):
    # Copy the first client so the api key and app id stay valid JSON, and give
    # the copy our package name.
    entry = json.loads(json.dumps(clients[0]))
    entry["client_info"]["android_client_info"]["package_name"] = package
    clients.append(entry)
    config["client"] = clients
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(config, handle, indent=2, ensure_ascii=False)
PYTHON
done

echo "credentials installed: app id ${app_id}, package ${package}"
