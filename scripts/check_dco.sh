#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Fails when a commit in BASE..HEAD has no "Signed-off-by:" line from its author
# (Developer Certificate of Origin 1.1, https://developercertificate.org/).
# Usage: [PR_AUTHOR=login] scripts/check_dco.sh BASE_SHA HEAD_SHA
#
# Dependabot cannot sign off; its updates are generated, not contributed. Its commits are exempt
# only on a pull request Dependabot opened itself (PR_AUTHOR comes from GitHub and cannot be set by
# a contributor) and only with Dependabot's exact author address. Anyone can put a bot-like address
# into a commit, so the address alone is never enough.
set -eu

base="$1"
head="$2"
pr_author="${PR_AUTHOR:-}"
dependabot_email="49699333+dependabot[bot]@users.noreply.github.com"
missing=0

for commit in $(git rev-list --no-merges "$base..$head"); do
    author_email=$(git log -1 --format='%ae' "$commit")
    if [ "$pr_author" = "dependabot[bot]" ] && [ "$author_email" = "$dependabot_email" ]; then
        echo "bot     $(git log -1 --format='%h %s' "$commit")"
        continue
    fi
    if git log -1 --format='%(trailers:key=Signed-off-by,valueonly)' "$commit" | grep -qiF "<$author_email>"; then
        echo "ok      $(git log -1 --format='%h %s' "$commit")"
    else
        echo "MISSING $(git log -1 --format='%h %s' "$commit") (needs Signed-off-by with <$author_email>)"
        missing=1
    fi
done

if [ "$missing" -ne 0 ]; then
    echo "Sign off each commit with: git commit -s (see CONTRIBUTING.md)"
fi
exit "$missing"
