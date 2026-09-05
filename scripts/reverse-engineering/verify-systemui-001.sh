#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SIZE=52843421
EXPECTED_SHA256=84bcf387b3a656299290f7ab833c6e80b55737e304e8f719c3be70f23564cd28
EXPECTED_DEX_COUNT=3
EXPECTED_PACKAGE='com.android.systemui'
EXPECTED_VERSION_NAME='16.03.251211.r'

apk="${1:-}"
if [[ -z "$apk" ]]; then
  echo "usage: $0 /path/to/MiuiSystemUI.apk" >&2
  exit 2
fi
if [[ ! -f "$apk" ]]; then
  echo "APK not found: $apk" >&2
  exit 2
fi

actual_size="$(stat -c '%s' "$apk")"
if [[ "$actual_size" != "$EXPECTED_SIZE" ]]; then
  echo "size mismatch: expected=$EXPECTED_SIZE actual=$actual_size" >&2
  exit 1
fi

actual_sha="$(sha256sum "$apk" | awk '{print $1}')"
if [[ "$actual_sha" != "$EXPECTED_SHA256" ]]; then
  echo "sha256 mismatch: expected=$EXPECTED_SHA256 actual=$actual_sha" >&2
  exit 1
fi

actual_dex_count="$(unzip -Z1 "$apk" | grep -Ec '^classes([0-9]+)?\.dex$')"
if [[ "$actual_dex_count" != "$EXPECTED_DEX_COUNT" ]]; then
  echo "DEX count mismatch: expected=$EXPECTED_DEX_COUNT actual=$actual_dex_count" >&2
  exit 1
fi

manifest_tmp="$(mktemp)"
trap 'rm -f "$manifest_tmp"' EXIT
unzip -p "$apk" AndroidManifest.xml > "$manifest_tmp"
manifest_strings="$( { strings -a "$manifest_tmp"; strings -el "$manifest_tmp"; } )"
if ! grep -Fq "$EXPECTED_PACKAGE" <<<"$manifest_strings"; then
  echo "package marker missing: $EXPECTED_PACKAGE" >&2
  exit 1
fi
if ! grep -Fq "$EXPECTED_VERSION_NAME" <<<"$manifest_strings"; then
  echo "versionName marker missing: $EXPECTED_VERSION_NAME" >&2
  exit 1
fi

printf 'systemui-001 provenance PASS\n'
printf '  size=%s\n' "$actual_size"
printf '  sha256=%s\n' "$actual_sha"
printf '  dex_count=%s\n' "$actual_dex_count"
printf '  package=%s\n' "$EXPECTED_PACKAGE"
printf '  versionName=%s\n' "$EXPECTED_VERSION_NAME"
