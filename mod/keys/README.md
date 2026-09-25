# Ключ подписи Sakura

Свой ключ подписи для APK мода. Тип: **JKS**, RSA 4096, срок 30 лет.

| Параметр | Значение |
|---|---|
| Файл | `kamigram.jks` (+ `kamigram.jks.b64` — та же копия в base64 для CI) |
| Alias | `kamigram` |
| Пароль хранилища / ключа | `kamigram2026` |
| Владелец сертификата | `CN=Sakura, OU=Mod, O=Sakura, L=Telegram Mod, ST=NRW, C=DE` |

## Важно про публичность

Ключ лежит в **публичном** репозитории, то есть приватным не является (ровно так же, как публичен
upstream-ключ Telegram `android` / `androidkey` / `android`). Это сделано осознанно: подпись
**стабильна между сборками**, поэтому новые APK мода ставятся «поверх» старых без удаления.

Хочешь приватный ключ — сгенерируй свой и передай в патчер:

```bash
KEYSTORE_B64=$(base64 -w0 my.jks) \
KEYSTORE_PASSWORD=... KEY_ALIAS=... KEY_PASSWORD=... \
TG_DIR=./telegram-src bash mod/apply-mod.sh
```

и убери `mod/keys/*` из репозитория.

## Пересоздать ключ

Сгенерировать заново (сменится подпись → старую версию придётся удалить с телефона):

* Actions → **Generate Keystore (один раз)** → Run workflow → `force = true`,
* либо вручную:

```bash
keytool -genkeypair -v -keystore mod/keys/kamigram.jks -storetype JKS \
  -alias kamigram -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass kamigram2026 -keypass kamigram2026 \
  -dname "CN=Sakura, OU=Mod, O=Sakura, L=Telegram Mod, ST=NRW, C=DE"
base64 -w0 mod/keys/kamigram.jks > mod/keys/kamigram.jks.b64
```

## Проверить подпись готового APK

```bash
apksigner verify --print-certs KamiGram-*.apk
```
