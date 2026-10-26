#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
"""Prints the release notes for one version from CHANGELOG.md.

Takes the section "## [X.Y.Z]" if it exists, otherwise "## [Unreleased]" (for test tags made
before the version has its own section). Fails when neither exists or the section is empty.

Usage: python3 scripts/release_notes.py X.Y.Z [CHANGELOG.md]
"""

import pathlib
import re
import sys


def section(text: str, title: str) -> str | None:
    """The body under '## [title]' up to the next '## ' heading, or None."""
    match = re.search(rf"^## \[{re.escape(title)}\][^\n]*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    return match.group(1).strip() if match else None


def main() -> int:
    version = sys.argv[1]
    path = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md")
    text = path.read_text(encoding="utf-8")
    body = section(text, version) or section(text, "Unreleased")
    if not body:
        print(f"no changelog section for {version} or Unreleased", file=sys.stderr)
        return 1
    print(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
