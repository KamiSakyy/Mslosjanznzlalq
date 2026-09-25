#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_SAKURA_BRAND_R101 — внутри приложения «Sakura», а не «Telegram».

Пользователь: «внутри приложения название должно быть Sakura, а не Telegram» и
«кнопку "Возможности Telegram" переименуй в "Sakura канал", по нажатию открывай
канал @AsuMeo».

Патч делает два слоя:

  1. Ресурсы (res/values*/strings.xml). Переписываются видимые строки во всех
     языковых каталогах. Не трогаются:
        * строки-идентификаторы (имя заканчивается на Url/Scheme/Action/...),
        * ссылки и домены (https://telegram.org, t.me/..., tg://...),
        * пакеты и authority (org.telegram.messenger.provider),
        * хэндлы (@Telegram) и технические токены (telegram_bot).
     Отдельно TelegramFeaturesUrl → https://t.me/AsuMeo,
     TelegramFeatures → «Sakura Channel» (в values-ru — «Sakura канал»).

  2. Рантайм (LocaleController). Облачные языковые пакеты Telegram применяет
     поверх ресурсов уже после старта, поэтому строки дополнительно проходят
     через KamiGramBranding.localize() в getStringInternal()/getServerString()
     и в кнопке настроек.

Запуск: python3 apply_branding.py <путь до TMessagesProj/src/main>
"""

import io
import os
import re
import sys

MARKER = "KAMIGRAM_SAKURA_BRAND_R101"
CHANNEL_URL = "https://t.me/AsuMeo"
BRAND = "Telegram"
# Символ ПЕРЕД «telegram», который означает идентификатор/ссылку, а не текст:
#   org.telegram.messenger, t.me/Telegram, tg:Telegram, ?domain=telegram, @Telegram
PREV_TECHNICAL = "./:=@#&%~|;"
# Символ ПОСЛЕ «telegram» с тем же смыслом. Точка проверяется отдельно:
# «telegram.org» — домен (не трогаем), «Telegram.» в конце предложения — текст.
NEXT_TECHNICAL = "/:=#&%@~|;"
DOMAIN_SUFFIX = re.compile(r"\.[A-Za-z]{2,8}(?![A-Za-z])")  # match(value, pos): без «^»
# Символы, на которых обрывается «токен» при проверке на ссылку: разметка XML и
# escape-последовательности не являются частью URL.
TOKEN_STOP = "<>\"'\\;&"
# Строка-значение, которая целиком является ссылкой/доменом/authority: такие не
# переписываем (по имени ресурса определять нельзя — «Re-action» ловило «Action»).
URL_VALUE = re.compile(r"^\s*(?:https?://|tg://|www\.)", re.I)
PURE_DOMAIN = re.compile(r"^\s*[A-Za-z0-9_.:/?=&%#@+~-]*telegram[A-Za-z0-9_.:/?=&%#@+~-]*\s*$", re.I)
STRING_TAG = re.compile(r"(<string name=\"([A-Za-z0-9_]+)\"[^>]*>)(.*?)(</string>)", re.S)


# --------------------------------------------------------------------- бренд


def _match_case(word):
    if word.isupper():
        return "SAKURA"
    if word.islower():
        return "sakura"
    return "Sakura"


def _is_technical(value, start, end):
    # «telegram» со подчёркиванием — идентификатор (telegram_bot), «Telegram» —
    # текст, в том числе в markdown-разметке __Telegram Premium.__
    lowercase = value[start] == "t"
    if start > 0:
        before = value[start - 1]
        if before in PREV_TECHNICAL or before.isalnum():
            return True
        if before == "_" and lowercase:
            return True
    if end < len(value):
        char = value[end]
        if char in NEXT_TECHNICAL or char.isalnum():
            return True
        if char == "_" and lowercase:
            return True
        if char == "." and DOMAIN_SUFFIX.match(value, end):
            return True
    left = start
    while left > 0 and not value[left - 1].isspace() and value[left - 1] not in TOKEN_STOP:
        left -= 1
    right = end
    while right < len(value) and not value[right].isspace() and value[right] not in TOKEN_STOP:
        right += 1
    token = value[left:right]
    return "://" in token or token.startswith("www.") or "@" in token


def is_technical_value(value):
    """Значение целиком ссылка/домен/authority — бренд внутри него не текст.

    Одиночное «Telegram» (NotificationHiddenName, Page1Title, SecretChatName и
    т.п.) доменом НЕ считается: точка/слэш/двоеточие обязательны, иначе
    видимое название приложения осталось бы прежним.
    """
    if not value:
        return False
    stripped = value.strip()
    if URL_VALUE.match(stripped):
        return True
    has_structure = any(char in stripped for char in "./:")
    return bool(has_structure and PURE_DOMAIN.match(stripped))


def rebrand(value):
    """«Telegram» → «Sakura», не трогая ссылки, домены и идентификаторы."""
    if not value or "elegram" not in value and "ELEGRAM" not in value:
        return value, 0
    lower = value.lower()
    out = []
    index = 0
    count = 0
    found = lower.find("telegram", index)
    while found >= 0:
        end = found + len(BRAND)
        out.append(value[index:found])
        if _is_technical(value, found, end):
            out.append(value[found:end])
        else:
            out.append(_match_case(value[found:end]))
            count += 1
        index = end
        found = lower.find("telegram", index)
    out.append(value[index:])
    return "".join(out), count


# ------------------------------------------------------------------- ресурсы


def patch_strings_xml(res_root):
    total_files = 0
    total_replacements = 0
    for name in sorted(os.listdir(res_root)):
        if not name.startswith("values"):
            continue
        path = os.path.join(res_root, name, "strings.xml")
        if not os.path.isfile(path):
            continue
        with io.open(path, encoding="utf-8") as handle:
            source = handle.read()
        russian = name == "values-ru" or name.startswith("values-ru")
        stats = {"count": 0}

        def replace(match):
            opening, key, value, closing = match.group(1), match.group(2), match.group(3), match.group(4)
            if key == "TelegramFeaturesUrl":
                stats["count"] += 1
                return opening + CHANNEL_URL + closing
            if key == "TelegramFeatures":
                stats["count"] += 1
                return opening + ("Sakura канал" if russian else "Sakura Channel") + closing
            if is_technical_value(value):
                return match.group(0)
            updated, count = rebrand(value)
            if count:
                stats["count"] += count
                return opening + updated + closing
            return match.group(0)

        updated_source = STRING_TAG.sub(replace, source)
        if updated_source != source:
            with io.open(path, "w", encoding="utf-8") as handle:
                handle.write(updated_source)
            total_files += 1
            total_replacements += stats["count"]
    return total_files, total_replacements


# ---------------------------------------------------------------------- код


def patch_file(path, pairs, label):
    if not os.path.isfile(path):
        return False, "нет файла %s" % label
    with io.open(path, encoding="utf-8") as handle:
        source = handle.read()
    if MARKER in source:
        return True, "%s: уже применено" % label
    updated = source
    for old, new in pairs:
        if old not in updated:
            return False, "%s: не найден якорь %r" % (label, old[:70])
        updated = updated.replace(old, new, 1)
    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(updated)
    return True, "%s: бренд Sakura применён" % label


def patch_locale_controller(java_root):
    path = os.path.join(java_root, "org", "telegram", "messenger", "LocaleController.java")
    pairs = [
        (
            """        if (value == null) {
            value = "LOC_ERR:" + key;
        }
        return value;
    }
