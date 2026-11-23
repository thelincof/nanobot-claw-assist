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
    text = path.read_text()
    if replacement[0] in text:
        return
    lines = text.splitlines(keepends=True)
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
        "ln -sf /opt/clawd/bin/clawd /usr/bin/clawd",
        "mkdir -p /usr/share/applications 2>/dev/null || true",
        "install -m 644 /opt/clawd/lib/clawd-clawd.desktop /usr/share/applications/clawd.desktop 2>/dev/null || true",
        "if command -v update-desktop-database >/dev/null 2>&1; then",
        "    update-desktop-database /usr/share/applications >/dev/null 2>&1 || true",
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
        "rm -f /usr/bin/clawd /usr/share/applications/clawd.desktop",
        "if command -v update-desktop-database >/dev/null 2>&1; then",
        "    update-desktop-database /usr/share/applications >/dev/null 2>&1 || true",
        "fi",
    ],
)
PY

desktop="$work/root/opt/clawd/lib/clawd-clawd.desktop"
if [[ -f "$desktop" ]]; then
    sed -i \
        -e 's/^Name=clawd$/Name=Clawd/' \
        -e 's/^Categories=.*/Categories=Network;/' \
        "$desktop"
fi

sh -n "$work/root/DEBIAN/postinst"
sh -n "$work/root/DEBIAN/prerm"
dpkg-deb --root-owner-group -b "$work/root" "$deb" >/dev/null
echo "patched: $deb"
