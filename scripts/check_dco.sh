#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Fails when a commit in BASE..HEAD has no "Signed-off-by:" line from its author
# (Developer Certificate of Origin 1.1, https://developercertificate.org/).
# Usage: scripts/check_dco.sh BASE_SHA HEAD_SHA
set -eu

base="$1"
head="$2"
missing=0

for commit in $(git rev-list --no-merges "$base..$head"); do
    author_email=$(git log -1 --format='%ae' "$commit")
    # Dependency bots cannot sign off; their changes are generated, not contributed.
    case "$author_email" in
        *"[bot]@users.noreply.github.com")
            echo "bot     $(git log -1 --format='%h %s' "$commit")"
            continue
            ;;
    esac
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
