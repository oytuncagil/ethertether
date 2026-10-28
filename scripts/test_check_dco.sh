#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Tests for check_dco.sh: builds throwaway repositories with one commit each and checks that the
# script accepts or rejects them. Run: sh scripts/test_check_dco.sh
set -eu

check="$(cd "$(dirname "$0")" && pwd)/check_dco.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0
dependabot="49699333+dependabot[bot]@users.noreply.github.com"

# expect PASS|FAIL "name" PR_AUTHOR AUTHOR_EMAIL MESSAGE
expect() {
    want="$1" name="$2" pr_author="$3" email="$4" message="$5"
    repo="$work/$(printf '%s' "$name" | tr -c 'a-zA-Z0-9' '_')"
    git init -q "$repo"
    git -C "$repo" -c user.name=base -c user.email=base@example.com commit -q --allow-empty -m base
    git -C "$repo" -c user.name=author -c user.email="$email" commit -q --allow-empty -m "$message"
    if (cd "$repo" && PR_AUTHOR="$pr_author" sh "$check" HEAD~1 HEAD >/dev/null); then got=PASS; else got=FAIL; fi
    if [ "$got" = "$want" ]; then
        echo "ok   $name"
    else
        echo "FAIL $name (expected $want, got $got)"
        failures=1
    fi
}

expect PASS "signed off by the author" "someone" "dev@example.com" "Change

Signed-off-by: Dev <dev@example.com>"
expect FAIL "no sign-off" "someone" "dev@example.com" "Change"
expect FAIL "sign-off by another address" "someone" "dev@example.com" "Change

Signed-off-by: Other <other@example.com>"
expect FAIL "bot-like address on a contributor's pull request" "someone" "helper[bot]@users.noreply.github.com" "Change"
expect FAIL "Dependabot's address on a contributor's pull request" "someone" "$dependabot" "Change"
expect PASS "Dependabot's commit on its own pull request" "dependabot[bot]" "$dependabot" "deps: bump something"
expect FAIL "unsigned human commit on a Dependabot pull request" "dependabot[bot]" "dev@example.com" "Change"
expect FAIL "another bot's address on a Dependabot pull request" "dependabot[bot]" "helper[bot]@users.noreply.github.com" "Change"

exit "$failures"
