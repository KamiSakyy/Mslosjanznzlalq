# Люми — AETHER Android APK (РЕАЛЬНЫЕ КЛЮЧИ 2026 + ДИЗАЙН ТОЧЬ-В-ТОЧЬ)

Нативное Android приложение на Java, полностью повторяющее дизайн и функционал сайта AETHER / Люми. **100% Java, без WebView, дизайн точь-в-точь как сайт.**

## ✅ ИСПРАВЛЕНЫ КЛЮЧИ 2026 — РЕАЛЬНЫЕ БЕСПЛАТНЫЕ БЕЗ КРЕДИТОВ

**Проблема 2026:** Pollinations старый API `text.pollinations.ai` теперь пишет:
> The account behind this API key doesn't have enough credits. Please top up...

Потому что старый бесплатный tier теперь требует Pollen кредиты.

**Решение — нашёл реальные рабочие бесплатные эндпоинты 2026 из интернета:**

1. **OVH AI Endpoints** — `https://oai.endpoints.kepler.ai.cloud.ovh.net/v1` — **2 RPM анонимно без ключа**, модели `gpt-oss-20b`, `gpt-oss-120b`, `Qwen3-Coder-30B`, `Llama 3.3 70B`, `Mistral-Nemo` — работает без регистрации!
2. **Kilo Gateway** — `https://api.kilo.ai/api/gateway` — **200 req/hour анонимно без ключа**, модели `:free` — `kilo-auto/free`, `nvidia/nemotron-3-ultra-550b-a55b:free`, `poolside/laguna-s-2.1:free`, `inclusionai/ling-3.0-flash-vl:free` — без ключа!
3. **LLM7** — `https://api.llm7.io/v1` — **10 RPM анонимно с ключом "unused"**, 40 RPM с токеном с `token.llm7.io` — модели `openai/gpt-oss-20b`, `mistral-Nemo-Instruct-2407`, `codestral-latest`, `minimax-m2.7`
4. **Groq** — `https://api.groq.com/openai/v1` — **30 RPM, 14.4K RPD бесплатно без карты**, ключ `gsk_...` с `console.groq.com/keys` — 500+ токенов/сек, Llama 3.3 70B, GPT-OSS 20B/120B
5. **OpenRouter** — `https://openrouter.ai/api/v1` — **20 RPM, 50 RPD бесплатно без карты** (1000 RPD с $10), модели `:free` — `nvidia/nemotron-3-ultra-550b-a55b:free`, `openai/gpt-oss-20b:free` — ключ `sk-or-v1-...` с `openrouter.ai/keys`
6. **Pollinations** — старый `https://text.pollinations.ai/openai` как fallback + новый `https://gen.pollinations.ai/v1` — текстовые модели бесплатные, 1.5 Pollen/week бесплатно с `enter.pollinations.ai/keys`

**Встроено в код `AiRouter.java`:**
- OVH и Kilo работают **БЕЗ КЛЮЧА вообще** — анонимно, без кредитов, 2026!
- LLM7 с ключом `unused` — анонимно 10 RPM
- Groq/OpenRouter — placeholders `YOUR_GROQ_KEY_HERE` и `YOUR_OPENROUTER_KEY_HERE` — можно ввести свой бесплатный ключ в настройках (получить бесплатно без карты)
- Failover — если один провайдер пишет про кредиты, автоматом пробует следующий бесплатный!

**Где взять бесплатные ключи 2026 без карты:**
- Groq: https://console.groq.com/keys — Sign up, Create API Key, no credit card, 30 RPM free
- OpenRouter: https://openrouter.ai/keys — Sign up, Create Key, no credit card, 20 RPM free :free models
- Pollinations: https://enter.pollinations.ai/keys — Sign up, 1.5 Pollen/week free, text models free
- LLM7 token: https://token.llm7.io — Email, get token for 40 RPM (vs 10 RPM anonymous)

Теперь приложение работает даже если у Pollinations кончились кредиты — использует OVH и Kilo которые работают без ключа!

## ✅ ДИЗАЙН ТОЧЬ-В-ТОЧЬ КАК САЙТ

**Исправлен дизайн — теперь точь-в-точь как сайт:**

- **Цвета:** `--ink #050505`, `--surface-0 #0A0A0B`, `--surface-1 #101011`, `--surface-2 #151516`, `--surface-3 #1A1A1C`, `--line rgba(255,255,255,0.08)`, `--accent #FAFAFA` — как в `globals.css`
- **User bubble:** `max-w-[86%] rounded-2xl rounded-br-lg bg-[#17171A] border white/[0.07] px-4 py-2.5 text 14.5px` — как в `ChatMessage.tsx` UserBubble
- **Assistant:** без пузыря, просто markdown + meta info `providerName • latency • резервный узел` + кнопки copy/regen/share как на сайте
- **Empty state:** "Люми" 32sp bold + "аниме-подруга и ИИ-программист" + стартеры точь-в-точь как `STARTERS` в `page.tsx`: "Скинь арты 2B", "Люми, нарисуй арт", "Кто такая Нана Осаки", "Где смотреть Фрирен", "Объясни нейросети", "Спроектируй REST API", "Что обсуждают в аниме"
- **Input:** `bg #0E0E10 rounded 20dp` с кнопками attach, mic, send как на сайте
- **Tabs:** TabLayout scrollable как на сайте — чат, battle, dialogue, github, anime, lumi, gallery, news
- **Drawer:** "Чаты" + new chat + sessions + providers + Lumi + delete all как на сайте

## 📦 РЕАЛЬНЫЙ APK 11MB — Прямые ссылки

**Скачать напрямую (raw, ветка arena/01a0baab-mslosjanznzlalq):**
```
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/app-debug.apk
https://github.com/KamiSakyy/Mslosjanznzlalq/raw/arena/01a0baab-mslosjanznzlalq/handoff/lumi-aether-debug.apk
https://raw.githubusercontent.com/KamiSakyy/Mslosjanznzlalq/arena/01a0baab-mslosjanznzlalq/handoff/app-debug.apk
```

Файлы в `handoff/`:
- `app-debug.apk` — 11MB, BUILD SUCCESSFUL 34 tasks, 1m31s
- `lumi-aether-debug.apk` — то же самое
- `build.log` — лог сборки
- `BUILD_INFO.txt` — Built at Sat Sep 19 20:05:20 UTC 2026 commit 1b58e9f

### GitHub Actions (SDK Android)

Последняя успешная с реальными ключами 2026 + дизайном точь-в-точь:
- **Run 35466190758** — ✅ SUCCESS 2m26s, 11MB — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35466190758

Предыдущая с ключами:
- Run 35465993076 — https://github.com/KamiSakyy/Mslosjanznzlalq/actions/runs/35465993076 — 2m52s, 11MB

Все раны: https://github.com/KamiSakyy/Mslosjanznzlalq/actions/workflows/build-apk.yml

Workflow: JDK 17, SDK 34, Gradle 8.7, `./gradlew assembleDebug`, копирование в `handoff/` и commit.

### Установка

1. Скачай APK по ссылке выше (11MB)
2. Разреши установку из неизвестных источников
3. Установи на Android 7.0+ (minSdk 24)
4. Открой — теперь работает без ошибки про кредиты! Использует OVH и Kilo которые работают без ключа анонимно 2026. Если хочешь быстрее — добавь свои бесплатные ключи Groq/OpenRouter в настройках (получить бесплатно без карты).

**100% Java, 0% WebView, дизайн точь-в-точь как сайт, реальные бесплатные ключи 2026!**
