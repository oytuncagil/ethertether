#!/usr/bin/env sh
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# Tests for check_dco.sh: builds throwaway repositories and checks that the script accepts or
# rejects them. Run: sh scripts/test_check_dco.sh
set -eu

# The caller's git settings (author overrides, signing, hooks, an outer GIT_DIR) must not change
# the results or touch another repository.
unset CDPATH GIT_DIR GIT_WORK_TREE GIT_INDEX_FILE PR_AUTHOR
unset GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

check="$(cd "$(dirname "$0")" && pwd)/check_dco.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
trap 'exit 1' HUP INT TERM
failures=0
dependabot="49699333+dependabot[bot]@users.noreply.github.com"
# A Dependabot commit message ends like this.
dependabot_message="deps: bump something

Signed-off-by: dependabot[bot] <support@github.com>"
signed="Change

Signed-off-by: Dev <dev@example.com>"

# new_repo NAME: a repository with one base commit; sets $repo and $base
new_repo() {
    repo="$work/$(printf '%s' "$1" | tr -c 'a-zA-Z0-9' '_')"
    git init -q "$repo"
    commit base@example.com base
    base=$(git -C "$repo" rev-parse HEAD)
}

# commit EMAIL MESSAGE: a commit by EMAIL in $repo (also finishes a merge in progress)
commit() {
    git -C "$repo" -c user.name=author -c user.email="$1" commit -q --allow-empty -m "$2"
}

# side_branch: a branch "side" from the base with one signed commit, and a merge of it left uncommitted
side_branch() {
    git -C "$repo" checkout -q -b side "$base"
    commit dev@example.com "$signed"
    git -C "$repo" checkout -q -
    git -C "$repo" -c user.name=author -c user.email=dev@example.com merge -q --no-ff --no-commit side >/dev/null 2>&1
}

# verify PASS|FAIL NAME PR_AUTHOR [BASE]: runs check_dco.sh on BASE..HEAD of $repo; an empty
# PR_AUTHOR leaves it unset, as on a local run
verify() {
    if (
        cd "$repo"
        if [ -n "$3" ]; then export PR_AUTHOR="$3"; fi
        sh "$check" "${4:-$base}" HEAD >/dev/null 2>&1
    ); then got=PASS; else got=FAIL; fi
    if [ "$got" = "$1" ]; then
        echo "ok   $2"
    else
        echo "FAIL $2 (expected $1, got $got)"
        failures=1
    fi
}

# expect PASS|FAIL NAME PR_AUTHOR AUTHOR_EMAIL MESSAGE: one commit on top of the base
expect() {
    new_repo "$2"
    commit "$4" "$5"
    verify "$1" "$2" "$3"
}

expect PASS "signed off by the author" "someone" "dev@example.com" "$signed"
expect FAIL "no sign-off" "someone" "dev@example.com" "Change"
expect FAIL "sign-off by another address" "someone" "dev@example.com" "Change

Signed-off-by: Other <other@example.com>"
expect FAIL "bot-like address on a contributor's pull request" "someone" "helper[bot]@users.noreply.github.com" "Change"
expect FAIL "Dependabot's commit on a contributor's pull request" "someone" "$dependabot" "$dependabot_message"
expect FAIL "Dependabot's commit outside a pull request" "" "$dependabot" "$dependabot_message"
expect PASS "Dependabot's commit on its own pull request" "dependabot[bot]" "$dependabot" "$dependabot_message"
expect FAIL "unsigned human commit on a Dependabot pull request" "dependabot[bot]" "dev@example.com" "Change"
expect FAIL "another bot's address on a Dependabot pull request" "dependabot[bot]" "helper[bot]@users.noreply.github.com" "Change"

new_repo "signed commit on top of Dependabot's"
commit "$dependabot" "$dependabot_message"
commit dev@example.com "$signed"
verify PASS "signed commit on top of Dependabot's" "dependabot[bot]"

new_repo "one unsigned commit among signed ones"
commit dev@example.com "$signed"
commit dev@example.com "Change"
commit dev@example.com "$signed"
verify FAIL "one unsigned commit among signed ones" "someone"

new_repo "merge that only joins two branches"
side_branch
commit dev@example.com "Merge branch 'side'"
verify PASS "merge that only joins two branches" "someone"

new_repo "unsigned merge that adds a change"
side_branch
printf 'new\n' >"$repo/added.txt"
git -C "$repo" add added.txt
commit dev@example.com "Merge branch 'side'"
verify FAIL "unsigned merge that adds a change" "someone"

new_repo "signed merge that adds a change"
side_branch
printf 'new\n' >"$repo/added.txt"
git -C "$repo" add added.txt
commit dev@example.com "Merge branch 'side'

Signed-off-by: Dev <dev@example.com>"
verify PASS "signed merge that adds a change" "someone"

new_repo "unknown base commit"
commit dev@example.com "$signed"
verify FAIL "unknown base commit" "someone" 0123456789012345678901234567890123456789

exit "$failures"
