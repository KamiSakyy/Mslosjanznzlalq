#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram: упаковка исходников в .zip для папки handoff.

Правило пользователя: исходники каждой версии обязательно сохраняются в handoff
и никогда не удаляются. Этот скрипт кладёт в один .zip всё, из чего собирается мод:

  * mod/              - патчер, все Java-классы KamiGram, иконки, скрипты;
  * mod-java/         - уже применённые Java-классы мода (как они попали в сборку);
  * reports/          - MOD_INFO.txt и отчёты по улучшениям (P50/P60/P70);
  * reports/changes.patch - полный diff против исходников Telegram.

Запуск:
  python3 mod/zip-sources.py <каталог-репо> <версия> <номер-сборки> <каталог-telegram-src>
"""

import os
import sys
import zipfile

SKIP_DIRS = {'__pycache__', '.git', 'build', '.gradle'}


def add_tree(zf, base, arc_prefix):
    """Добавляет каталог (или файл) в архив."""
    if not os.path.exists(base):
        return 0
    if os.path.isfile(base):
        zf.write(base, arc_prefix)
        return 1
    count = 0
    for root, dirs, files in os.walk(base):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        for name in files:
            full = os.path.join(root, name)
            arc = os.path.join(arc_prefix, os.path.relpath(full, base))
            try:
                zf.write(full, arc)
                count += 1
            except Exception:
                pass
    return count


def main():
    if len(sys.argv) < 5:
        print(__doc__)
        return 2
    repo, version, run, tg_src = sys.argv[1:5]

    out_dir = os.path.join(repo, 'handoff', 'sources')
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, 'kamigram-mod-src-%s-r%s.zip' % (version, run))

    total = 0
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        # 1. сами исходники мода (патчер + классы + ресурсы + скрипты)
        for rel in ('mod/apply-mod.sh', 'mod/archive-handoff.sh', 'mod/zip-sources.py',
                    'mod/kamigram', 'mod/keys', 'notes/MOD_FEATURES-r39.txt'):
            total += add_tree(zf, os.path.join(repo, rel), rel)

        # 2. уже применённые Java-классы мода (то, что реально ушло в сборку)
        if tg_src and os.path.isdir(tg_src):
            for rel, prefix in (
                ('TMessagesProj/src/main/java/org/telegram/messenger/kamigram', 'mod-java/messenger'),
                ('TMessagesProj/src/main/java/org/telegram/ui/Components/kamigram', 'mod-java/ui_components'),
                ('TMessagesProj/src/main/res/drawable', 'mod-java/res_drawable'),
            ):
                src = os.path.join(tg_src, rel)
                if rel.endswith('res/drawable'):
                    # из ресурсов берём только наши файлы
                    if os.path.isdir(src):
                        for name in sorted(os.listdir(src)):
                            if name.startswith('kamigram_'):
                                total += add_tree(zf, os.path.join(src, name),
                                                  prefix + '/' + name)
                else:
                    total += add_tree(zf, src, prefix)

            # 3. отчёты и diff
            reports = os.path.join(tg_src, 'MOD_INFO.txt')
            if os.path.isfile(reports):
                zf.write(reports, 'reports/MOD_INFO.txt')
                total += 1
            for name in ('MOD_FEATURES.txt', 'MOD_PRO_FEATURES.txt', 'MOD_MORE_FEATURES.txt'):
                path = os.path.join(tg_src, name)
                if os.path.isfile(path):
                    zf.write(path, 'reports/' + name)
                    total += 1

        for name in ('changes.patch', 'changes.patch.gz'):
            path = os.path.join(repo, name)
            if os.path.isfile(path):
                zf.write(path, 'reports/' + name)
                total += 1

        # 4. короткая памятка, как собрать
        readme = (
            'KamiGram - исходники мода (версия %s, сборка r%s)\n'
            '=================================================\n\n'
            'Что внутри:\n'
            '  mod/        - патчер apply-mod.sh и все Java-классы KamiGram\n'
            '  mod-java/   - классы мода уже в применённом виде (как в APK)\n'
            '  reports/    - отчёты по улучшениям и diff против исходников Telegram\n\n'
            'Как собрать:\n'
            '  1. git clone --depth 1 https://github.com/DrKLO/Telegram.git\n'
            '  2. TG_DIR=<куда склонировали> APP_NAME=KamiGram APP_PACKAGE=com.kami.gram \\\n'
            '       bash mod/apply-mod.sh\n'
            '  3. cd <TG_DIR> && ./gradlew :TMessagesProj_App:assembleAfatRelease\n\n'
            'Подпись: оригинальный ключ Telegram (android / androidkey).\n'
            'Файлов в архиве: %d\n'
        ) % (version, run, total)
        zf.writestr('README-КАК-СОБРАТЬ.txt', readme)

    size = os.path.getsize(out)
    print('ZIP с исходниками мода: %s (%.1f КБ, файлов: %d)' % (out, size / 1024.0, total))
    return 0


if __name__ == '__main__':
    sys.exit(main())
