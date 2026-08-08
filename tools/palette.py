"""Generate M3 tonal ramps from the Jellyfin brand hues, and check the contrast of every
foreground/background pair Material defines."""

import colorsys

# Brand hues. Chroma is held at the source colour's saturation and only lightness moves, so every
# tone stays recognisably the same hue.
HUES = {
    # #9B59D0 -- the brand purple
    "primary": (276 / 360, 0.57),
    # #00A4DC -- the brand blue
    "secondary": (196 / 360, 1.00),
    # #3EA55F -- the green already used for "watched"
    "tertiary": (140 / 360, 0.45),
    # Material's baseline error red, matched by hue
    "error": (6 / 360, 0.72),
    # Pure neutral: the dark scheme's greys are untinted and the light one matches
    "neutral": (0.0, 0.0),
    "neutralVariant": (276 / 360, 0.06),
}

TONES = [0, 4, 6, 10, 12, 17, 20, 22, 24, 30, 40, 50, 60, 70, 80, 87, 90, 92, 94, 95, 96, 98, 100]


def tone(name, t):
    h, s = HUES[name]
    r, g, b = colorsys.hls_to_rgb(h, t / 100, s)
    return f"0xFF{round(r * 255):02X}{round(g * 255):02X}{round(b * 255):02X}"


def lum(hexstr):
    v = int(hexstr[2:], 16)
    out = 0.0
    for shift, k in ((16, 0.2126), (8, 0.7152), (0, 0.0722)):
        c = ((v >> shift) & 0xFF) / 255
        c = c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
        out += k * c
    return out


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


# The dark scheme's neutrals are fixed by the existing design rather than generated.
DARK_FIXED = {
    "background": "0xFF101010",
    "surface": "0xFF101010",
    "surfaceVariant": "0xFF242424",
    "surfaceContainerLowest": "0xFF0A0A0A",
    "surfaceContainerLow": "0xFF161616",
    "surfaceContainer": "0xFF1C1C1C",
    "surfaceContainerHigh": "0xFF242424",
    "surfaceContainerHighest": "0xFF2E2E2E",
    "surfaceDim": "0xFF0C0C0C",
    "surfaceBright": "0xFF383838",
}

LIGHT_FIXED = {
    "background": "0xFFFAF8FB",
    "surface": "0xFFFAF8FB",
    "surfaceVariant": "0xFFE8E2EC",
    "surfaceContainerLowest": "0xFFFFFFFF",
    "surfaceContainerLow": "0xFFF5F2F7",
    "surfaceContainer": "0xFFEFECF2",
    "surfaceContainerHigh": "0xFFE9E6EC",
    "surfaceContainerHighest": "0xFFE3E0E7",
    "surfaceDim": "0xFFDCD9E0",
    "surfaceBright": "0xFFFAF8FB",
}


def scheme(dark):
    fixed = DARK_FIXED if dark else LIGHT_FIXED
    if dark:
        acc = {"main": 70, "on": 20, "container": 30, "onContainer": 90}
    else:
        acc = {"main": 40, "on": 100, "container": 90, "onContainer": 10}

    # The blue and the green are far more saturated than the purple, so HSL lightness overstates
    # how dark they look: tone 40 of either lands around 3.4:1 on white. One step down each.
    light_main = {"secondary": 30, "tertiary": 30}

    s = {}
    for name in ("primary", "secondary", "tertiary", "error"):
        cap = name.capitalize() if name != "primary" else "Primary"
        main = acc["main"] if dark else light_main.get(name, acc["main"])
        s[name] = tone(name, main)
        s["on" + cap] = tone(name, acc["on"])
        s[name + "Container"] = tone(name, acc["container"])
        s["on" + cap + "Container"] = tone(name, acc["onContainer"])
    s.update(fixed)
    s["onBackground"] = tone("neutral", 90 if dark else 10)
    s["onSurface"] = tone("neutral", 90 if dark else 10)
    s["onSurfaceVariant"] = tone("neutralVariant", 80 if dark else 30)
    s["outline"] = tone("neutralVariant", 60 if dark else 50)
    s["outlineVariant"] = tone("neutralVariant", 30 if dark else 80)
    s["inverseSurface"] = tone("neutral", 90 if dark else 20)
    s["inverseOnSurface"] = tone("neutral", 20 if dark else 95)
    s["inversePrimary"] = tone("primary", 40 if dark else 70)
    s["scrim"] = "0xFF000000"
    s["surfaceTint"] = s["primary"]
    return s


PAIRS = [
    ("onPrimary", "primary"), ("onPrimaryContainer", "primaryContainer"),
    ("onSecondary", "secondary"), ("onSecondaryContainer", "secondaryContainer"),
    ("onTertiary", "tertiary"), ("onTertiaryContainer", "tertiaryContainer"),
    ("onError", "error"), ("onErrorContainer", "errorContainer"),
    ("onBackground", "background"), ("onSurface", "surface"),
    ("onSurfaceVariant", "surfaceVariant"), ("onSurfaceVariant", "surface"),
    ("onSurface", "surfaceContainerHighest"), ("outline", "surface"),
    ("primary", "surface"), ("secondary", "surface"), ("error", "surface"),
    ("inverseOnSurface", "inverseSurface"),
]

ORDER = [
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "error", "onError", "errorContainer", "onErrorContainer",
    "background", "onBackground", "surface", "onSurface",
    "surfaceVariant", "onSurfaceVariant", "surfaceTint",
    "surfaceDim", "surfaceBright",
    "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
    "surfaceContainerHigh", "surfaceContainerHighest",
    "outline", "outlineVariant",
    "inverseSurface", "inverseOnSurface", "inversePrimary", "scrim",
]

failures = []

for dark in (True, False):
    s = scheme(dark)
    print(("dark" if dark else "light") + "ColorScheme(")
    for k in ORDER:
        print(f"    {k} = Color({s[k]}),")
    print(")")
    print("-- contrast --")
    for fg, bg in PAIRS:
        c = contrast(s[fg], s[bg])
        # `outline` draws borders and dividers, not text, so Material asks 3:1 of it rather than 4.5.
        floor = 3.0 if fg == "outline" else 4.5
        flag = "" if c >= floor else "  <-- FAILS"
        if c < floor:
            failures.append(f"{'dark' if dark else 'light'}: {fg} on {bg} = {c:.2f}")
        print(f"   {fg:>24} on {bg:<24} {c:5.2f}{flag}")
    print()

if failures:
    raise SystemExit("contrast failures:\n  " + "\n  ".join(failures))
print("all pairs pass")
