#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_UPLOAD_SERVICE_R101 — отправка крупных файлов в фоне.

Задача пользователя: «сделай отправку файлов, фото, видео и т.д., которые весят
больше 10 МБ, тоже в фоне, с уведомлением через сервис — как сделано для
загрузки».

Что делает патч:
  * все отправки Telegram (фото, видео, кружочки, голосовые, документы, стикеры)
    идут через FileUploadOperation и сообщают о себе в ОДНОМ месте —
    FileLoader.FileLoaderDelegate, реализация которого живёт в ImageLoader;
  * в эти три колбэка добавляется учёт крупных отправок (KamiGramUploads):
      - fileUploadProgressChanged → прогресс + старт foreground-сервиса,
      - fileDidUploaded / fileDidFailedUpload → отправка завершена;
  * сам сервис (KamiGramDownloadService) уже объявлен в манифесте с
    foregroundServiceType="dataSync" и поднимается только для файлов крупнее
    10 МБ, показывает прогресс и останавливается сам, когда работы нет.

Собственная логика Telegram не меняется: части, повторные попытки, resume и
шифрование по-прежнему выполняет нативный FileUploadOperation. Никаких
таймеров, опросов и постоянных сервисов — только событийные колбэки.

Запуск:  python3 apply_upload_service.py <путь до TMessagesProj/src/main/java>
"""

import io
import os
import sys

MARKER = "KAMIGRAM_UPLOAD_SERVICE_R101"

HOOKS = (
    # прогресс: учитываем и, если файл крупный, поднимаем foreground-сервис
    (
        "                public void fileUploadProgressChanged(FileUploadOperation operation, final String location, long uploadedSize, long totalSize, final boolean isEncrypted) {\n",
        """                /* %s: крупная отправка продолжает идти в фоне с уведомлением */
                org.telegram.messenger.kamigram.KamiGramUploads.onProgress(currentAccount, location, uploadedSize, totalSize);
""" % MARKER,
    ),
    # файл отправлен
    (
        "                public void fileDidUploaded(final String location, final TLRPC.InputFile inputFile, final TLRPC.InputEncryptedFile inputEncryptedFile, final byte[] key, final byte[] iv, final long totalFileSize) {\n",
        """                org.telegram.messenger.kamigram.KamiGramUploads.onFinished(currentAccount, location); /* %s */
""" % MARKER,
    ),
    # отправка не удалась или отменена (cancel() приводит сюда же)
    (
        "                public void fileDidFailedUpload(final String location, final boolean isEncrypted) {\n",
        """                org.telegram.messenger.kamigram.KamiGramUploads.onFinished(currentAccount, location); /* %s */
""" % MARKER,
    ),
)


def patch_image_loader(java_root):
    path = os.path.join(java_root, "org", "telegram", "messenger", "ImageLoader.java")
    if not os.path.isfile(path):
        return False, "нет файла %s" % path
    with io.open(path, encoding="utf-8") as handle:
        source = handle.read()

    missing = []
    for signature, insertion in HOOKS:
        # Идемпотентность проверяем по паре «сигнатура + вставка»: сами вставки
        # для fileDidUploaded и fileDidFailedUpload совпадают, и проверка только
        # по вставке пропустила бы второй колбэк.
        if signature + insertion in source:
            continue  # уже применено
        if signature not in source:
            missing.append(signature.strip()[:70])
            continue
        source = source.replace(signature, signature + insertion, 1)

    if missing:
        return False, "не найдены точки вставки: " + "; ".join(missing)

    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(source)
    return True, "ImageLoader: учёт крупных отправок добавлен"


def verify(java_root):
    problems = []
    image_loader = os.path.join(java_root, "org", "telegram", "messenger", "ImageLoader.java")
    uploads = os.path.join(java_root, "org", "telegram", "messenger", "kamigram", "KamiGramUploads.java")
    service = os.path.join(java_root, "org", "telegram", "messenger", "kamigram", "KamiGramDownloadService.java")

    with io.open(image_loader, encoding="utf-8") as handle:
        source = handle.read()
    if source.count("KamiGramUploads.onProgress") != 1:
        problems.append("в ImageLoader нет ровно одного KamiGramUploads.onProgress")
    if source.count("KamiGramUploads.onFinished") != 2:
        problems.append("в ImageLoader нет двух KamiGramUploads.onFinished")

    for path, needle in ((uploads, "hasActiveLargeUploads"), (uploads, "FOREGROUND_MIN_BYTES"),
                         (service, "ensureStartedForLargeUpload"), (service, "stat_sys_upload"),
                         (service, "KamiGramUploads.hasActiveLargeUploads")):
        if not os.path.isfile(path):
            problems.append("нет файла %s" % os.path.basename(path))
            continue
        with io.open(path, encoding="utf-8") as handle:
            if needle not in handle.read():
                problems.append("в %s нет %s" % (os.path.basename(path), needle))
    return problems


def main(argv):
    if len(argv) != 2:
        sys.stderr.write("usage: apply_upload_service.py <java root>\n")
        return 2
    java_root = argv[1]
    ok, message = patch_image_loader(java_root)
    if not ok:
        sys.stderr.write("UPLOAD SERVICE: %s\n" % message)
        return 1
    problems = verify(java_root)
    if problems:
        for problem in problems:
            sys.stderr.write("UPLOAD SERVICE: %s\n" % problem)
        return 1
    print("UPLOAD SERVICE: %s" % message)
    print("UPLOAD SERVICE: >10 МБ отправляется в фоне через сервис с прогрессом")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
