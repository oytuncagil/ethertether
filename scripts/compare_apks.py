#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
"""Checks that two APKs have the same contents, ignoring their signatures.

Release APKs are signed with the v2 and v3 schemes only, which live in the APK signing block,
outside the zip entries. So an official APK and an unsigned APK built from the same tag are the
same build exactly when every zip entry (name, order and bytes) is the same. See
docs/RELEASING.md, "Verify a release yourself".

Usage: python3 scripts/compare_apks.py OFFICIAL.apk YOUR-BUILD.apk   (exit code 1 on a difference)
"""

import sys
import zipfile

# v1 (JAR) signature files; release APKs have none, but an APK signed by other tools might.
SIGNATURE_FILES = ("META-INF/MANIFEST.MF",)
SIGNATURE_SUFFIXES = (".SF", ".RSA", ".DSA", ".EC")


def entries(path: str) -> list[tuple[str, bytes]]:
    """(name, bytes) of every zip entry in order, without v1 signature files."""
    with zipfile.ZipFile(path) as apk:
        return [
            (info.filename, apk.read(info))
            for info in apk.infolist()
            if not (info.filename in SIGNATURE_FILES or (info.filename.startswith("META-INF/") and info.filename.endswith(SIGNATURE_SUFFIXES)))
        ]


def main() -> int:
    first, second = entries(sys.argv[1]), entries(sys.argv[2])
    names_first, names_second = [n for n, _ in first], [n for n, _ in second]
    if names_first != names_second:
        print("different files or order:")
        for name in sorted(set(names_first) ^ set(names_second)):
            print(f"  only in one APK: {name}")
        return 1
    different = [name for (name, a), (_, b) in zip(first, second) if a != b]
    for name in different:
        print(f"different: {name}")
    if different:
        return 1
    print(f"same contents: {len(first)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
