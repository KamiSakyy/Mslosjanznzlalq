#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r111: аудио/музыка (и любые медиа) больше не удаляются автоматически.

Расследование 12.10.3 показало: кроме уже отключённого AutoDeleteMediaTask
и закрытого в r106 входа markMessageAsRead2, локальные задачи автоудаления
создавались ещё тремя путями, а старые задачи, записанные в базу ДО этих
правок, продолжали исполняться:

  1. MessagesStorage.markMessagesContentAsRead -> createTaskForSecretMedia
     (планировал удаление любых медиа с ttl при отметке контента прочитанным);
  2. MessagesStorage.createTaskForSecretChat (4 вызова из MessagesController
     и SecretChatHelper);
  3. MessagesStorage.createTaskForMid (вход закрыт в r106, здесь — сам путь);
  4. исполнитель MessagesStorage.getNewTask -> MessagesController
     .checkDeletingTask -> deleteMessages -> FileLoader.deleteFiles — именно
     он удалял файлы (включая аудио/музыку) по старым задачам из enc_tasks_v4.

Правки (маркер KAMIGRAM_TTL_NO_LOCAL_TASKS_R111):
  * toTask-ветка markMessagesContentAsRead вместо планирования удаления
    просто отмечает контент прочитанным (бейджи гаснут, повторных scans нет);
  * createTaskForSecretMedia и createTaskForMid — ранний выход;
  * createTaskForSecretChat — список задач очищается сразу после курсора,
    отметка прочитанного (random_ids) сохраняется;
  * getNewTask — ранний выход: исполнитель локальных задач выключен;
  * KAMIGRAM_ENC_TASKS_PURGE_R111 — одноразовая (на аккаунт) чистка таблицы
    enc_tasks_v4 сразу после openDatabase: старые таймеры из прежних сборок
    удаляются и никогда не срабатывают.

Серверные удаления (протокол: сервер сам присылает deleteMessages) и ручная
очистка в штатных настройках Telegram (CacheControlActivity) не затрагиваются.

Запуск: python3 apply_r111_fixes.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
PATH = os.path.join(TG, "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")

MARK = "KAMIGRAM_TTL_NO_LOCAL_TASKS_R111"


def replace_once(source, old, new, what):
    count = source.count(old)
    if count != 1:
        raise SystemExit("r111: якорь «%s» найден %d раз (ожидался 1)" % (what, count))
    return source.replace(old, new, 1)


