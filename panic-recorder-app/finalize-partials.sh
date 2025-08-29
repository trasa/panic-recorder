#!/usr/bin/env bash
set -euo pipefail

# ---------- CONFIG (override with env or flags) ----------
ENDPOINT="${ENDPOINT:-https://sfo3.digitaloceanspaces.com}"
BUCKET="${BUCKET:-panic-recordings}"
PROFILE="${PROFILE:-do-spaces}"      # leave empty to use default profile
PREFIX="${PREFIX:-}"                 # optional: only process under this prefix
MODE="${MODE:-complete}"             # complete | abort
DRY_RUN="${DRY_RUN:-0}"              # 1 = preview, 0 = execute
MAX="${MAX:-0}"                      # 0 = all; otherwise limit

usage() {
  cat <<EOF
Usage: $(basename "$0") [--endpoint URL] [--bucket NAME] [--profile NAME] [--prefix PATH]
                        [--mode complete|abort] [--dry-run] [--max N]
Completes or aborts in-progress multipart uploads in an S3-compatible bucket (e.g., DO Spaces).

Env/flags:
  ENDPOINT  S3 endpoint (default: $ENDPOINT)
  BUCKET    Bucket/Space name (default: $BUCKET)
  PROFILE   AWS CLI profile (default: $PROFILE)
  PREFIX    Key prefix filter
  MODE      'complete' (default) or 'abort'
  DRY_RUN   1 to preview, 0 to execute (default: 0)
  MAX       Max uploads to process (0 = all)
EOF
}

# ---------- Parse flags ----------
while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0;;
    --endpoint) ENDPOINT="$2"; shift 2;;
    --bucket)   BUCKET="$2"; shift 2;;
    --profile)  PROFILE="$2"; shift 2;;
    --prefix)   PREFIX="$2"; shift 2;;
    --mode)     MODE="$2"; shift 2;;
    --dry-run)  DRY_RUN=1; shift 1;;
    --max)      MAX="$2"; shift 2;;
    *) echo "Unknown arg: $1"; usage; exit 1;;
  esac
done

# ---------- Prereqs ----------
command -v aws >/dev/null || { echo "aws CLI not found"; exit 1; }
command -v jq  >/dev/null || { echo "jq not found"; exit 1; }

aws_do() {
  if [ -n "${PROFILE:-}" ]; then
    aws --profile "$PROFILE" --endpoint-url "$ENDPOINT" "$@"
  else
    aws --endpoint-url "$ENDPOINT" "$@"
  fi
}

echo "Endpoint : $ENDPOINT"
echo "Bucket   : $BUCKET"
echo "Profile  : ${PROFILE:-<default>}"
echo "Prefix   : ${PREFIX:-<none>}"
echo "Mode     : $MODE"
echo "Dry-run  : $DRY_RUN"
echo "Max      : $MAX"
echo

if [ "$DRY_RUN" -ne 1 ]; then
  printf "Proceed to %s multipart uploads on bucket '%s'? [y/N] " "$MODE" "$BUCKET"
  read ans
  case "$ans" in
    y|Y) ;;
    *) echo "Aborted."; exit 0;;
  esac
fi

# ---------- List multipart uploads ----------
echo "Listing in-progress multipart uploads..."
if [ -n "$PREFIX" ]; then
  uploads_json="$(aws_do s3api list-multipart-uploads --bucket "$BUCKET" --prefix "$PREFIX" --output json || true)"
else
  uploads_json="$(aws_do s3api list-multipart-uploads --bucket "$BUCKET" --output json || true)"
fi

# Create a temp file with tab-separated (Key, UploadId, Initiated)
tmp="$(mktemp)"
printf '%s' "$uploads_json" | jq -r '.Uploads[]? | [.Key, .UploadId, .Initiated] | @tsv' > "$tmp"

count_total=$(wc -l < "$tmp" | tr -d ' ')
if [ "$count_total" -eq 0 ]; then
  echo "No in-progress multipart uploads found."
  rm -f "$tmp"
  exit 0
fi
echo "Found $count_total upload(s) in progress."

processed=0
errors=0

while IFS=$'\t' read -r KEY UPLOAD_ID INITIATED; do
  [ -n "${KEY:-}" ] || continue
  processed=$((processed + 1))
  if [ "$MAX" -gt 0 ] && [ "$processed" -gt "$MAX" ]; then
    break
  fi

  echo
  echo "[$processed/$count_total] Key='$KEY'"
  echo "  UploadId = $UPLOAD_ID"
  echo "  Initiated= $INITIATED"

  if [ "$MODE" = "abort" ]; then
    echo "  Action   = ABORT"
    if [ "$DRY_RUN" -eq 1 ]; then
      echo "  (dry-run) would abort"
      continue
    fi
    if aws_do s3api abort-multipart-upload --bucket "$BUCKET" --key "$KEY" --upload-id "$UPLOAD_ID" >/dev/null; then
      echo "  Aborted."
    else
      echo "  ERROR: abort failed." >&2
      errors=$((errors + 1))
    fi
    continue
  fi

  # MODE=complete
  echo "  Action   = COMPLETE"

  parts_json="$(aws_do s3api list-parts --bucket "$BUCKET" --key "$KEY" --upload-id "$UPLOAD_ID" --output json || true)"
  part_count="$(printf '%s' "$parts_json" | jq -r '.Parts | length // 0')"
  echo "  Parts    = $part_count"

  if [ "$part_count" -eq 0 ]; then
    echo "  WARNING: no parts found; skipping (consider --mode abort)."
    errors=$((errors + 1))
    continue
  fi

  completion_body="$(printf '%s' "$parts_json" | jq -c '{Parts: ( [ .Parts[]? | {ETag: .ETag, PartNumber: .PartNumber} ] | sort_by(.PartNumber) )}')"
  echo "  Completion body: $completion_body"

  if [ "$DRY_RUN" -eq 1 ]; then
    echo "  (dry-run) would complete"
    continue
  fi

  if aws_do s3api complete-multipart-upload \
        --bucket "$BUCKET" \
        --key "$KEY" \
        --upload-id "$UPLOAD_ID" \
        --multipart-upload "$completion_body" >/dev/null; then
    echo "  Completed."
  else
    echo "  ERROR: completion failed." >&2
    errors=$((errors + 1))
  fi
done < "$tmp"

rm -f "$tmp"

echo
echo "Done. processed=$processed errors=$errors"
[ "$errors" -gt 0 ] && exit 2 || exit 0
