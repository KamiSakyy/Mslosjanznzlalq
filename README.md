# Люми — AETHER (Android Java)

Полный нативный порт сайта AETHER / Люми на Android Java (без единой строчки Web).

## Сайт оригинал

Оригинальный сайт (Next.js) находится в `fix-images-and-upgrade-lumi (1).zip`:
- Чат с роутером ИИ (19 провайдеров: Pollinations, Kilo, LLM7)
- Сессии, провайдеры, Battle Mode, Dialogue Mode
- Аниме каталог (Shikimori, AniLibria), Lumi подписки, календарь
- GitHub-чат, галерея артов, новости, видео плеер, markdown

## Android приложение

Весь функционал переписан на **чистую Java Android**:

```
app/src/main/java/com/aether/app/
├── MainActivity.java (главная активити + 9 фрагментов)
├── models/ (ChatMessage, ChatSession, AIProvider, AnimeCard, etc.)
├── data/ (DatabaseHelper SQLite, LumiStore, PreferencesManager)
├── network/ (AiRouter, AnimeApi, ImageSearchApi, GithubApi, CharacterApi)
├── ui/ (ChatAdapter, SessionsAdapter, AnimeDetail, VideoPlayer)
└── utils/ (TimeUtils, MarkdownRenderer)
```

### Дизайн точь-в-точь

- Тёмная тема `#050505`, поверхности `#0A0A0B`, `#101011`, `#151516`
- Скругления 12-20dp, карточки, чипы, боттом-шит
- Иконки, типографика, анимации как на сайте

### Сборка APK через GitHub Actions

Workflow: `.github/workflows/build-apk.yml`

```yaml
- JDK 17 Temurin
- Android SDK 34, build-tools 34.0.0
- Gradle 8.7 (wrapper генерируется на CI)
- ./gradlew assembleDebug
```

APK попадает в:
- `app/build/outputs/apk/debug/app-debug.apk`
- `handoff/app-debug.apk` (копируется в workflow)
- Артефакты GitHub Actions: `app-debug-apk`

### Ссылка на APK

1. Откройте вкладку **Actions** в GitHub
2. Выберите последний запуск **Build APK**
3. Скачайте артефакт **app-debug-apk**
4. Или скачайте напрямую из папки `handoff/` после сборки

Локальный путь после сборки: `handoff/app-debug.apk`

### Установка

- Android 7.0+ (minSdk 24)
- Разрешите установку из неизвестных источников
- Установите `app-debug.apk`

### Функции

- Чат с ИИ-роутером, стриминг, failover, markdown, вложения, голос
- Сессии, пин, удаление, экспорт
- Battle Mode (3 модели), Dialogue Mode (бесконечный)
- Аниме каталог, подписки Люми, проверка новых серий
- GitHub подключение, галерея артов, новости, видео HLS

**100% Java, 0% WebView.**
