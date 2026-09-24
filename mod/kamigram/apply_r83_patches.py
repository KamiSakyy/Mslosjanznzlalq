#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KamiGram r83 UI/safety cleanup applied after the r82/r83 source passes."""

import io
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
ROOT = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram")
DONE = []
MISS = []


def file_path(rel):
    return os.path.join(ROOT, *rel.split("/"))


def read(rel):
    return io.open(file_path(rel), encoding="utf-8").read()


def write(rel, text):
    io.open(file_path(rel), "w", encoding="utf-8").write(text)


def replace_once(rel, marker, old, new, what):
    text = read(rel)
    if marker in text:
        return
    if old not in text:
        MISS.append("%s: anchor not found (%s)" % (rel, what))
        return
    write(rel, text.replace(old, new, 1))
    DONE.append(what)


def strip_legacy_sponsor():
    """Safety net for a target tree produced by an older r82 installer."""
    rel = "ui/Adapters/DialogsAdapter.java"
    text = read(rel)
    original = text
    text = re.sub(
        r"\s*import org\.telegram\.messenger\.kamigram\.KamiGramSponsorCell;[^\n]*\n",
        "\n", text)
    text = re.sub(
        r"\s*VIEW_TYPE_KAMIGRAM_SPONSOR\s*=\s*\d+;[^\n]*\n",
        "\n", text)
    text = text.replace(" && viewType != VIEW_TYPE_KAMIGRAM_SPONSOR", "")
    text = re.sub(
        r"\s*case VIEW_TYPE_KAMIGRAM_SPONSOR:.*?break;\s*\n",
        "\n", text, flags=re.S)
    text = re.sub(
        r"\s*if \(dialogsType == DialogsActivity\.DIALOGS_TYPE_DEFAULT && folderId == 0\s*"
        r"&& !isOnlySelect && communityId == 0 && !parentFragment\.isArchive\(\)\) \{\s*"
        r"itemInternals\.add\(new ItemInternal\(VIEW_TYPE_KAMIGRAM_SPONSOR\).*?\}\s*",
        "\n", text, flags=re.S)
    text = re.sub(
        r"\s*if \(itemInternals\.get\(position\)\.viewType == VIEW_TYPE_KAMIGRAM_SPONSOR\) \{.*?\}\s*",
        "\n", text, flags=re.S)
    # Remove compatibility comments left on ordinary adapter conditions too;
    # a target must contain neither a hidden sponsor branch nor its old marker.
    text = re.sub(r"\s*/\*\s*KAMIGRAM_ASUMEO_SPONSOR[^*]*\*/", "", text)
    if text != original:
        write(rel, text)
        DONE.append("dialog list: remove every legacy sponsor row")


def patch_archive_button():
    replace_once(
        "ui/DialogsActivity.java",
        "KAMIGRAM_ARCHIVE_HIDE_BUTTON_R83",
        'SharedConfig.archiveHidden ? LocaleController.getString(R.string.PinInTheList) : LocaleController.getString(R.string.HideAboveTheList)',
        'SharedConfig.archiveHidden ? "Показать архив" : "Скрыть архив" /* KAMIGRAM_ARCHIVE_HIDE_BUTTON_R83 */',
        "archive: expose an explicit hide/show archive action",
    )


def validate():
    try:
        dialogs = read("ui/Adapters/DialogsAdapter.java")
        if "KamiGramSponsorCell" in dialogs or "KAMIGRAM_ASUMEO_SPONSOR" in dialogs:
            MISS.append("ui/Adapters/DialogsAdapter.java: legacy sponsor row remains")
    except OSError as exc:
        MISS.append("ui/Adapters/DialogsAdapter.java: %s" % exc)

    try:
        dialogs_activity = read("ui/DialogsActivity.java")
        if "KAMIGRAM_ARCHIVE_HIDE_BUTTON_R83" not in dialogs_activity:
            MISS.append("ui/DialogsActivity.java: explicit archive hide button marker missing")
        if "toggleArchiveHidden" not in dialogs_activity:
            MISS.append("ui/DialogsActivity.java: native archive toggle disappeared")
    except OSError as exc:
        MISS.append("ui/DialogsActivity.java: %s" % exc)

    try:
        center = read("messenger/kamigram/KamiGramCenter.java")
        if "Разработчик KamiGram" not in center:
            MISS.append("KamiGramCenter.java: developer settings action missing")
        if "https://t.me/AsuMeo" not in read("messenger/kamigram/KamiGramChannelGuard.java"):
            MISS.append("KamiGramChannelGuard.java: developer URL missing")
    except OSError as exc:
        MISS.append("KamiGram settings: %s" % exc)

    try:
        connections = read("tgnet/ConnectionsManager.java")
        if "!kamigramMessageRequest && !kamigramPushRequest" not in connections:
            MISS.append("ConnectionsManager.java: push request scope missing")
    except OSError as exc:
        MISS.append("ConnectionsManager.java: %s" % exc)


def main():
    try:
        strip_legacy_sponsor()
        patch_archive_button()
        validate()
    except Exception as exc:
        print("r83: %s" % exc, file=sys.stderr)
        return 1
    print("r83: changes — %d" % len(DONE))
    for item in DONE:
        print("  ✓ %s" % item)
    if MISS:
        print("r83: failed — %d" % len(MISS), file=sys.stderr)
        for item in MISS:
            print("  ! %s" % item, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
