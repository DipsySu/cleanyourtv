#!/usr/bin/env bash
# Build this fork's Xiaomi-compatible launcher without changing a connected TV.
# Uses the current Android SDK, Java installation and local signing key.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease \
  -PlauncherApplicationId=com.ts.launcher "$@"
apk_file="$PWD/app/build/outputs/apk/release/app-release.apk"
apk_bytes=$(wc -c < "$apk_file")
if (( apk_bytes >= 8388608 )); then
  echo "APK exceeds this profile's 8 MiB budget." >&2
  exit 1
fi
printf '\nAPK: %s (%s bytes)\n' "$apk_file" "$apk_bytes"
