#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deprecated P80 entry point.

Theme lifecycle hooks used to repaint every Telegram activity.  Sakura keeps
Telegram's own theme lifecycle untouched, so this script is intentionally a
safe no-op and never edits ApplicationLoader.java.
"""

import sys


def main():
    print("P80 disabled: native Telegram theme lifecycle remains untouched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