""",
            """        if (value == null) {
            value = "LOC_ERR:" + key;
        }
        if (value.startsWith("LOC_ERR:")) {
            return value;
        }
        /* %s: облачный языковой пакет не может вернуть «Telegram» в интерфейс */
        return org.telegram.messenger.kamigram.KamiGramBranding.localize(
            key != null ? key : fallback, value);
    }
""" % MARKER,
        ),
        (
            """    public static String getServerString(String key) {
        String value = getInstance().localizationExternal.getByResName(key);
        if (value == null) {
            int resourceId = getLocalizedStringByName(key);
            if (resourceId != 0) {
                value = getInstance().getLocalizedString(resourceId);
            }
        }
        return value;
    }
""",
            """    public static String getServerString(String key) {
        String value = getInstance().localizationExternal.getByResName(key);
        if (value == null) {
            int resourceId = getLocalizedStringByName(key);
            if (resourceId != 0) {
                value = getInstance().getLocalizedString(resourceId);
            }
        }
        return org.telegram.messenger.kamigram.KamiGramBranding.localize(key, value); /* %s */
    }
""" % MARKER,
        ),
    ]
    return patch_file(path, pairs, "LocaleController")


def patch_settings_activity(java_root):
    path = os.path.join(java_root, "org", "telegram", "ui", "SettingsActivity.java")
    pairs = [
        (
            "R.drawable.settings_features, getString(R.string.TelegramFeatures)))",
            "R.drawable.settings_features, org.telegram.messenger.kamigram.KamiGramBranding.featuresTitle())) /* %s: «Sakura канал» */" % MARKER,
        ),
        (
            "                    Browser.openUrl(getContext(), LocaleController.getString(R.string.TelegramFeaturesUrl));",
            "                    Browser.openUrl(getContext(), org.telegram.messenger.kamigram.KamiGramBranding.CHANNEL_URL); /* %s: канал разработчика */" % MARKER,
        ),
    ]
    return patch_file(path, pairs, "SettingsActivity")


def patch_link_manager(java_root):
    path = os.path.join(java_root, "org", "telegram", "ui", "LinkManager.java")
    if not os.path.isfile(path):
        return True, "LinkManager: файла нет, пропускаем"
    with io.open(path, encoding="utf-8") as handle:
        source = handle.read()
    needle = "Browser.openUrl(activity, LocaleController.getString(R.string.TelegramFeaturesUrl));"
    if MARKER in source:
        return True, "LinkManager: уже применено"
    if needle not in source:
        return False, "LinkManager: не найден якорь открытия TelegramFeaturesUrl"
    source = source.replace(
        needle,
        "Browser.openUrl(activity, org.telegram.messenger.kamigram.KamiGramBranding.CHANNEL_URL); /* %s */" % MARKER,
        1,
    )
    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(source)
    return True, "LinkManager: «Sakura канал» открывает @AsuMeo"


def verify(java_root, res_root):
    problems = []
    branding = os.path.join(java_root, "org", "telegram", "messenger", "kamigram", "KamiGramBranding.java")
    if not os.path.isfile(branding):
        problems.append("нет KamiGramBranding.java в дереве")
    else:
        with io.open(branding, encoding="utf-8") as handle:
            source = handle.read()
        for needle in ("CHANNEL_URL", "featuresTitle", "localize", "public static String text("):
            if needle not in source:
                problems.append("в KamiGramBranding.java нет %s" % needle)

    base = os.path.join(res_root, "values", "strings.xml")
    with io.open(base, encoding="utf-8") as handle:
        strings = handle.read()
    if "<string name=\"TelegramFeaturesUrl\">" + CHANNEL_URL + "</string>" not in strings:
        problems.append("TelegramFeaturesUrl не указывает на %s" % CHANNEL_URL)
    if "<string name=\"TelegramFeatures\">Sakura Channel</string>" not in strings:
        problems.append("TelegramFeatures не переименован в Sakura Channel")
    if re.search(r"<string name=\"AppName\">(?!Sakura)", strings):
        problems.append("AppName не Sakura")
    # видимых отдельных слов «Telegram» в базовых строках остаться не должно
    leftovers = []
    for match in STRING_TAG.finditer(strings):
        key, value = match.group(2), match.group(3)
        if is_technical_value(value):
            continue
        _again, count = rebrand(value)
        if count:
            leftovers.append(key)
    if leftovers:
        problems.append("в values/strings.xml остался бренд Telegram: %s" % ", ".join(leftovers[:12]))
    return problems


def main(argv):
    if len(argv) != 2:
        sys.stderr.write("usage: apply_branding.py <TMessagesProj/src/main>\n")
        return 2
    main_root = argv[1]
    java_root = os.path.join(main_root, "java")
    res_root = os.path.join(main_root, "res")
    if not os.path.isdir(java_root) or not os.path.isdir(res_root):
        sys.stderr.write("BRAND: ожидался путь .../TMessagesProj/src/main\n")
        return 2

    results = []
    failed = False
    for step in (patch_locale_controller, patch_settings_activity, patch_link_manager):
        ok, message = step(java_root)
        results.append(message)
        failed = failed or not ok

    if failed:
        for message in results:
            sys.stderr.write("BRAND: %s\n" % message)
        return 1

    files, replacements = patch_strings_xml(res_root)
    results.append("strings.xml: файлов %d, замен %d" % (files, replacements))

    problems = verify(java_root, res_root)
    for message in results:
        print("BRAND: %s" % message)
    for problem in problems:
        sys.stderr.write("BRAND: %s\n" % problem)
    if problems:
        return 1
    print("BRAND: внутри приложения бренд Sakura, «Sakura канал» открывает %s" % CHANNEL_URL)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
