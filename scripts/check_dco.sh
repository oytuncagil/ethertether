#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Fails when a commit in BASE..HEAD has no "Signed-off-by:" line from its author
# (Developer Certificate of Origin 1.1, https://developercertificate.org/).
# Usage: [PR_AUTHOR=login] scripts/check_dco.sh BASE_SHA HEAD_SHA
#
# A merge commit needs no sign-off only when its tree is exactly what git's own merge of its two
# parents gives, without conflicts (as with GitHub's "Update branch"). One that resolves a conflict,
# adds a change or joins more than two branches is checked like any other commit. This needs
# git 2.38 or newer (git merge-tree --write-tree); with an older git every merge needs a sign-off.
#
# Dependabot signs off as <support@github.com>, which is not its author address, and its updates
# are generated, not contributed. Its commits are exempt only on a pull request Dependabot opened
# itself (PR_AUTHOR is the login GitHub gives for the pull request, not anything in the commits) and
# only with Dependabot's exact author address. Anyone can put a bot-like address into a commit, so
# the address alone is never enough. CI runs a pull request's own copy of this script and of
# ci.yml, so a pull request that changes them needs a careful read.
set -eu

base="$1"
head="$2"
pr_author="${PR_AUTHOR:-}"
dependabot_email="49699333+dependabot[bot]@users.noreply.github.com"
missing=0

# clean_merge COMMIT: true when COMMIT joins two parents and holds nothing but their automatic merge
clean_merge() {
    git rev-parse --quiet --verify "$1^2" >/dev/null || return 1
    if git rev-parse --quiet --verify "$1^3" >/dev/null; then return 1; fi
    auto=$(git merge-tree --write-tree --no-messages "$1^1" "$1^2" 2>/dev/null) || return 1
    [ "$auto" = "$(git rev-parse "$1^{tree}")" ]
}

for rev in "$base" "$head"; do
    if ! git rev-parse --quiet --verify "$rev^{commit}" >/dev/null; then
        printf "Unknown commit: '%s'\n" "$rev"
        exit 2
    fi
done
commits=$(git rev-list "$base..$head")

for commit in $commits; do
    summary=$(git log -1 --format='%h %s' "$commit")
    if clean_merge "$commit"; then
        printf 'merge   %s\n' "$summary"
        continue
    fi
    author_email=$(git log -1 --format='%ae' "$commit")
    if [ "$pr_author" = "dependabot[bot]" ] && [ "$author_email" = "$dependabot_email" ]; then
        printf 'bot     %s\n' "$summary"
        continue
    fi
    if git log -1 --format='%(trailers:key=Signed-off-by,valueonly)' "$commit" | grep -qiF "<$author_email>"; then
        printf 'ok      %s\n' "$summary"
    else
        printf 'MISSING %s (needs Signed-off-by with <%s>)\n' "$summary" "$author_email"
        missing=1
    fi
done

if [ "$missing" -ne 0 ]; then
    echo "Sign off each commit with: git commit -s, or the whole branch with: git rebase --signoff main (see CONTRIBUTING.md)"
fi
exit "$missing"
