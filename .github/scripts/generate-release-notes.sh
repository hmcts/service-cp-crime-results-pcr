#!/usr/bin/env bash
# Deterministic changelog for Auto-Release (design doc §6). The /release skill's LLM-mediated,
# human-confirmed changelog can't run inside a CI job as-is - this re-implements its
# dependabot/chore/docs/bump-X-from-Y-to-Z filter mechanically, without the prose synthesis step.
set -euo pipefail

LAST_TAG=$(gh release list --limit 1 --json tagName --jq '.[0].tagName // empty')

if [ -z "$LAST_TAG" ]; then
  NEXT_VERSION="v0.0.1"
  PR_LIST=$(gh pr list --state merged --limit 50 --json number,title,url,author \
    --jq '.[] | select(.author.login != "dependabot[bot]" and .author.login != "renovate[bot]")')
else
  PUBLISHED_AT=$(gh release view "$LAST_TAG" --json publishedAt --jq '.publishedAt')
  MAJOR=$(echo "${LAST_TAG#v}" | cut -d. -f1)
  MINOR=$(echo "${LAST_TAG#v}" | cut -d. -f2)
  PATCH=$(echo "${LAST_TAG#v}" | cut -d. -f3)
  NEXT_VERSION="v${MAJOR}.${MINOR}.$((PATCH + 1))"
  PR_LIST=$(gh pr list --state merged --limit 50 --json number,title,url,mergedAt,author \
    --jq --arg since "$PUBLISHED_AT" \
    '.[] | select(.mergedAt > $since) | select(.author.login != "dependabot[bot]" and .author.login != "renovate[bot]")')
fi

FILTERED=$(echo "$PR_LIST" | jq -s '[.[] | select(
  (.title | test("^chore(\\(.*\\))?:"; "i") | not) and
  (.title | test("^docs(\\(.*\\))?:"; "i") | not) and
  (.title | test("^ci(\\(.*\\))?:"; "i") | not) and
  (.title | test("bump .* from .* to "; "i") | not)
)]')

echo "version=${NEXT_VERSION}" >> "$GITHUB_OUTPUT"

COUNT=$(echo "$FILTERED" | jq 'length')
if [ "$COUNT" -eq 0 ]; then
  echo "No functional changes found since ${LAST_TAG:-the initial release}. Nothing to release."
  echo "has_changes=false" >> "$GITHUB_OUTPUT"
  exit 0
fi

echo "has_changes=true" >> "$GITHUB_OUTPUT"

{
  echo "## What's changed in ${NEXT_VERSION}"
  echo
  echo "$FILTERED" | jq -r '.[] | "- #\(.number) \(.title) (\(.url))"'
} > release-notes.md
