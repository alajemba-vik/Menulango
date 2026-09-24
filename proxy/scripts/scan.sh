#!/usr/bin/env bash
# Sends a menu photo to the proxy and prints the streamed JSON as it arrives.
#   usage: scripts/scan.sh <PROXY_URL> <photo.jpg> [locale]
# Resize first for a realistic test:  sips -Z 1536 -s formatOptions 80 in.jpg --out menu.jpg
set -euo pipefail
url="${1:?proxy url}"; photo="${2:?jpeg path}"; locale="${3:-en-US}"
body=$(mktemp)
trap 'rm -f "$body"' EXIT
printf '{"image":"%s","locale":"%s"}' "$(base64 < "$photo" | tr -d '\n')" "$locale" > "$body"
curl -sS -N --fail-with-body -X POST "${url%/}/scan" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: curl-test-device-0001" \
  --data-binary @"$body"
echo
