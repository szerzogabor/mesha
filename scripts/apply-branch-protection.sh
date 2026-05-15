#!/usr/bin/env bash
# Applies branch protection rules to the main branch.
# Requirements: gh CLI authenticated with a token that has admin access to the repo.
# Usage: ./scripts/apply-branch-protection.sh [owner/repo]

set -euo pipefail

REPO="${1:-szerzogabor/mesha}"

echo "Applying branch protection rules to ${REPO} main..."

gh api "repos/${REPO}/branches/main/protection" \
  --method PUT \
  --header "Accept: application/vnd.github+json" \
  --input - <<'EOF'
{
  "required_status_checks": {
    "strict": false,
    "contexts": ["build"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

echo "Done. Branch protection rules are now active on main:"
echo "  - Require pull request (1 approving review)"
echo "  - Require CI status check: build"
echo "  - Enforce for admins"
echo "  - Block force pushes and deletions"
