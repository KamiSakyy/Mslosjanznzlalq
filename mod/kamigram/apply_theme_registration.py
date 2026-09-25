#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deprecated theme-registration entry point.

Sakura does not register a custom theme.  Keeping this helper as a
no-op makes rerunning old build automation safe and guarantees that the native
Telegram theme registry is never modified by the mod.
"""

import os
import sys


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else ""
    if path and not os.path.isfile(path):
        print("P16 disabled: Theme.java not found", file=sys.stderr)
        return 2
    print("P16 disabled: stock Telegram themes only; no custom theme registered")
    return 0


if __name__ == "__main__":
    sys.exit(main())
