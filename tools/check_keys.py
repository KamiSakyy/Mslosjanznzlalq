#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Проверка api_id/api_hash на живом сервере Telegram.

Каждой паре выдаётся СВЕЖИЙ заведомо недействительный номер (+99966xxxxx),
чтобы лимиты на номер не мешали: принятый ключ даёт ответ по номеру
(PHONE_NUMBER_INVALID), отклонённый — API_ID_PUBLISHED_FLOOD / API_ID_INVALID.

Использование:
  python3 tools/check_keys.py [pairs.json]
pairs.json — список [название, api_id, api_hash] (по умолчанию встроенный список).
"""
import asyncio
import json
import random
import sys


BUILTIN = [
    ['Telegram Android (публичный, ключ мода и MDGram)', 4, '014b35b6184100b085b0d0572f9b5103'],
    ['Telegram Desktop', 2040, 'b18441a1ff607e10a989891a5462e627'],
    ['TDLib / Nicegram', 94575, 'a3406de8d171bb422bb6ddf3bbd800e2'],
    ['Telegram Desktop (example)', 17349, '344583e45741c457fe1862106095a5eb'],
    ['TG for Android (Play)', 6, 'eb06d4abfb49dc3eeb1aeb98ae0f581e'],
    ['Public Static Final', 5, '1c5c96d5edd401b1ed40db3fb5633e2d'],
    ['Telegram X', 21724, '3e0cb5efcd52300aec5994fdfc5bdc16'],
    ['Telegram iOS beta', 8, '7245de8e747a0d6fbe11f7cc14fcc0bb'],
    ['Telegram Web K', 1025907, '452b0359b988148995f22ff0f4229750'],
    ['Telegram Web', 2496, '8da85b0d5bfe62527e5b244c209159c3'],
    ['Telegram Swift', 10840, '33c45224029d59cb3ad0c16134215aeb'],
    ['Plus Messenger', 16623, '8c9dbfe58437d1739540f5d53c72ae4b'],
    ['Telegram macOS beta', 2834, '68875f756c9b437a8b916ca3de215815'],
    ['Telegram public beta', 9, '3975f648bb682ee889f35483bc618d1c'],
    ['Telegram CLI', 2899, '36722c72256a24c1225de00eb6a1ca74'],
]


def fresh_number():
    return '+99966%05d' % random.randint(0, 99999)


async def check(name, api_id, api_hash):
    try:
        from telethon import TelegramClient
    except ImportError:
        return 'нет telethon'
    session = '/tmp/sess_%s_%s' % (api_id, random.randint(0, 999999))
    client = TelegramClient(session, api_id, api_hash,
                            timeout=25, connection_retries=1, request_retries=1)
    try:
        await client.connect()
        try:
            await client.send_code_request(fresh_number())
            return 'ПРИНЯТ (сервер обработал запрос)'
        except Exception as e:
            message = str(e)
            upper = message.upper()
            if 'API_ID' in upper or 'PUBLISHED' in upper:
                return 'ОТКЛОНЁН: ' + message[:110]
            if 'PHONE' in upper or 'NUMBER' in upper:
                return 'ПРИНЯТ (ответ по номеру: ' + message[:60] + ')'
            if 'FLOOD' in upper:
                return 'ПРИНЯТ (лимит: ' + message[:60] + ')'
            return type(e).__name__ + ': ' + message[:100]
    except Exception as e:
        return 'НЕТ СВЯЗИ: ' + type(e).__name__ + ': ' + str(e)[:90]
    finally:
        try:
            await client.disconnect()
        except Exception:
            pass


async def main():
    pairs = BUILTIN
    if len(sys.argv) > 1:
        try:
            data = json.load(open(sys.argv[1], encoding='utf-8'))
            if isinstance(data, dict):
                data = data.get('pairs') or data.get('keys') or []
            pairs = [[p[0], int(p[1]), p[2]] for p in data if p and p[1] and p[2]]
        except Exception as e:
            print('не смог прочитать %s: %s' % (sys.argv[1], e))
    print()
    print('== ПРОВЕРКА КЛЮЧЕЙ НА ЖИВОМ СЕРВЕРЕ TELEGRAM (у каждой пары свой недействительный номер) ==')
    for name, api_id, api_hash in pairs:
        verdict = await check(name, api_id, api_hash)
        print('%-9s %-46s %s' % (api_id, str(name)[:46], verdict))
        sys.stdout.flush()


if __name__ == '__main__':
    asyncio.run(main())
