#!/usr/bin/env bash
# jpackage creates a postinst that runs xdg-desktop-menu under `set -e`.
# On systems without a writable /usr/share/applications this fails the whole
# package configuration. Make shortcut registration best-effort and repack.
set -euo pipefail

deb="${1:?usage: patch-deb.sh <path-to-deb>}"
[[ -f "$deb" ]] || { echo "not a file: $deb" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

dpkg-deb -R "$deb" "$work/root"

python3 - "$work/root/DEBIAN/postinst" "$work/root/DEBIAN/prerm" <<'PY'
import pathlib
import sys

postinst = pathlib.Path(sys.argv[1])
prerm = pathlib.Path(sys.argv[2])

def patch(path: pathlib.Path, marker: str, replacement: list[str]) -> None:
    if not path.exists():
        return
    lines = path.read_text().splitlines(keepends=True)
    out: list[str] = []
    for line in lines:
        if marker in line:
            indent = line[: len(line) - len(line.lstrip())]
            out.extend(indent + part + "\n" for part in replacement)
        else:
            out.append(line)
    path.write_text("".join(out))

patch(
    postinst,
    "xdg-desktop-menu install",
    [
        "mkdir -p /usr/share/applications 2>/dev/null || true",
        "if command -v xdg-desktop-menu >/dev/null 2>&1; then",
        "    xdg-desktop-menu install /opt/clawd/lib/clawd-clawd.desktop || true",
        "fi",
    ],
)
patch(
    prerm,
    "xdg-desktop-menu uninstall",
    [
        "if command -v xdg-desktop-menu >/dev/null 2>&1; then",
        "    xdg-desktop-menu uninstall /opt/clawd/lib/clawd-clawd.desktop || true",
        "fi",
    ],
)
PY

sh -n "$work/root/DEBIAN/postinst"
sh -n "$work/root/DEBIAN/prerm"
dpkg-deb --root-owner-group -b "$work/root" "$deb" >/dev/null
echo "patched: $deb"
