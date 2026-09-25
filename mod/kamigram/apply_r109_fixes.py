#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r109: сгорающие и одноразовые медиа можно пересылать и сохранять.

Жалоба: «пересылка и сохранение в галерею сгорающих/одноразовых фото и видео
не работает — убери защиту, копай глубже».

Что найдено в 12.10.3 (глубокий разбор):
  * canForwardMessage() уже разблокирован (r80: noRestrictions по умолчанию);
  * НО меню сообщения в ChatActivity прячет пункты «Переслать», «Сохранить в
    галерею», «Сохранить в загрузки», «Поделиться» для медиа с таймером:
      - canForward: `!selectedObject.needDrawBluredPreview()`;
      - сохранение: `!selectedObject.needDrawBluredPreview()` и
        `!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()`;
  * PhotoViewer прячет кнопку галереи и «Поделиться» при
    `messageOwner.ttl != 0 && ttl < 3600` (сгорающие) и noforwards.

Патч снимает именно эти клиентские блокировки. Серверное удаление одноразовых
медиа (протокол Telegram) не перекрывается — но пока медиа существует, его
можно переслать и сохранить.

Запуск: python3 apply_r109_fixes.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
MARKER = "KAMIGRAM_TTL_MEDIA_R109"
DONE = []
FAIL = []


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


chat = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java")
viewer = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java")

# --- 1. ChatActivity: пункты меню для сгорающих/одноразовых медиа ---------
if not os.path.isfile(chat):
    FAIL.append("ChatActivity.java не найден")
else:
    text = read(chat)
    if MARKER in text:
        DONE.append("ChatActivity: уже применено")
    else:
        count = 0

        # «Переслать» и сохранение: размытый предпросмотр (медиа с таймером)
        # больше не прячет пункты меню.
        old = "!selectedObject.needDrawBluredPreview()"
        count += text.count(old)
        text = text.replace(old, "true /* %s */" % MARKER)

        # одноразовые голосовые и кружки можно сохранять и делиться.
        old = "!selectedObject.isVoiceOnce() && !selectedObject.isRoundOnce()"
        count += text.count(old)
        text = text.replace(old, "true /* %s */" % MARKER)

        if count == 0:
            FAIL.append("ChatActivity: блокировки сгорающих медиа не найдены")
        else:
            write(chat, text)
            DONE.append("ChatActivity: снято блокировок — %d" % count)

# --- 2. PhotoViewer: кнопка галереи и «Поделиться» ------------------------
if not os.path.isfile(viewer):
    FAIL.append("PhotoViewer.java не найден")
else:
    text = read(viewer)
    marker2 = "KAMIGRAM_TTL_GALLERY_R109"
    if marker2 in text:
        DONE.append("PhotoViewer: уже применено")
    else:
        old = ("            if (isEmbedVideo || newMessageObject.messageOwner.ttl != 0"
               " && newMessageObject.messageOwner.ttl < 60 * 60 || noforwards) {\n")
        new = ("            if (isEmbedVideo) { /* %s: сгорающие медиа можно"
               " сохранять в галерею и делиться */\n") % marker2
        if old not in text:
            FAIL.append("PhotoViewer: якорь ttl-блокировки галереи не найден")
        else:
            write(viewer, text.replace(old, new, 1))
            DONE.append("PhotoViewer: галерея/«Поделиться» доступны для сгорающих медиа")

print("=== r109 fixes done ===")
for line in DONE:
    print("  + " + line)
if FAIL:
    print("=== r109 fixes FAILED ===")
    for line in FAIL:
        print("  ! " + line)
    sys.exit(1)
