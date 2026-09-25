#!/usr/bin/env python3
"""r106: разрешение «поверх других окон», сгорающие медиа, крупная иконка.

P118 (запускается из mod/apply-mod.sh перед проверкой символов):

1. Гарантия `android.permission.SYSTEM_ALERT_WINDOW` в манифесте: без него
   плавающее окно видео не может работать поверх приложений (жалоба r105).
2. Локальный таймер уничтожения сгорающих/одноразовых медиа не создаётся —
   контент остаётся в чате и галерее после просмотра (жалоба r105). Серверный
   updateDeleteMessages не трогаем: это протокол Telegram.
"""
import os
import re
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
DONE = []
FAIL = []


def read(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def die(msg):
    FAIL.append(msg)


PERM = 'android.permission.SYSTEM_ALERT_WINDOW'

# --- 1. разрешение в манифесте ----------------------------------------
for variant in ("main", "debug", "release"):
    manifest = os.path.join(TG, "TMessagesProj/src/%s/AndroidManifest.xml" % variant)
    if not os.path.isfile(manifest):
        continue
    text = read(manifest)
    if PERM in text:
        DONE.append("manifest %s: SYSTEM_ALERT_WINDOW уже на месте" % variant)
        continue
    # Вставляем после первой строки uses-permission; если их нет — после <manifest...>
    m = re.search(r"^(\s*)<uses-permission[^>]*>\s*$", text, re.M)
    if m:
        line = '%s<uses-permission android:name="%s" />' % (m.group(1), PERM)
        text = text[:m.end()] + "\n" + line + text[m.end():]
    else:
        m = re.search(r"<manifest[^>]*>", text)
        if not m:
            die("manifest %s: не найден тег <manifest>" % variant)
            continue
        line = '\n    <uses-permission android:name="%s" />' % PERM
        text = text[:m.end()] + line + text[m.end():]
    write(manifest, text)
    DONE.append("manifest %s: SYSTEM_ALERT_WINDOW добавлено" % variant)

# --- 2. локальный таймер сгорающих медиа ------------------------------
mc = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java")
if os.path.isfile(mc):
    text = read(mc)
    marker = "KAMIGRAM_KEEP_TTL_MEDIA_R106"
    if marker in text:
        DONE.append("MessagesController: keepTtlMedia уже применён")
    else:
        old = ("        if (createDeleteTask) {\n"
               "            getMessagesStorage().createTaskForMid(dialogId, mid, time, time, ttl, false);\n"
               "        }")
        new = ("        if (createDeleteTask) {\n"
               "            /* %s: сгорающие и одноразовые медиа не уничтожаются\n"
               "               локально после просмотра (keepTtlMedia). */\n"
               "            if (org.telegram.messenger.kamigram.KamiGramConfig.keepTtlMedia()) {\n"
               "                createDeleteTask = false;\n"
               "            }\n"
               "        }\n"
               "        if (createDeleteTask) {\n"
               "            getMessagesStorage().createTaskForMid(dialogId, mid, time, time, ttl, false);\n"
               "        }") % marker
        if old in text:
            write(mc, text.replace(old, new, 1))
            DONE.append("MessagesController: локальный таймер уничтожения отключён")
        else:
            die("MessagesController: якорь createTaskForMid не найден")
else:
    die("MessagesController.java не найден")

print("=== r106 fixes done ===")
for line in DONE:
    print("  + " + line)
if FAIL:
    print("=== r106 fixes FAILED ===")
    for line in FAIL:
        print("  ! " + line)
    sys.exit(1)
