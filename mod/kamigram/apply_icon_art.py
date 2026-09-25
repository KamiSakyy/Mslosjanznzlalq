#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Install the supplied, existing Emilia artwork as Sakura's Android icon.

No artwork is generated here.  ImageMagick only makes the Android density
variants, the circular legacy fallback and the adaptive-icon foreground from
the checked-in source image.
"""

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


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.PIPE).strip()


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def square_source(artwork, res):
    """Make a square portrait crop without assuming a particular source size."""
    width, height = [int(value) for value in output("identify", "-format", "%w %h", artwork).split()]
    side = min(width, height)
    crop = os.path.join(res, ".sakura_icon_crop.png")
    # North keeps Emilia's face and hair in the icon even for a tall portrait.
    run("convert", artwork, "-auto-orient", "-gravity", "North",
        "-crop", "%dx%d+0+0" % (side, side), "+repage", "-strip", crop)
    return crop, side


def circular_raster(crop, path, size):
    square = os.path.join(os.path.dirname(path), ".sakura_square_%d.png" % size)
    mask = os.path.join(os.path.dirname(path), ".sakura_mask_%d.png" % size)
    run("convert", crop, "-resize", "%dx%d^" % (size, size), "-gravity", "center",
        "-extent", "%dx%d" % (size, size), "-strip", square)
    run("convert", "-size", "%dx%d" % (size, size), "xc:none", "-fill", "white",
        "-draw", "circle %d,%d %d,0" % (size // 2, size // 2, size // 2), mask)
    run("convert", square, mask, "-alpha", "on", "-compose", "CopyOpacity",
        "-composite", "-strip", path)
    for temporary in (square, mask):
        try:
            os.remove(temporary)
        except OSError:
            pass


def render_icons(artwork, res):
    crop, _ = square_source(artwork, res)
    try:
        for density, size in DENSITIES.items():
            directory = os.path.join(res, "mipmap-" + density)
            os.makedirs(directory, exist_ok=True)

            # Both legacy names are genuinely circular.  The manifest uses the
            # round name as its primary icon, so launchers which do not apply a
            # mask still receive a complete circle rather than a square tile.
            circular_raster(crop, os.path.join(directory, "ic_launcher.png"), size)
            circular_raster(crop, os.path.join(directory, "ic_launcher_round.png"), size)

            # Adaptive icons use a 108dp canvas.  The square artwork fills the
            # canvas; Android's roundIcon mask supplies the circular silhouette
            # while the legacy fallback above covers pre-O and OEM edge cases.
            canvas = int(round(108 * size / 48.0))
            foreground = os.path.join(directory, "kamigram_icon_foreground.png")
            run("convert", crop, "-resize", "%dx%d^" % (canvas, canvas),
                "-gravity", "center", "-extent", "%dx%d" % (canvas, canvas),
                "-strip", foreground)

        anydpi = os.path.join(res, "mipmap-anydpi-v26")
        os.makedirs(anydpi, exist_ok=True)
        adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/kamigram_icon_background" />
    <foreground android:drawable="@mipmap/kamigram_icon_foreground" />
    <monochrome android:drawable="@mipmap/kamigram_icon_foreground" />
</adaptive-icon>
'''
        write(os.path.join(anydpi, "ic_launcher.xml"), adaptive)
        write(os.path.join(anydpi, "ic_launcher_round.xml"), adaptive)
        write(os.path.join(res, "drawable/kamigram_icon_background.xml"), '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#241735" />
</shape>
''')
    finally:
        try:
            os.remove(crop)
        except OSError:
            pass


def patch_manifest(manifest):
    source = io.open(manifest, encoding="utf-8").read()

    # Make the application and every Telegram icon alias deterministic.  The
    # round resource is circular on pre-O and explicitly marked round on O+.
    source = re.sub(
        r'android:(?:icon|roundIcon)="@(?:mipmap|drawable)/(?:ic_launcher|icon_[1-6]_launcher(?:_round)?)"',
        lambda match: 'android:' + ('roundIcon' if 'roundIcon' in match.group(0) else 'icon')
        + '="@mipmap/ic_launcher_round"',
        source,
    )
    app_start = source.find("<application")
    if app_start < 0:
        raise RuntimeError("AndroidManifest.xml has no application tag")
    app_end = source.find(">", app_start)
    if app_end < 0:
        raise RuntimeError("application tag is not closed")
    app_tag = source[app_start:app_end + 1]
    if "android:icon=" in app_tag:
        app_tag = re.sub(r'android:icon="[^"]+"',
                         'android:icon="@mipmap/ic_launcher_round"', app_tag, count=1)
    else:
        app_tag = app_tag[:-1] + ' android:icon="@mipmap/ic_launcher_round">'
    if "android:roundIcon=" in app_tag:
        app_tag = re.sub(r'android:roundIcon="[^"]+"',
                         'android:roundIcon="@mipmap/ic_launcher_round"', app_tag, count=1)
    else:
        app_tag = app_tag[:-1] + ' android:roundIcon="@mipmap/ic_launcher_round">'
    app_marker = "        <!-- %s: existing artwork, adaptive and round resources -->\n" % MARK
    if MARK not in source:
        source = source[:app_start] + app_tag + "\n" + app_marker + source[app_end + 1:]
    else:
        source = source[:app_start] + app_tag + source[app_end + 1:]

    # roundIcon is an application attribute; aliases use their circular
    # android:icon resource instead so aapt accepts every Android version.
    source = re.sub(r'(<activity-alias\b[^>]*?)\s+android:roundIcon="[^"]+"', r'\1', source, flags=re.DOTALL)

    # The default alias is the icon chosen by Telegram's icon switcher.
    default_pattern = re.compile(
        r'(<activity-alias\b(?=[^>]*android:name="org\.telegram\.messenger\.DefaultIcon")[^>]*?)>',
        re.DOTALL,
    )

    def default_alias(match):
        tag = match.group(1)
        self_closing = tag.rstrip().endswith('/')
        tag = tag.rstrip().rstrip('/')
        tag = re.sub(r'\s+android:(?:icon|roundIcon)="[^"]+"', '', tag)
        closing = '/>' if self_closing else '>'
        return (tag + '\n            android:icon="@mipmap/ic_launcher_round"' + closing)

    source = default_pattern.sub(default_alias, source, count=1)
    io.open(manifest, "w", encoding="utf-8").write(source)


def main():
    if len(sys.argv) != 3:
        print("usage: apply_icon_art.py <res> <artwork>", file=sys.stderr)
        return 2
    res, artwork = sys.argv[1:]
    if not os.path.isfile(artwork):
        print("artwork not found: %s" % artwork, file=sys.stderr)
        return 1
    if shutil.which("convert") is None or shutil.which("identify") is None:
        print("ImageMagick convert and identify are required", file=sys.stderr)
        return 1
    render_icons(artwork, res)
    patch_manifest(os.path.join(os.path.dirname(res), "AndroidManifest.xml"))
    print("P2A: adaptive and circular launcher icons installed from existing artwork")
    return 0


if __name__ == "__main__":
    sys.exit(main())
