#!/usr/bin/env python3
"""Builds the published gallery: index.html with every screenshot embedded as a JPEG data URI.

The repository copy of the gallery reads the PNG files next to it; a viewer that cannot load sibling files
(seen on 2026-11-20) shows no screenshots, so the Artifact gets this self-contained build instead.
Usage (macOS, needs `sips`): python3 context/changes/ui-design-system/gallery/inline.py <output.html>
The screens of the design are embedded as PNG; the earlier variants are scaled down to 1100 px JPEG to fit the 16 MB page limit.
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
DESIGN = HERE.parents[2] / "foundation" / "ui-design"


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
    paths = re.findall(r"'((?:[a-e]|screens)/[a-z0-9-]+-(?:light|dark)-\d+\.png)'", page)
    data = {}
    for path in dict.fromkeys(paths):
        if path.startswith("screens/"):
            # The screens of the design are already small palette PNG files: embedded as they are.
            data[path] = "data:image/png;base64," + base64.b64encode((DESIGN / path).read_bytes()).decode()
        else:
            data[path] = "data:image/jpeg;base64," + base64.b64encode(jpeg(SHOTS / path, 1100)).decode()
    marker = "<script>\n  // Every screenshot of the variants"
    assert page.count(marker) == 1
    built = page.replace(marker, "<script>window.GALLERY_INLINE = " + json.dumps(data) + "</script>\n" + marker)
    pathlib.Path(sys.argv[1]).write_text(built)
    print(f"{len(data)} screenshots embedded, {len(built) / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
