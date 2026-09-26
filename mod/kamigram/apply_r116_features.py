#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r116: поиск «только глобальный» + «чистый поиск» по категориям, фильтр по
словам в ленте и поиске, защита перемотки аудио от вылета.

1. ПЕРЕМОТКА АУДИО НЕ ВЫЛЕТАЕТ (KAMIGRAM_AUDIO_SEEK_SAFE_R116):
   * MediaController.seekToProgress / seekToProgressMs ловят Throwable, а не
     только Exception (IllegalStateException от MediaCodec/ExoPlayer при
     перемотке больше не роняет приложение);
   * MusicPlayerService.onSeekTo (системные кнопки/гарнитура/Android Auto)
     тоже защищён;
   * корень проблемы r114 (удаление файлов играющего диалога авто-чисткой)
     убран в r115: авто-архив больше ничего не удаляет.

2. ПОИСК (KAMIGRAM_SEARCH_FILTER_R116):
   * DialogsSearchAdapter.updateSearchResults — локальные результаты проходят
     через KamiGramSearchFilter (режим «только глобальный поиск» очищает
     локальную выдачу; категории люди/группы/боты/каналы; фильтр по словам);
   * onDataSetChanged (приход серверной выдачи) — глобальный и «свой
     серверный» списки фильтруются на месте. Диалог «Поделиться» использует
     собственный экземпляр помощника и не ограничивается.

3. ФИЛЬТР ПО СЛОВАМ В ЛЕНТЕ (KAMIGRAM_WORD_FILTER_R116):
   * ChatActivity.didReceivedNotification_messagesDidLoad — загруженная
     история (каналы, чаты, боты) очищается от сообщений со словами-
     исключениями до попадания в список;
   * ChatActivity.processNewMessages — новые входящие сообщения фильтруются
     так же. Свои сообщения не трогаются.

Запуск: python3 apply_r116_features.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
J = os.path.join(TG, "TMessagesProj/src/main/java")
MEDIAC = os.path.join(J, "org/telegram/messenger/MediaController.java")
MUSIC = os.path.join(J, "org/telegram/messenger/MusicPlayerService.java")
CHAT = os.path.join(J, "org/telegram/ui/ChatActivity.java")
DSA = os.path.join(J, "org/telegram/ui/Adapters/DialogsSearchAdapter.java")

SEEK_MARK = "KAMIGRAM_AUDIO_SEEK_SAFE_R116"
MUSIC_MARK = "KAMIGRAM_MUSIC_SEEK_SAFE_R116"
WORD_MARK = "KAMIGRAM_WORD_FILTER_R116"
SEARCH_MARK = "KAMIGRAM_SEARCH_FILTER_R116"


def die(msg):
    sys.stderr.write("r116: %s\n" % msg)
    sys.exit(1)


