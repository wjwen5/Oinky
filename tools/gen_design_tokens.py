#!/usr/bin/env python3
"""Generate app/src/main/java/com/oinky/app/ui/theme/DesignTokens.kt from DESIGN.md.

DESIGN.md is the source of truth for colours, type, radii and spacing. After editing it, run:

    npx -p @google/design.md designmd lint DESIGN.md
    python3 tools/gen_design_tokens.py
"""
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "DESIGN.md"
OUT = ROOT / "app/src/main/java/com/oinky/app/ui/theme/DesignTokens.kt"


def camel(name: str) -> str:
    parts = re.split(r"[-_ ]", name)
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def color(value: str) -> str:
    h = value.strip().lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    if len(h) == 6:
        h = "FF" + h
    elif len(h) == 8:  # CSS #RRGGBBAA -> Android AARRGGBB
        h = h[6:] + h[:6]
    else:
        sys.exit(f"Unsupported colour {value!r}; use hex in DESIGN.md")
    return f"Color(0x{h.upper()})"


def dim(value, unit: str) -> str:
    """px -> dp/sp (1 CSS px == 1 dp on Android), em stays em."""
    s = str(value).strip()
    if s.endswith("em") and not s.endswith("rem"):
        return f"({float(s[:-2])}).em"
    if s.endswith("rem"):
        return f"{float(s[:-3]) * 16:g}.{unit}"
    if s.endswith("px"):
        return f"{float(s[:-2]):g}.{unit}"
    return f"{float(s):g}.{unit}"


def main() -> None:
    text = SRC.read_text(encoding="utf-8")
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        sys.exit("DESIGN.md has no YAML front matter")
    tokens = yaml.safe_load(m.group(1))

    out = [
        "// GENERATED from DESIGN.md by tools/gen_design_tokens.py. Do not edit by hand.",
        "@file:Suppress(\"MemberVisibilityCanBePrivate\", \"unused\")",
        "",
        "package com.oinky.app.ui.theme",
        "",
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.text.TextStyle",
        "import androidx.compose.ui.text.font.FontWeight",
        "import androidx.compose.ui.unit.dp",
        "import androidx.compose.ui.unit.em",
        "import androidx.compose.ui.unit.sp",
        "",
        f"/** Design tokens for \"{tokens['name']}\". */",
        "object Tokens {",
        "    object Colors {",
    ]
    for name, value in tokens.get("colors", {}).items():
        out.append(f"        val {camel(name)} = {color(value)}")
    out += ["    }", "", "    object Type {"]
    for name, t in tokens.get("typography", {}).items():
        args = [f"fontFamily = FontFamilies.{t['fontFamily'].replace(' ', '')}"]
        if "fontSize" in t:
            args.append(f"fontSize = {dim(t['fontSize'], 'sp')}")
        if "fontWeight" in t:
            args.append(f"fontWeight = FontWeight({int(t['fontWeight'])})")
        if "lineHeight" in t:
            lh = t["lineHeight"]
            # Unitless line height is a multiple of the font size.
            args.append(f"lineHeight = {dim(lh, 'sp')}" if isinstance(lh, str) else f"lineHeight = ({lh}).em")
        if "letterSpacing" in t:
            args.append(f"letterSpacing = {dim(t['letterSpacing'], 'sp')}")
        if "fontFeature" in t:
            feature = str(t["fontFeature"]).replace('"', "")
            args.append(f'fontFeatureSettings = "{feature}"')
        out.append(f"        val {camel(name)} = TextStyle(" + ", ".join(args) + ")")
    out += ["    }", "", "    object Radius {"]
    for name, value in tokens.get("rounded", {}).items():
        out.append(f"        val {camel(name)} = {dim(value, 'dp')}")
    out += ["    }", "", "    object Space {"]
    for name, value in tokens.get("spacing", {}).items():
        out.append(f"        val {camel(name)} = {dim(value, 'dp')}")
    out += ["    }", "}", ""]

    OUT.write_text("\n".join(out), encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
