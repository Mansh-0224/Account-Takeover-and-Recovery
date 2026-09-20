#!/usr/bin/env bash
# Simple smoke test: is the backend up and answering /api/health?
# Usage: ./tests/health-check.sh [base-url]     (default: http://localhost:8080)
set -e

BASE_URL="${1:-http://localhost:8080}"
OUT_FILE="$(mktemp)"

echo "Calling $BASE_URL/api/health ..."
HTTP_CODE="$(curl -sS -o "$OUT_FILE" -w "%{http_code}" "$BASE_URL/api/health")"

cat "$OUT_FILE"
echo
rm -f "$OUT_FILE"

if [ "$HTTP_CODE" = "200" ]; then
    echo "PASS: backend answered with HTTP 200"
else
    echo "FAIL: backend answered with HTTP $HTTP_CODE"
    exit 1
fi
