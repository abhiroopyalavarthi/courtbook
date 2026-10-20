#!/usr/bin/env bash
# Creates one GitHub issue per user story in docs/backlog.md.
# Needs the GitHub CLI, logged in: gh auth login
#   ./scripts/create-issues.sh
set -euo pipefail
cd "$(dirname "$0")/.."

gh label create story --color 0E8A16 --description "User story" --force >/dev/null

# Table rows look like: | 7 | As a member, ... | Done when ... |
grep -E '^\| [0-9]+ \|' docs/backlog.md | while IFS='|' read -r _ num story done _; do
  story=$(echo "$story" | sed 's/^ *//; s/ *$//')
  done=$(echo "$done" | sed 's/^ *//; s/ *$//')
  title=$(echo "$story" | sed -E 's/^As (a|an|the) [^,]+, (I want|we want) (to )?//; s/ so that.*//; s/\.$//')
  title="$(echo "${title:0:1}" | tr '[:lower:]' '[:upper:]')${title:1}"
  body=$(printf '%s\n\n**Done when:** %s\n\n**Estimate:** _ points\n' "$story" "$done")
  gh issue create --title "$title" --body "$body" --label story >/dev/null
  echo "Created #$num: $title"
done