def read(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def write(path, src):
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(src)


def count(src, anchor):
    return src.count(anchor)


def patch_once(path, anchor, replacement, mark, label):
    src = read(path)
    if mark in src:
        print("r116: %s уже применён" % label)
        return
    n = count(src, anchor)
    if n != 1:
        die("%s: якорь найден %d раз (ожидался 1) в %s" % (label, n, path))
    write(path, src.replace(anchor, replacement, 1))
    print("r116: %s применён" % label)


# ------------------------------------------------------------------ 1. seek
SEEK1_ANCHOR = (
    "        } catch (Exception e) {\n"
    "            FileLog.e(e);\n"
    "            return false;\n"
    "        }\n"
    "        NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidSeek, playingMessageObject.getId(), progress);\n"
)
SEEK1_REPLACE = (
    "        } catch (Throwable e) { /* " + SEEK_MARK + ": перемотка не роняет приложение */\n"
    "            FileLog.e(e);\n"
    "            return false;\n"
    "        }\n"
    "        NotificationCenter.getInstance(messageObject.currentAccount).postNotificationName(NotificationCenter.messagePlayingDidSeek, playingMessageObject.getId(), progress);\n"
)

SEEK2_ANCHOR = (
    "        } catch (Exception e) {\n"
    "            FileLog.e(e);\n"
    "            return false;\n"
    "        }\n"
    "        if (duration != 0) {\n"
)
SEEK2_REPLACE = (
    "        } catch (Throwable e) { /* KAMIGRAM_AUDIO_SEEKMS_SAFE_R116: перемотка не роняет приложение */\n"
    "            FileLog.e(e);\n"
    "            return false;\n"
    "        }\n"
    "        if (duration != 0) {\n"
)

MUSIC_ANCHOR = (
    "                public void onSeekTo(long pos) {\n"
    "                    MessageObject object = MediaController.getInstance().getPlayingMessageObject();\n"
    "                    if (object != null) {\n"
    "                        MediaController.getInstance().seekToProgress(object, pos / 1000 / (float) object.getDuration());\n"
    "                        updatePlaybackState(pos);\n"
    "                    }\n"
    "                }\n"
)
MUSIC_REPLACE = (
    "                public void onSeekTo(long pos) {\n"
    "                    /* " + MUSIC_MARK + ": системная перемотка не роняет приложение */\n"
    "                    try {\n"
    "                        MessageObject object = MediaController.getInstance().getPlayingMessageObject();\n"
    "                        if (object != null) {\n"
    "                            MediaController.getInstance().seekToProgress(object, pos / 1000 / (float) object.getDuration());\n"
    "                            updatePlaybackState(pos);\n"
    "                        }\n"
    "                    } catch (Throwable e) {\n"
    "                        FileLog.e(e);\n"
    "                    }\n"
    "                }\n"
)

# ------------------------------------------------------- 2. фильтр по словам
LOADED_ANCHOR = (
    "    private void didReceivedNotification_messagesDidLoad(int id, int account, final Object... args) {\n"
    "        int guid = (Integer) args[10];\n"
    "        if (guid != classGuid) {\n"
    "            return;\n"
    "        }\n"
)
LOADED_REPLACE = LOADED_ANCHOR + (
    "        /* KAMIGRAM_WORD_FILTER_LOADED_R116: посты со словами-исключениями не попадают в список.\n"
    "           Счётчик args[1] не меняется — он используется для определения конца\n"
    "           истории (подгрузка вверх продолжает работать). */\n"
    "        if (args.length > 2 && args[2] instanceof ArrayList) {\n"
    "            try {\n"
    "                @SuppressWarnings(\"unchecked\")\n"
    "                ArrayList<MessageObject> kamiLoaded = (ArrayList<MessageObject>) args[2];\n"
    "                org.telegram.messenger.kamigram.KamiGramWordFilter.filterMessages(kamiLoaded);\n"
    "            } catch (Throwable kamiT) {\n"
    "                FileLog.e(kamiT);\n"
    "            }\n"
    "        }\n"
)

NEWMSG_ANCHOR = (
    "    private void processNewMessages(ArrayList<MessageObject> arr, final boolean animatedFromBottom) {\n"
    "        FileLog.d(\"processNewMessages \" + arr.size() + \" messages\");\n"
)
NEWMSG_REPLACE = NEWMSG_ANCHOR + (
    "        /* KAMIGRAM_WORD_FILTER_NEW_R116: новые сообщения со словами-исключениями не показываются. */\n"
    "        org.telegram.messenger.kamigram.KamiGramWordFilter.filterMessages(arr);\n"
    "        if (arr.isEmpty()) {\n"
    "            return;\n"
    "        }\n"
)

# ------------------------------------------------------------------ 3. поиск
LOCAL_ANCHOR = (
    "            searchWas = true;\n"
    "            for (int i = 0; i < result.size(); ++i) {\n"
    "                if (!filter(result.get(i))) {\n"
)
LOCAL_REPLACE = (
    "            searchWas = true;\n"
    "            /* KAMIGRAM_SEARCH_FILTER_LOCAL_R116: «только глобальный поиск» убирает локальную\n"
    "               выдачу; категории (люди/группы/боты/каналы) и фильтр по словам\n"
    "               применяются к каждому результату. */\n"
    "            if (org.telegram.messenger.kamigram.KamiGramSearchFilter.globalOnly()) {\n"
    "                result.clear();\n"
    "                names.clear();\n"
    "            } else {\n"
    "                for (int kamiI = 0; kamiI < result.size(); kamiI++) {\n"
    "                    Object kamiItem = result.get(kamiI);\n"
    "                    CharSequence kamiName = kamiI < names.size() ? names.get(kamiI) : null;\n"
    "                    if (!org.telegram.messenger.kamigram.KamiGramSearchFilter.allow(kamiItem, kamiName)) {\n"
    "                        result.remove(kamiI);\n"
    "                        if (kamiI < names.size()) {\n"
    "                            names.remove(kamiI);\n"
    "                        }\n"
    "                        kamiI--;\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "            for (int i = 0; i < result.size(); ++i) {\n"
    "                if (!filter(result.get(i))) {\n"
)

GLOBAL_ANCHOR = (
    "                searchWas = true;\n"
    "                if (delegate != null) {\n"
    "                    delegate.searchStateChanged(waitingResponseCount > 0, true);\n"
    "                }\n"
    "                notifyDataSetChanged();\n"
)
GLOBAL_REPLACE = (
    "                searchWas = true;\n"
    "                if (delegate != null) {\n"
    "                    delegate.searchStateChanged(waitingResponseCount > 0, true);\n"
    "                }\n"
    "                /* KAMIGRAM_SEARCH_FILTER_GLOBAL_R116: серверная выдача фильтруется до отрисовки. */\n"
    "                org.telegram.messenger.kamigram.KamiGramSearchFilter.applyTo(searchAdapterHelper);\n"
    "                notifyDataSetChanged();\n"
)


def main():
    for path in (MEDIAC, MUSIC, CHAT, DSA):
        if not os.path.exists(path):
            die("нет файла %s" % path)

    patch_once(MEDIAC, SEEK1_ANCHOR, SEEK1_REPLACE, SEEK_MARK, "seekToProgress: catch Throwable")
    patch_once(MEDIAC, SEEK2_ANCHOR, SEEK2_REPLACE, "KAMIGRAM_AUDIO_SEEKMS_SAFE_R116", "seekToProgressMs: catch Throwable")
    patch_once(MUSIC, MUSIC_ANCHOR, MUSIC_REPLACE, MUSIC_MARK, "MusicPlayerService.onSeekTo")
    patch_once(CHAT, LOADED_ANCHOR, LOADED_REPLACE, "KAMIGRAM_WORD_FILTER_LOADED_R116", "messagesDidLoad: фильтр слов")
    patch_once(CHAT, NEWMSG_ANCHOR, NEWMSG_REPLACE, "KAMIGRAM_WORD_FILTER_NEW_R116", "processNewMessages: фильтр слов")
    patch_once(DSA, LOCAL_ANCHOR, LOCAL_REPLACE, "KAMIGRAM_SEARCH_FILTER_LOCAL_R116", "updateSearchResults: фильтры поиска")
    patch_once(DSA, GLOBAL_ANCHOR, GLOBAL_REPLACE, "KAMIGRAM_SEARCH_FILTER_GLOBAL_R116", "onDataSetChanged: фильтры поиска")

    # ---- итоговые проверки
    src = read(MEDIAC)
    if "} catch (Throwable e) { /* " + SEEK_MARK not in src \
        or "KAMIGRAM_AUDIO_SEEKMS_SAFE_R116" not in src:
        die("MediaController: защита перемотки не встала")
    src = read(CHAT)
    if src.count("KAMIGRAM_WORD_FILTER_LOADED_R116") != 1 \
        or src.count("KAMIGRAM_WORD_FILTER_NEW_R116") != 1:
        die("ChatActivity: метки фильтра слов не на месте")
    src = read(DSA)
    if src.count("KAMIGRAM_SEARCH_FILTER_LOCAL_R116") != 1 \
        or src.count("KAMIGRAM_SEARCH_FILTER_GLOBAL_R116") != 1:
        die("DialogsSearchAdapter: метки фильтров поиска не на месте")
    print("r116: все патчи применены")


if __name__ == "__main__":
    main()
