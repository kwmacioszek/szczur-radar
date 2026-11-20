#!/usr/bin/env python3
"""Builds the published gallery: index.html with every screenshot embedded as a JPEG data URI.

The repository copy of the gallery reads the PNG files next to it; a viewer that cannot load sibling files
(seen on 2026-11-20) shows no screenshots, so the Artifact gets this self-contained build instead.
Usage (macOS, needs `sips`): python3 context/changes/ui-design-system/gallery/inline.py <output.html>
The screenshots are scaled down (current layout to 1920 px, earlier variants to 1100 px) to fit the 16 MB page limit.
"""
import base64
import json
import pathlib
import re
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SHOTS = HERE.parent / "directions"


def jpeg(png: pathlib.Path, max_side: int | None) -> bytes:
    with tempfile.TemporaryDirectory() as tmp:
        out = pathlib.Path(tmp) / "shot.jpg"
        args = ["sips", "-s", "format", "jpeg", "-s", "formatOptions", "82"]
        if max_side:
            args += ["-Z", str(max_side)]
        subprocess.run([*args, str(png), "--out", str(out)], check=True, capture_output=True)
        return out.read_bytes()


def main() -> None:
    page = (HERE / "index.html").read_text()
    paths = re.findall(r"'([a-e]/[a-z-]+-(?:light|dark)-\d+\.png)'", page)
    data = {}
    for path in dict.fromkeys(paths):
        current = path.startswith("e/")
        body = jpeg(SHOTS / path, 1920 if current else 1100)
        data[path] = "data:image/jpeg;base64," + base64.b64encode(body).decode()
    marker = "<script>\n  // Every screenshot of the variants"
    assert page.count(marker) == 1
    built = page.replace(marker, "<script>window.GALLERY_INLINE = " + json.dumps(data) + "</script>\n" + marker)
    pathlib.Path(sys.argv[1]).write_text(built)
    print(f"{len(data)} screenshots embedded, {len(built) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
