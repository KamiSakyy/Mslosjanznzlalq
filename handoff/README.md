# Люми — AETHER Android APK

Нативное Android приложение на Java, полностью повторяющее дизайн и функционал сайта AETHER / Люми.

## Что внутри (100% нативная Java, без WebView)

- **Чат с ИИ-роутером**: 19 провайдеров (Pollinations, Kilo, LLM7), failover, стриминг, reasoning, markdown, вложения, голосовой ввод
- **Сессии**: создание, удаление, пин, переименование, экспорт в Markdown, удаление всех
- **Провайдеры**: включение/выключение, пинг, сброс статистики, бейджи
- **Battle Mode**: 3 модели отвечают одновременно, выбор лучшего
- **Dialogue Mode**: бесконечный диалог между ИИ-агентами
- **Аниме каталог**: Shikimori API, поиск, фильтры (популярные/онгоинги/вышло/анонсы), календарь, добавление в подписки
- **Люми подписки**: отслеживание новых серий каждые 10 минут, уведомления
- **GitHub-чат**: подключение по PAT токену, список репозиториев, чат по коду с ИИ-архитектором
- **Галерея артов**: Danbooru + Pollinations, поиск, полноэкранный просмотр
- **Новости**: аниме-сообщество, Shikimori
- **Видео плеер**: HLS через ExoPlayer / Media3
- **Image Generation**: Pollinations Flux
- **Character Search**: Shikimori + Jikan (MyAnimeList)
- **Дизайн**: точь-в-точь как сайт — тёмная тема #050505, поверхности #101011, скругления 12-20dp, шрифты, анимации

## Сборка APK через GitHub Actions

APK собирается автоматически через workflow `.github/workflows/build-apk.yml`:

- JDK 17 (Temurin)
- Android SDK 34, build-tools 34.0.0
- Gradle 8.7 (wrapper генерируется на CI через `gradle/actions/setup-gradle`)
- Команда: `./gradlew assembleDebug`

Готовый APK попадает в:
- Артефакты workflow: `app-debug-apk`
- Папка `handoff/` в репозитории: `app-debug.apk` и `lumi-aether-debug.apk`

## Ссылка на APK

После прогона GitHub Actions скачайте APK из:

- **Actions → Build APK → Artifacts → app-debug-apk**
- Или прямо из папки `handoff/app-debug.apk` в этом репозитории (после того как workflow скопирует туда файл)

Локально APK лежит по пути: `app/build/outputs/apk/debug/app-debug.apk`

## Установка

1. Скачайте `app-debug.apk`
2. Разрешите установку из неизвестных источников
3. Установите на Android 7.0+ (minSdk 24, targetSdk 34)

## Технологии

- Java 17, AndroidX, Material3
- OkHttp + Gson, Glide, Media3 ExoPlayer, Markwon (Markdown)
- SQLite (DatabaseHelper), SharedPreferences
- RecyclerView, ViewPager2, DrawerLayout, TabLayout

Полностью нативная Java, ни одной строчки WebView.
