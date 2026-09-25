#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""r107: жёсткая защита APK — R8/обфускация/словари имён.

Что делает (только на этапе сборки; исходники остаются читаемыми):

1. Проверяет, что release-сборка TMessagesProj_App минифицируется R8
   (minifyEnabled/shrinkResources/fullMode) — это базовая защита.
2. Подключает дополнительные правила `proguard-sakura.pro`:
   * словари обфускации из визуально неразличимых имён (O/0/l/1/I) —
     декомпилированный код превращается в «лапшу»;
   * -allowaccessmodification — R8 агрессивнее сливает и оптимизирует код,
     APK становится меньше.
3. Ничего не шифрует в исходниках: вся защита выполняется сборщиком R8,
   в репозитории остаётся обычный читаемый Java-код.

Запуск: python3 apply_r107_hardening.py <TG_DIR>
"""
import io
import os
import sys

TG = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TG_DIR", ".")
DONE = []
FAIL = []


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


def die(msg):
    FAIL.append(msg)


KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "in", "instanceof", "int", "interface", "is", "let", "long",
    "module", "native", "new", "null", "open", "or", "package", "private",
    "protected", "public", "record", "return", "sealed", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "to", "transient", "try", "var", "void", "volatile", "while", "yield",
}


def build_dictionary():
    """Имена, которые невозможно читать: O/0, l/1/I смешиваются в глазах."""
    confusing = ["O", "0", "l", "1", "I", "i", "j", "c", "a", "e", "d", "g"]
    firsts = ["O", "l", "I", "i", "j", "c", "a", "e", "d", "g", "b", "f",
              "h", "k", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
              "w", "x", "y", "z"]
    words = []
    seen = set()
    for a in firsts:
        if a not in KEYWORDS and a not in seen:
            seen.add(a)
            words.append(a)
    for a in firsts:
        for b in confusing:
            word = a + b
            if word in KEYWORDS or word in seen:
                continue
            seen.add(word)
            words.append(word)
    for a in firsts:
        for b in confusing:
            for c in confusing:
                word = a + b + c
                if word in KEYWORDS or word in seen:
                    continue
                seen.add(word)
                words.append(word)
                if len(words) >= 2500:
                    return words
    return words


app_gradle = os.path.join(TG, "TMessagesProj_App/build.gradle")
props = os.path.join(TG, "gradle.properties")
rules_dir = os.path.join(TG, "TMessagesProj")

if not os.path.isfile(app_gradle):
    die("TMessagesProj_App/build.gradle не найден")
else:
    gradle_text = read(app_gradle)

    # 1) базовая защита уже должна быть включена (release: minify + shrink)
    if "minifyEnabled true" not in gradle_text:
        die("в release не включена минификация R8 (minifyEnabled true)")
    else:
        DONE.append("release: минификация R8 включена")
    if "shrinkResources true" not in gradle_text:
        die("в release не включено сжатие ресурсов (shrinkResources true)")
    else:
        DONE.append("release: сжатие ресурсов включено")

    if os.path.isfile(props) and "enableR8.fullMode=true" in read(props):
        DONE.append("R8 работает в full mode")
    else:
        die("R8 full mode не включён в gradle.properties")

    # 2) словарь обфускации + правила Sakura
    dict_path = os.path.join(rules_dir, "proguard-sakura-dict.txt")
    write(dict_path, "\n".join(build_dictionary()) + "\n")
    DONE.append("словарь обфускации: %d имён" % len(build_dictionary()))

    sakura_rules = os.path.join(rules_dir, "proguard-sakura.pro")
    abs_dict = os.path.abspath(dict_path)
    write(sakura_rules, "\n".join([
        "# Sakura hardening (r107): применяется только сборщиком R8.",
        "# Исходники не шифруются — имена портятся лишь внутри APK.",
        "-obfuscationdictionary %s" % abs_dict,
        "-classobfuscationdictionary %s" % abs_dict,
        "-packageobfuscationdictionary %s" % abs_dict,
        "-allowaccessmodification",
        "",
    ]))
    DONE.append("правила proguard-sakura.pro созданы")

    # 3) подключить правила к release-сборке приложения
    if "proguard-sakura.pro" in gradle_text:
        DONE.append("build.gradle: правила Sakura уже подключены")
    else:
        gradle_text += (
            "\n// Sakura hardening (r107): обфускация имён и агрессивная"
            " оптимизация R8.\n"
            "android.buildTypes.release.proguardFiles("
            "new File(rootDir, 'TMessagesProj/proguard-sakura.pro'))\n"
        )
        write(app_gradle, gradle_text)
        DONE.append("build.gradle: правила Sakura подключены к release")

print("=== r107 hardening done ===")
for line in DONE:
    print("  + " + line)
if FAIL:
    print("=== r107 hardening FAILED ===")
    for line in FAIL:
        print("  ! " + line)
    sys.exit(1)
