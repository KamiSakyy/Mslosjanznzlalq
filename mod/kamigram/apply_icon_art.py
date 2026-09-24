#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Install the supplied KamiGram artwork as a real adaptive launcher icon."""

import io
import os
import re
import shutil
import subprocess
import sys

MARK = "KAMIGRAM_ADAPTIVE_ICON"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def render_icons(artwork, res):
    # Keep the portrait in the adaptive safe zone. The source is deliberately
    # not flattened into the background: launchers can mask/scale the foreground
    # independently while the lavender background remains a real adaptive layer.
    crop = os.path.join(res, ".kamigram_icon_crop.png")
    run("convert", artwork, "-auto-orient", "-gravity", "North", "-crop", "338x338+0+0", "+repage", crop)

    for density, size in DENSITIES.items():
        directory = os.path.join(res, "mipmap-" + density)
        os.makedirs(directory, exist_ok=True)
        # Raster fallback is a composed square portrait for pre-O launchers.
        run("convert", crop, "-resize", "%dx%d^" % (size, size), "-gravity", "center",
            "-extent", "%dx%d" % (size, size), os.path.join(directory, "ic_launcher.png"))
        # Round fallback has a true alpha circle instead of relying on a vendor
        # launcher to crop a square.
        round_path = os.path.join(directory, "ic_launcher_round.png")
        mask = os.path.join(res, ".kamigram_mask_%s.png" % density)
        run("convert", "-size", "%dx%d" % (size, size), "xc:none", "-fill", "white",
            "-draw", "circle %d,%d %d,0" % (size // 2, size // 2, size // 2), mask)
        run("convert", crop, "-resize", "%dx%d^" % (size, size), "-gravity", "center",
            "-extent", "%dx%d" % (size, size), mask, "-alpha", "off", "-compose", "CopyOpacity",
            "-composite", round_path)

        mipmap = os.path.join(directory, "kamigram_icon_foreground.png")
        # Adaptive icons use a 108dp canvas. Keep the supplied portrait in an
        # approximately 80dp centered safe zone; the transparent padding lets
        # every launcher apply its own circle/squircle mask without cutting the
        # face.
        scale = size / 48.0
        canvas = int(round(108 * scale))
        art_px = int(round(80 * scale))
        work = os.path.join(res, ".kamigram_icon_work_%s.png" % density)
        run("convert", crop, "-resize", "%dx%d^" % (art_px, art_px), "-gravity", "center",
            "-extent", "%dx%d" % (art_px, art_px), work)
        run("convert", "-size", "%dx%d" % (canvas, canvas), "xc:none", work,
            "-gravity", "center", "-compose", "Over", "-composite", mipmap)
        try:
            os.remove(work)
        except OSError:
            pass

    # Any-dpi foreground references the density-independent raster. This avoids
    # vectorizing the artwork and preserves its actual details.
    anydpi = os.path.join(res, "mipmap-anydpi-v26")

    # Any-dpi foreground references the density-independent raster. This avoids
    # vectorizing the artwork and preserves its actual details.
    anydpi = os.path.join(res, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    write(os.path.join(anydpi, "ic_launcher.xml"), '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/kamigram_icon_background" />
    <foreground android:drawable="@mipmap/kamigram_icon_foreground" />
    <monochrome android:drawable="@mipmap/kamigram_icon_foreground" />
</adaptive-icon>
''')
    write(os.path.join(anydpi, "ic_launcher_round.xml"), '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/kamigram_icon_background" />
    <foreground android:drawable="@mipmap/kamigram_icon_foreground" />
    <monochrome android:drawable="@mipmap/kamigram_icon_foreground" />
</adaptive-icon>
''')
    write(os.path.join(res, "drawable/kamigram_icon_background.xml"), '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#241735" />
</shape>
''')

    for filename in (work, crop):
        try:
            os.remove(filename)
        except OSError:
            pass
    for density in DENSITIES:
        try:
            os.remove(os.path.join(res, ".kamigram_mask_%s.png" % density))
        except OSError:
            pass


def patch_manifest(manifest):
    source = io.open(manifest, encoding="utf-8").read()
    # DefaultIcon inherits the application icon, but explicit icon attrs make
    # every launcher and OEM round-icon implementation deterministic.
    if MARK not in source:
        anchor = ('<activity-alias\n'
                  '            android:enabled="true"\n'
                  '            android:name="org.telegram.messenger.DefaultIcon"')
        if anchor in source:
            source = source.replace(
                anchor,
                anchor + '\n            android:icon="@mipmap/ic_launcher"\n'
                '            android:roundIcon="@mipmap/ic_launcher_round"',
                1,
            )
        source = re.sub(r'android:icon="@mipmap/icon_[2-6]_launcher"',
                        'android:icon="@mipmap/ic_launcher"', source)
        source = re.sub(r'android:roundIcon="@mipmap/icon_[2-6]_launcher_round"',
                        'android:roundIcon="@mipmap/ic_launcher_round"', source)
        # Mark only the application tag, so a rerun remains harmless.
        source = source.replace('<application\n', '<application\n        <!-- %s -->\n' % MARK, 1)
        io.open(manifest, "w", encoding="utf-8").write(source)


def main():
    if len(sys.argv) != 3:
        print("usage: apply_icon_art.py <res> <artwork>", file=sys.stderr)
        return 2
    res, artwork = sys.argv[1:]
    if not os.path.isfile(artwork):
        print("artwork not found: %s" % artwork, file=sys.stderr)
        return 1
    render_icons(artwork, res)
    patch_manifest(os.path.join(os.path.dirname(res), "AndroidManifest.xml"))
    print("P2A: adaptive launcher icon installed from supplied artwork")
    return 0


if __name__ == "__main__":
    sys.exit(main())
