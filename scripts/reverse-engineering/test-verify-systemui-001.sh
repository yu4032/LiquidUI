#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY="$ROOT/scripts/reverse-engineering/verify-systemui-001.sh"
APK="${1:-/mnt/data/liquidui-bootstrap/MiuiSystemUI.apk}"

if [[ ! -x "$VERIFY" ]]; then
  echo "verifier missing or not executable: $VERIFY" >&2
  exit 1
fi

"$VERIFY" "$APK" | grep -q 'systemui-001 provenance PASS'

bad="$(mktemp --suffix=.apk)"
trap 'rm -f "$bad"' EXIT
cp "$APK" "$bad"
printf '\0' >> "$bad"
if "$VERIFY" "$bad" >/dev/null 2>&1; then
  echo "mutated APK was incorrectly accepted" >&2
  exit 1
fi

echo "provenance tests PASS"