def main():
    source = io.open(PATH, encoding="utf-8", errors="replace").read()
    if MARK in source:
        print("r111 media tasks: уже применено")
        return 0

    # 1) markMessagesContentAsRead: toTask -> отметка прочитанным вместо задачи
    source = replace_once(
        source,
        "                    for (int a = 0, N = toTask.size(); a < N; a++) {\n"
        "                        createTaskForSecretMedia(toTask.keyAt(a), toTask.valueAt(a));\n"
        "                    }\n",
        "                    /* " + MARK + ": сгорающие медиа только отмечаются\n"
        "                       прочитанными — локальные задачи автоудаления не планируются,\n"
        "                       аудио/музыка и другие медиа не уничтожаются автоматически. */\n"
        "                    for (int a = 0, N = toTask.size(); a < N; a++) {\n"
        "                        SparseArray<ArrayList<Integer>> byDate = toTask.valueAt(a);\n"
        "                        ArrayList<Integer> flat = new ArrayList<>();\n"
        "                        for (int b = 0; b < byDate.size(); b++) {\n"
        "                            flat.addAll(byDate.valueAt(b));\n"
        "                        }\n"
        "                        markMessagesContentAsReadInternal(toTask.keyAt(a), flat, currentDate);\n"
        "                    }\n",
        "toTask loop",
    )

    # 2) createTaskForSecretMedia: ранний выход
    source = replace_once(
        source,
        "    private void createTaskForSecretMedia(long dialogId, SparseArray<ArrayList<Integer>> messages) {\n"
        "        SQLiteCursor cursor = null;\n",
        "    private void createTaskForSecretMedia(long dialogId, SparseArray<ArrayList<Integer>> messages) {\n"
        "        if (true) {\n"
        "            return; /* " + MARK + " */\n"
        "        }\n"
        "        SQLiteCursor cursor = null;\n",
        "createTaskForSecretMedia",
    )

    # 3) createTaskForMid: ранний выход
    source = replace_once(
        source,
        "    public void createTaskForMid(long dialogId, int messageId, int time, int readTime, int ttl, boolean inner) {\n"
        "        storageQueue.postRunnable(() -> {\n",
        "    public void createTaskForMid(long dialogId, int messageId, int time, int readTime, int ttl, boolean inner) {\n"
        "        if (true) {\n"
        "            return; /* " + MARK + " */\n"
        "        }\n"
        "        storageQueue.postRunnable(() -> {\n",
        "createTaskForMid",
    )

    # 4) createTaskForSecretChat: задачи не планируются, отметка прочитанного остаётся
    source = replace_once(
        source,
        "                cursor.dispose();\n"
        "                cursor = null;\n"
        "\n"
        "                if (random_ids != null) {\n"
        "                    AndroidUtilities.runOnUIThread(() -> {\n"
        "                        markMessagesContentAsRead(dialogId, midsArray, 0, 0);\n",
        "                cursor.dispose();\n"
        "                cursor = null;\n"
        "                /* " + MARK + ": список задач очищается — медиа не\n"
        "                   удаляется автоматически; отметка прочитанного сохраняется. */\n"
        "                messages.clear();\n"
        "\n"
        "                if (random_ids != null) {\n"
        "                    AndroidUtilities.runOnUIThread(() -> {\n"
        "                        markMessagesContentAsRead(dialogId, midsArray, 0, 0);\n",
        "createTaskForSecretChat",
    )

    # 5) getNewTask: исполнитель локальных задач автоудаления выключен
    source = replace_once(
        source,
        "    public void getNewTask(LongSparseArray<ArrayList<Integer>> oldTask, LongSparseArray<ArrayList<Integer>> oldTaskMedia) {\n"
        "        storageQueue.postRunnable(() -> {\n",
        "    public void getNewTask(LongSparseArray<ArrayList<Integer>> oldTask, LongSparseArray<ArrayList<Integer>> oldTaskMedia) {\n"
        "        if (true) {\n"
        "            return; /* " + MARK + ": исполнитель автоудаления выключен */\n"
        "        }\n"
        "        storageQueue.postRunnable(() -> {\n",
        "getNewTask",
    )

    # 6) одноразовая чистка старых задач из enc_tasks_v4 после открытия базы
    source = replace_once(
        source,
        "        storageQueue.postRunnable(() -> openDatabase(1));\n"
        "    }\n",
        "        storageQueue.postRunnable(() -> openDatabase(1));\n"
        "        /* KAMIGRAM_ENC_TASKS_PURGE_R111: одноразовое удаление задач\n"
        "           автоудаления, оставшихся от прежних сборок, — старые таймеры\n"
        "           больше не уничтожают аудио/музыку и другие медиа. */\n"
        "        storageQueue.postRunnable(() -> {\n"
        "            try {\n"
        "                if (database == null) {\n"
        "                    return;\n"
        "                }\n"
        "                final android.content.SharedPreferences flags =\n"
        "                    ApplicationLoader.applicationContext.getSharedPreferences(\n"
        "                        \"sakura_flags\", android.content.Context.MODE_PRIVATE);\n"
        "                final String key = \"enc_tasks_purged_r111_\" + currentAccount;\n"
        "                if (flags.getInt(key, 0) != 0) {\n"
        "                    return;\n"
        "                }\n"
        "                database.executeFast(\"DELETE FROM enc_tasks_v4\").stepThis().dispose();\n"
        "                flags.edit().putInt(key, 1).commit();\n"
        "            } catch (Throwable ignore) {\n"
        "            }\n"
        "        });\n"
        "    }\n",
        "enc_tasks purge",
    )

    io.open(PATH, "w", encoding="utf-8").write(source)
    print("r111 media tasks: локальные автоудаления медиа выключены, старые задачи вычищены")
    return 0


if __name__ == "__main__":
    sys.exit(main())
