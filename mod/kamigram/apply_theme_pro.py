#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Legacy P16 entry point.

Sakura intentionally ships only Telegram's original themes.  This compatibility
entry point does not create, rewrite or register an attheme; it is retained so
older automation fails safe instead of silently recolouring Telegram assets.
"""

import os
import sys


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else ""
    if assets and not os.path.isdir(assets):
        print("P16 disabled: asset directory not found", file=sys.stderr)
        return 2
    print("P16 disabled: stock Telegram themes only; no custom attheme generated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
