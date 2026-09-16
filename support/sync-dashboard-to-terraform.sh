#!/usr/bin/env bash
# Syncs dashboard-kql/ to the cp-amp-terraform-az-dashboard queries/pcr-data/ folder.
# Run from anywhere — uses the script's own location to find both repos.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$SCRIPT_DIR/dashboard-kql"
TF_REPO="$(cd "$SCRIPT_DIR/../../cp-amp-terraform-az-dashboard" 2>/dev/null && pwd)"

if [ -z "$TF_REPO" ]; then
  echo "ERROR: cp-amp-terraform-az-dashboard repo not found alongside this repo"
  exit 1
fi

DEST="$TF_REPO/queries/pcr-data"
mkdir -p "$DEST"

echo "Syncing:"
echo "  from: $SRC"
echo "  to:   $DEST"
echo ""

CHANGES=0

for f in "$SRC"/*.kql; do
  name=$(basename "$f")
  if [ -f "$DEST/$name" ]; then
    if diff -q "$f" "$DEST/$name" > /dev/null 2>&1; then
      echo "  unchanged: $name"
    else
      cp "$f" "$DEST/$name"
      echo "  updated:   $name"
      CHANGES=$((CHANGES + 1))
    fi
  else
    cp "$f" "$DEST/$name"
    echo "  added:     $name"
    CHANGES=$((CHANGES + 1))
  fi
done

echo ""
if [ "$CHANGES" -gt 0 ]; then
  echo "Done — $CHANGES file(s) changed. Next steps:"
  echo "  1. Review changes in $TF_REPO"
  echo "  2. Commit and push to a branch in cp-amp-terraform-az-dashboard"
  echo "  3. Raise a PR and get it merged"
  echo "  4. Run the Terraform pipeline to apply changes to Azure"
else
  echo "Done — nothing to sync, all files up to date."
fi
