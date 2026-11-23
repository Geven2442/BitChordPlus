#!/usr/bin/env bash
# Usage: tools/publish-release.sh <apk> <tag e.g. v1.10-plus14> <notes-file>
# Force-pushes an orphan `release-upload` branch (workflow + APK + notes); the
# repo's publish-release workflow turns it into a GitHub Release. History never grows.
set -euo pipefail
apk=$1; tag=$2; notes=$3
d=$(mktemp -d); mkdir -p "$d/.github/workflows" "$d/releases"
git show mine/release-upload:.github/workflows/release.yml > "$d/.github/workflows/release.yml"
cp "$apk" "$d/releases/"; echo "$tag" > "$d/releases/TAG"; cp "$notes" "$d/releases/NOTES.md"
cd "$d" && git init -q -b release-upload && git add -A \
  && git -c user.name=Geven2442 -c user.email=noreply@users.noreply.github.com commit -q -m "release upload $tag" \
  && git remote add origin https://github.com/Geven2442/BitChordPlus.git && git push -f origin release-upload
