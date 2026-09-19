# Люми — AETHER Android APK (РЕАЛЬНЫЙ, РАБОЧИЙ)

Нативное Android приложение на Java, полностью повторяющее дизайн и функционал сайта AETHER / Люми. **100% Java, без WebView, дизайн точь-в-точь.**

## ✅ РЕАЛЬНЫЙ APK - 11MB, собирается через GitHub Actions SDK Android

### Прямые ссылки на APK (из репозитория, ветка arena/01a0baab-mslosjanznzlalq)

**Скачать напрямую (raw):**
```
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/app-debug.apk
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/lumi-aether-debug.apk
```

Файлы в папке `handoff/`:
- `app-debug.apk` — 11MB, debug сборка
- `lumi-aether-debug.apk` — то же самое, второе имя
- `build.log` — лог сборки
- `BUILD_INFO.txt` — информация о сборке

### GitHub Actions сборки (настоящие, через SDK Android)

Последняя успешная сборка с реальным APK:

- **Run 35459780951** — ✅ SUCCESS, 2m5s, 11MB APK, 34 tasks
  https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35459780951
  Артефакт: `app-debug-apk` (скачать из Actions → Artifacts)

Предыдущие успешные:
- Run 35459258477 — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35459258477
- Run 35459140846 — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35459140846
- Run 35458342983 — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35458342983

Все раны workflow:
https://github.com/KamiSakyy/Mslosjanznzlalq/actions/workflows/build-apk.yml

Workflow файл: `.github/workflows/build-apk.yml`
```yaml
- JDK 17 Temurin
- Android SDK 34, build-tools 34.0.0
- Gradle 8.7 wrapper (генерируется на CI)
- ./gradlew assembleDebug --stacktrace
- Копирование в handoff/ и commit обратно в репу
```

Лог последней сборки: `BUILD SUCCESSFUL in 57s, 34 actionable tasks`

### Что внутри (100% нативная Java)

- **Чат с ИИ-роутером**: 19 провайдеров (Pollinations, Kilo, LLM7), failover, стриминг, reasoning, markdown (Markwon), вложения (resize 1280px → base64), голосовой ввод
- **Сессии**: создание, удаление, пин, переименование, экспорт MD, удаление всех
- **Провайдеры**: вкл/выкл, пинг, сброс статистики, бейджи
- **Battle Mode**: 3 модели одновременно, выбор лучшего
- **Dialogue Mode**: бесконечный диалог между ИИ-агентами
- **Аниме каталог**: Shikimori API, поиск, фильтры (популярные/онгоинги/вышло/анонсы), календарь, добавление в подписки
- **Люми подписки**: отслеживание новых серий каждые 10 мин, toast + уведомление
- **GitHub-чат**: PAT токен, список репозиториев, чат по коду с ИИ-архитектором
- **Галерея артов**: Danbooru + Pollinations, поиск, полноэкранный просмотр
- **Новости**, **Видео плеер** (HLS via ExoPlayer Media3), **Character Search**, **Image Generation**

Дизайн точь-в-точь как сайт: тёмная тема #050505, поверхности #101011, скругления 12-20dp, Material3.

### Установка

1. Скачай APK по прямой ссылке выше (11MB)
2. Разреши установку из неизвестных источников
3. Установи на Android 7.0+ (minSdk 24, targetSdk 34)
4. Открой — это нативное приложение, не WebView!

### Технологии

- Java 17, AndroidX, Material3, RecyclerView, ViewPager2, DrawerLayout, TabLayout
- OkHttp 4.12.0 + Gson 2.10.1, Glide 4.16.0, Media3 ExoPlayer 1.4.1, Markwon 4.6.2
- SQLite DatabaseHelper, SharedPreferences, LumiStore
- 100% Java, 0% WebView

Собрано через GitHub Actions SDK Android — настоящее приложение!
