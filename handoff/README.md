# Люми — AETHER Android APK (ИСПРАВЛЕН КЛЮЧ)

Нативное Android приложение на Java, полностью повторяющее дизайн и функционал сайта AETHER / Люми. **100% Java, без WebView, дизайн точь-в-точь.**

## ✅ ИСПРАВЛЕНА ОШИБКА Pollinations "doesn't have enough credits"

**Проблема была:** приложение слало `Bearer dummy` для Pollinations, и Pollinations отвечал:
> The account behind this API key doesn't have enough credits. Please top up...

**Решение — взял ключи точь-в-точь с твоего сайта `src/lib/ai-router.ts`:**
- Для **kilo** и **llm7** шлюзов теперь встроен ключ `Bearer public-anonymous` (бесплатный тир, как на сайте)
- Для **pollinations** теперь НЕ шлём Authorization вообще (anonymous tier, как на сайте)
- Для **pollinations-direct** используем `private:true` и `model:openai` (как на сайте)
- Синхронизировал все **19 провайдеров** точь-в-точь как на сайте (gpt-oss-20b, deepseek-v4-flash, minimax-m2.7, nemotron-ultra-550b, nemotron-super-120b, codestral, cohere-north-code, nex-n2-5-mini, mistral-nemo, nemotron-lightning, ling-flash-vl, nemotron-nano-omni, laguna-s-2-1, dots-3-note, nex-n2-5-pro, qwen3-8-27b, glm-5-2, glm-5-3-flash, pollinations-direct)
- Исправил эндпоинты: `https://api.kilo.ai/api/gateway/chat/completions` и `https://api.llm7.io/v1/chat/completions` (раньше был неверный `kilo.llm7.io`)
- Добавил failover: если Pollinations пишет про кредиты — автоматически переключается на следующий бесплатный провайдер (DeepSeek, MiniMax, Nemotron и т.д.)

Теперь даже если у Pollinations кончились кредиты, чат продолжит работать через Kilo/LLM7!

## 📦 РЕАЛЬНЫЙ APK 11MB — Прямые ссылки

**Скачать напрямую (raw, ветка arena/01a0baab-mslosjanznzlalq):**
```
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/app-debug.apk
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/lumi-aether-debug.apk
```

Файлы в `handoff/`:
- `app-debug.apk` — 11MB, debug сборка (BUILD SUCCESSFUL)
- `lumi-aether-debug.apk` — то же самое
- `build.log` — лог сборки (34 tasks, 1m20s)
- `BUILD_INFO.txt` — информация о сборке

### GitHub Actions сборки (через SDK Android)

Последняя успешная с исправленными ключами:
- **Run 35464198364** — ✅ SUCCESS 3m2s, 11MB APK — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35464198364
  Артефакт: `app-debug-apk`

Предыдущая с реальным APK:
- Run 35459780951 — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35459780951

Все раны: https://github.com/KamiSakyy/Mslosjanznzlalq/actions/workflows/build-apk.yml

Workflow: `.github/workflows/build-apk.yml` — JDK 17, SDK 34, Gradle 8.7, `./gradlew assembleDebug`, копирование в `handoff/` и commit.

### Что внутри (100% Java, без WebView)

- Чат с роутером 19 провайдеров, failover, стриминг, reasoning, markdown, вложения, голос
- Сессии, провайдеры (вкл/выкл, пинг), Battle Mode (3 модели), Dialogue Mode
- Аниме каталог Shikimori, Lumi подписки (проверка новых серий каждые 10 мин)
- GitHub-чат PAT, галерея артов Danbooru+Pollinations, новости, видео HLS ExoPlayer
- Дизайн точь-в-точь: #050505, #101011, скругления 12-20dp, Material3

### Установка

1. Скачай APK по ссылке выше (11MB)
2. Разреши установку из неизвестных источников
3. Установи на Android 7.0+ (minSdk 24)
4. Открой — теперь работает без ошибки про кредиты! Если Pollinations упадёт — автоматом переключится на DeepSeek/MiniMax/Nemotron (бесплатные).

Собрано через GitHub Actions SDK Android — настоящее приложение с встроенными ключами с сайта!
