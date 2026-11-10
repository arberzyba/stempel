#!/usr/bin/env sh
# Downloads the official KoSIT XRechnung test suite into backend/.testsuite/kosit (git-ignored, and not
# deleted by mvn clean), where KositTestSuiteTest picks it up. Run from anywhere: sh backend/scripts/fetch-kosit-testsuite.sh
set -eu

VERSION="v2026-08-31"
ZIP="xrechnung-3.0.2-testsuite-2026-08-31.zip"
URL="https://github.com/itplr-kosit/xrechnung-testsuite/releases/download/${VERSION}/${ZIP}"

TARGET="$(cd "$(dirname "$0")/.." && pwd)/.testsuite/kosit"
rm -rf "$TARGET"
mkdir -p "$TARGET"

echo "Downloading KoSIT test suite ${VERSION} ..."
curl -fsSL -o "$TARGET/suite.zip" "$URL"
unzip -q "$TARGET/suite.zip" -d "$TARGET"
rm "$TARGET/suite.zip"

echo "Done: $(find "$TARGET/instances" -name '*.xml' | wc -l | tr -d ' ') invoices in $TARGET/instances"
