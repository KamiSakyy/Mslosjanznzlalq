#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Проверка api_id/api_hash на живом сервере Telegram.

Ключи официальных клиентов и популярных модов: если сервер отклонил ключ, он
отвечает API_ID_PUBLISHED_FLOOD / API_ID_INVALID ещё до отправки кода, поэтому
для проверки достаточно заведомо неверного номера.
"""
import asyncio
import sys


KEYS = [
    ('MDGram / Telegram Android (публичный)', 4, '014b35b6184100b085b0d0572f9b5103'),
    ('Telegram Desktop (Win beta)', 2040, 'b18441a1ff607e10a989891a5462e627'),
    ('TG for Android (Play)', 6, 'eb06d4abfb49dc3eeb1aeb98ae0f581e'),
    ('Public Static Final', 5, '1c5c96d5edd401b1ed40db3fb5633e2d'),
    ('Telegram X (Android)', 21724, '3e0cb5efcd52300aec5994fdfc5bdc16'),
    ('Nicegram / TDLib', 94575, 'a3406de8d171bb422bb6ddf3bbd800e2'),
    ('Plus Messenger', 16623, '8c9dbfe58437d1739540f5d53c72ae4b'),
    ('Telegram Desktop (example)', 17349, '344583e45741c457fe1862106095a5eb'),
    ('Telegram macOS beta', 2834, '68875f756c9b437a8b916ca3de215815'),
    ('Public unknown beta', 9, '3975f648bb682ee889f35483bc618d1c'),
    ('Telegram Web', 2496, '8da85b0d5bfe62527e5b244c209159c3'),
    ('Telegram Web K', 1025907, '452b0359b988148995f22ff0f4229750'),
    ('Telegram iOS beta', 8, '7245de8e747a0d6fbe11f7cc14fcc0bb'),
    ('Telegram Swift', 10840, '33c45224029d59cb3ad0c16134215aeb'),
    ('Telegram CLI', 2899, '36722c72256a24c1225de00eb6a1ca74'),
]


async def check(name, api_id, api_hash):
    try:
        from telethon import TelegramClient
    except ImportError:
        print('telethon не установлен', file=sys.stderr)
        return 'нет telethon'
    client = TelegramClient('/tmp/s_key_%d' % api_id, api_id, api_hash,
                            timeout=20, connection_retries=1, request_retries=1)
    verdict = 'неизвестно'
    try:
        await client.connect()
        try:
            await client.send_code_request('+99999999999')
            verdict = 'ПРИНЯТ'
        except Exception as e:
            message = str(e)
            upper = message.upper()
            if 'API_ID' in upper:
                verdict = 'ОТКЛОНЁН: ' + message[:90]
            elif 'PHONE' in upper or 'NUMBER' in upper:
                verdict = 'ПРИНЯТ (ответ по номеру: ' + message[:70] + ')'
            else:
                verdict = type(e).__name__ + ': ' + message[:90]
    except Exception as e:
        verdict = 'НЕТ СВЯЗИ: ' + type(e).__name__ + ': ' + str(e)[:80]
    finally:
        try:
            await client.disconnect()
        except Exception:
            pass
    return verdict


async def main():
    print()
    print('== ПРОВЕРКА КЛЮЧЕЙ НА ЖИВОМ СЕРВЕРЕ TELEGRAM ==')
    for name, api_id, api_hash in KEYS:
        verdict = await check(name, api_id, api_hash)
        print('%-8s %-38s %s' % (api_id, name, verdict))
        sys.stdout.flush()


if __name__ == '__main__':
    asyncio.run(main())
