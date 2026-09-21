#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KamiGram P80: подключает ThemeHook ко всем экранам приложения.

Патч в ApplicationLoader.onCreate регистрирует ActivityLifecycleCallbacks,
поэтому ThemeHook.apply(activity) вызывается для КАЖДОГО экрана: держит
тёмную iOS-тему, применяет акцент и красит системные полосы. Это заменяет
прошлый подход, когда цвета задавались вручную и текст пропадал на тёмном
фоне.

Использование:
    python3 apply_theme_hook.py <путь к ApplicationLoader.java>
"""

import io
import sys

MARK = 'KAMIGRAM_THEME_HOOK'

# Якорь: начало onCreate(). Патчи мода (например, загрузка ключей) могли уже
# вставить свои строки следом, поэтому цепляемся только за сигнатуру метода.
ANCHOR = '    public void onCreate() {\n'

CALLBACKS = (
    ANCHOR +
    '        /* ' + MARK + ': единая точка дизайна KamiGram — тема и акценты на каждом экране */\n'
    '        try {\n'
    '            registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {\n'
    '                public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {\n'
    '                    org.telegram.messenger.kamigram.ThemeHook.apply(activity);\n'
    '                }\n'
    '                public void onActivityStarted(android.app.Activity activity) {\n'
    '                    org.telegram.messenger.kamigram.ThemeHook.apply(activity);\n'
    '                }\n'
    '                public void onActivityResumed(android.app.Activity activity) {\n'
    '                    org.telegram.messenger.kamigram.ThemeHook.apply(activity);\n'
    '                }\n'
    '                public void onActivityPaused(android.app.Activity activity) {\n'
    '                }\n'
    '                public void onActivityStopped(android.app.Activity activity) {\n'
    '                }\n'
    '                public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {\n'
    '                }\n'
    '                public void onActivityDestroyed(android.app.Activity activity) {\n'
    '                    org.telegram.messenger.kamigram.ThemeHook.forget(activity);\n'
    '                }\n'
    '                public void onActivityPostCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {\n'
    '                }\n'
    '                public void onActivityPostStarted(android.app.Activity activity) {\n'
    '                }\n'
    '                public void onActivityPostResumed(android.app.Activity activity) {\n'
    '                }\n'
    '                public void onActivityPreDestroyed(android.app.Activity activity) {\n'
    '                }\n'
    '                public void onActivityPostDestroyed(android.app.Activity activity) {\n'
    '                }\n'
    '            });\n'
    '        } catch (Throwable kamigramIgnore) {\n'
    '        }\n'
)


def main():
    if len(sys.argv) < 2:
        sys.stderr.write('укажи путь к ApplicationLoader.java\n')
        return 2
    path = sys.argv[1]
    src = io.open(path, encoding='utf-8').read()
    if MARK in src:
        print('P80: хук темы уже подключён')
        return 0
    if ANCHOR not in src:
        sys.stderr.write('P80: не найден onCreate() в ApplicationLoader\n')
        return 1
    src = src.replace(ANCHOR, CALLBACKS, 1)
    io.open(path, 'w', encoding='utf-8').write(src)
    print('P80: хук темы подключён к жизненному циклу экранов')
    return 0


if __name__ == '__main__':
    sys.exit(main())
