#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Add invisible brightness/volume swipe zones to Telegram's video views."""

import io
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
JAVA = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram")
MARK = "KAMIGRAM_VIDEO_GESTURES"
DONE = []
MISS = []


def patch_file(rel, marker, old, new, what):
    path = os.path.join(JAVA, *rel.split("/"))
    try:
        source = io.open(path, encoding="utf-8").read()
    except OSError as exc:
        MISS.append("%s: %s" % (rel, exc))
        return
    if marker in source:
        return
    if old not in source:
        MISS.append("%s: anchor not found (%s)" % (rel, what))
        return
    io.open(path, "w", encoding="utf-8").write(source.replace(old, new, 1))
    DONE.append(what)


def main():
    patch_file(
        "ui/PhotoViewer.java",
        MARK + "_PHOTO",
        """        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (videoPlayerControlVisible && isPlaying) {""",
        """        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            /* KAMIGRAM_VIDEO_GESTURES_PHOTO: invisible left/right vertical zones. */
            if (org.telegram.messenger.kamigram.KamiGramVideoGestures.handle(
                    this, ev, isCurrentVideo && videoPlayer != null)) {
                return true;
            }
            if (videoPlayerControlVisible && isPlaying) {""",
        "photo viewer: invisible brightness/volume zones",
    )

    patch_file(
        "ui/Components/PipVideoOverlay.java",
        MARK + "_PIP",
        """            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();""",
        """            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                /* KAMIGRAM_VIDEO_GESTURES_PIP: same zones in native PiP player. */
                if (org.telegram.messenger.kamigram.KamiGramVideoGestures.handle(
                        this, ev, photoViewer != null && photoViewer.getVideoPlayer() != null)) {
                    return true;
                }
                int action = ev.getActionMasked();""",
        "PiP video: invisible brightness/volume zones",
    )

    if MISS:
        for item in MISS:
            print("! " + item)
        return 1
    print("video gestures: %d patches" % len(DONE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
