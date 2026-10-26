#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
"""Repository hygiene checks, run in CI and before every push.

Checks the files git tracks (or would track) for:
  * signing material, secrets and machine-local files that must never be committed;
  * em dashes (project style rule);
  * CRLF line endings outside Windows scripts;
  * a missing SPDX license header in source and build files.

Usage: python3 scripts/check_repo.py   (exit code 1 on any finding)
"""

import pathlib
import re
import subprocess
import sys

FORBIDDEN_NAMES = re.compile(
    r"(^|/)(local\.properties|keystore\.properties|signing\.properties|EtherTether_[^/]*\.md|\.env)$"
    r"|\.(jks|keystore|p12|pfx|pem|key)$",
    re.IGNORECASE,
)
SECRET_PATTERNS = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    # A literal password; reading it from the environment (providers.environmentVariable) is fine.
    re.compile(r"(store|key)Password\s*[=:]\s*(?!providers\.environmentVariable\()['\"]?[^\s'\"$]{4,}", re.IGNORECASE),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{36,}"),
]
EM_DASH = chr(0x2014)
SPDX_SUFFIXES = {".kt", ".kts", ".java", ".aidl", ".py", ".sh", ".pro", ".toml", ".yml", ".yaml"}
SPDX_XML_DIRS = ("/src/",)
BINARY_SUFFIXES = {".jar", ".png", ".webp", ".jpg", ".jpeg", ".zip", ".apk", ".gif", ".ico"}
SPDX_EXEMPT = {"gradle/wrapper/gradle-wrapper.properties", "REUSE.toml"}
SELF = "scripts/check_repo.py"
# Built from two parts so this file does not itself look like a license tag to REUSE.
SPDX_TAG = "SPDX-" + "License-Identifier:"


def tracked_files() -> list[str]:
    """Files in the index plus untracked files that are not ignored."""
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        check=True, capture_output=True, text=True,
    ).stdout
    return sorted({line for line in out.splitlines() if line and pathlib.Path(line).is_file()})


def needs_spdx(path: str) -> bool:
    suffix = pathlib.PurePosixPath(path).suffix
    if path in SPDX_EXEMPT or path.startswith("gradlew"):
        return False
    if suffix in SPDX_SUFFIXES:
        return True
    return suffix == ".xml" and any(d in "/" + path for d in SPDX_XML_DIRS)


def check_file(path: str) -> list[str]:
    problems = []
    if FORBIDDEN_NAMES.search(path):
        problems.append("file must never be committed")
    if pathlib.PurePosixPath(path).suffix.lower() in BINARY_SUFFIXES:
        return problems
    raw = pathlib.Path(path).read_bytes()
    text = raw.decode("utf-8", errors="replace")
    if path != SELF:
        problems += [f"looks like a secret ({p.pattern[:30]}...)" for p in SECRET_PATTERNS if p.search(text)]
    if EM_DASH in text:
        problems.append("contains an em dash")
    if b"\r\n" in raw and not path.endswith((".bat", ".cmd")):
        problems.append("CRLF line endings")
    if needs_spdx(path) and SPDX_TAG not in text[:600]:
        problems.append("missing SPDX license header")
    return problems


def main() -> int:
    findings = [(f, p) for f in tracked_files() for p in check_file(f)]
    for path, problem in findings:
        print(f"{path}: {problem}")
    print(f"{len(findings)} problem(s) found" if findings else "Repository checks passed")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
