# Люми — AETHER Android APK

Нативное Android приложение на Java, полностью повторяющее дизайн и функционал сайта AETHER / Люми.

## Что внутри (100% нативная Java, без WebView)

- **Чат с ИИ-роутером**: 19 провайдеров (Pollinations, Kilo, LLM7), failover, стриминг, reasoning, markdown, вложения, голосовой ввод
- **Сессии**: создание, удаление, пин, переименование, экспорт в Markdown
- **Провайдеры**: включение/выключение, пинг, сброс статистики
- **Battle Mode**: 3 модели отвечают одновременно
- **Dialogue Mode**: бесконечный диалог между ИИ-агентами
- **Аниме каталог**: Shikimori API, поиск, фильтры, календарь, подписки Люми
- **Люми подписки**: отслеживание новых серий каждые 10 минут
- **GitHub-чат**: подключение по PAT, список репо, чат по коду
- **Галерея артов**: Danbooru + Pollinations, генерация изображений
- **Новости**, **Видео плеер** (HLS via ExoPlayer), **Character Search**

Дизайн точь-в-точь как сайт: тёмная тема #050505, поверхности #101011, скругления 12-20dp.

## Сборка APK через GitHub Actions

APK собирается автоматически через workflow `.github/workflows/build-apk.yml`:

- JDK 17 Temurin
- Android SDK 34, build-tools 34.0.0
- Gradle 8.7 (wrapper генерируется на CI)
- Команда: `./gradlew assembleDebug`

### Ссылки на APK

#### 1. Прямая ссылка из репозитория (handoff папка)
- `handoff/app-debug.apk` - в этом репозитории, ветка `arena/01a0baab-mslosjanznzlalq`
- `handoff/lumi-aether-debug.apk` - то же самое, второе имя

Скачать напрямую:
```
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/app-debug.apk
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/lumi-aether-debug.apk
```

#### 2. Артефакты GitHub Actions (настоящая сборка)
Последние успешные сборки:

- **Run 35458342983** (успешная, 1m46s):
  https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35458342983
  Артефакт: `app-debug-apk` (скачать из Actions → Artifacts)

- **Run 35458079929** (успешная, 1m48s):
  https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35458079929

Все раны:
https://github.com/KamiSakyy/Mslosjanznzlalq/actions/workflows/build-apk.yml

#### 3. Локальный путь после сборки
`app/build/outputs/apk/debug/app-debug.apk`

## Установка

1. Скачайте APK по одной из ссылок выше
2. Разрешите установку из неизвестных источников
3. Установите на Android 7.0+ (minSdk 24, targetSdk 34)

## Технологии

- Java 17, AndroidX, Material3, RecyclerView, ViewPager2, DrawerLayout
- OkHttp + Gson, Glide, Media3 ExoPlayer, Markwon (Markdown)
- SQLite (DatabaseHelper), SharedPreferences, LumiStore
- 100% Java, 0% WebView, дизайн точь-в-точь как сайт

## Структура проекта

```
app/src/main/java/com/aether/app/
├── MainActivity.java (главная + 9 фрагментов)
├── models/ (ChatMessage, ChatSession, AIProvider, AnimeCard...)
├── data/ (DatabaseHelper, LumiStore, PreferencesManager)
├── network/ (AiRouter, AnimeApi, ImageSearchApi, GithubApi, CharacterApi)
├── ui/ (ChatAdapter, SessionsAdapter, VideoPlayerActivity...)
└── utils/ (TimeUtils, MarkdownRenderer)
```

Полностью нативная Java, ни одной строчки Web.
