#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KAMIGRAM_SEARCH_NO_LIMITS_R101 — глобальный поиск без ограничений.

Жалоба пользователя: «ПОИСК ГЛОБАЛЬНЫЙ НЕ РАБОТАЕТ, я ввожу, а оно ищет только
из существующих» + «глобальный поиск всегда приоритет, даже если 1 букву ввёл —
сразу результат» + «добавь фильтры: только фото и т.д., по #, бесконечная лента
фото, убери любые ограничения».

Что найдено при разборе 12.10.3:
  * глобальный поиск людей/каналов — это запрос TL_contacts_search, и его
    блокировал фильтр «часто используемые» (KamiGramNetFilter.TOP_PEERS);
  * вкладки «Каналы / Боты / Посты / Публичные посты» и медиа-фильтры
    (Фото+Видео, Ссылки, Файлы, Музыка, Голосовые) в нативном SearchViewPager
    прятались, когда диалогов <= 10 и нет историй (DialogsActivity
    .onlyDialogsAdapter()). История у Sakura по умолчанию выключена — поэтому
    вкладки поиска не появлялись вовсе и поиск выглядел «только по своим»;
  * лимиты запросов — 20 результатов, задержка перед поиском — 300 мс.

Что делает патч:
  1. DialogsActivity.onlyDialogsAdapter() → только onlySelect: вкладки и
     фильтры поиска доступны всегда.
  2. FiltersView.filters[] → добавлены отдельные фильтры «Фото», «Видео» и
     «GIF» (TL_inputMessagesFilterPhotos / Video / Gif) — они выдают результат
     красивой бесконечной сеткой фото/видео (нативный SharedPhotoVideoAdapter).
  3. SearchViewPager.updateItems() → новые фильтры добавлены вкладками.
  4. Лимиты: TL_messages_searchGlobal и TL_messages_search — 20 → 100,
     TL_contacts_search — 20 → 50 (максимум, который принимает сервер).
  5. Задержка поиска 300 мс → 0: результат приходит сразу после первого
     символа (глобальный поиск людей/каналов работает от 1 буквы).
  6. Новые строки ресурсов для названий фильтров (values + values-ru).
  7. СВОЯ ЛЕНТА: при фильтре Фото/Видео/GIF и введённом запросе выдача идёт
     бесконечной сеткой 3 в ряд из глобального поиска по всем каналам
     (нативный Telegram при запросе показывал обычный список сообщений).

Блокировку поиска снимает сам KamiGramNetFilter (search whitelist) — этот
патчер отвечает за нативный UI и лимиты.

Запуск: python3 apply_search_power.py <путь до TMessagesProj/src/main>
"""

import io
import os
import sys

MARKER = "KAMIGRAM_SEARCH_NO_LIMITS_R101"

STRINGS_EN = (
    ('SakuraPhotosFilter', 'Photos'),
    ('SakuraVideosFilter', 'Videos'),
    ('SakuraGifsFilter', 'GIF'),
)
STRINGS_RU = (
    ('SakuraPhotosFilter', 'Фото'),
    ('SakuraVideosFilter', 'Видео'),
    ('SakuraGifsFilter', 'GIF'),
)


def read(path):
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


def write(path, text):
    with io.open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def replace_once(path, old, new, what, failed, done):
    if not os.path.isfile(path):
        failed.append("нет файла для «%s»" % what)
        return
    source = read(path)
    if new in source:
        done.append("%s: уже применено" % what)
        return
    if old not in source:
        failed.append("%s: не найден якорь" % what)
        return
    write(path, source.replace(old, new, 1))
    done.append(what)


def patch_dialogs_activity(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "DialogsActivity.java")
    old = (
        "    public boolean onlyDialogsAdapter() {\n"
        "        int dialogsCount = getMessagesController().getTotalDialogsCount();\n"
        "        return onlySelect || /*searchViewPager != null && !searchViewPager.dialogsSearchAdapter.hasRecentSearch() ||*/ dialogsCount <= 10 && !hasStories;\n"
        "    }\n"
    )
    new = (
        "    public boolean onlyDialogsAdapter() {\n"
        "        /* %s: вкладки «Каналы / Боты / Посты / Публичные посты» и\n"
        "           медиа-фильтры поиска доступны ВСЕГДА. Раньше они прятались, если\n"
        "           диалогов <= 10 и нет историй, и глобальный поиск выглядел так,\n"
        "           будто ищет только по своим чатам. */\n"
        "        return onlySelect;\n"
        "    }\n" % MARKER
    )
    replace_once(path, old, new, "вкладки и фильтры поиска доступны всегда", failed, done)


def patch_filters_view(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "Adapters", "FiltersView.java")
    old = (
        "            new MediaFilterData(R.drawable.search_voice_filled, R.string.SharedVoiceTab2, "
        "new TLRPC.TL_inputMessagesFilterRoundVoice(), FILTER_TYPE_VOICE)\n"
        "    };\n"
    )
    new = (
        "            new MediaFilterData(R.drawable.search_voice_filled, R.string.SharedVoiceTab2, "
        "new TLRPC.TL_inputMessagesFilterRoundVoice(), FILTER_TYPE_VOICE),\n"
        "            /* %s: отдельные фильтры «Фото», «Видео» и «GIF» — выдача\n"
        "               бесконечной сеткой медиа (нативный SharedPhotoVideoAdapter). */\n"
        "            new MediaFilterData(R.drawable.search_media_filled, R.string.SakuraPhotosFilter, "
        "new TLRPC.TL_inputMessagesFilterPhotos(), FILTER_TYPE_MEDIA),\n"
        "            new MediaFilterData(R.drawable.search_media_filled, R.string.SakuraVideosFilter, "
        "new TLRPC.TL_inputMessagesFilterVideo(), FILTER_TYPE_MEDIA),\n"
        "            new MediaFilterData(R.drawable.search_media_filled, R.string.SakuraGifsFilter, "
        "new TLRPC.TL_inputMessagesFilterGif(), FILTER_TYPE_MEDIA)\n"
        "    };\n" % MARKER
    )
    replace_once(path, old, new, "фильтры «Фото», «Видео», «GIF»", failed, done)


def patch_search_view_pager(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "Components", "SearchViewPager.java")
    old = (
        "                Item item = new Item(FILTER_TYPE);\n"
        "                item.filterIndex = 0;\n"
        "                items.add(item);\n"
    )
    new = (
        "                Item item = new Item(FILTER_TYPE);\n"
        "                item.filterIndex = 0;\n"
        "                items.add(item);\n"
        "                /* %s: «Фото», «Видео» и «GIF» — отдельные вкладки поиска */\n"
        "                item = new Item(FILTER_TYPE);\n"
        "                item.filterIndex = 5;\n"
        "                items.add(item);\n"
        "                item = new Item(FILTER_TYPE);\n"
        "                item.filterIndex = 6;\n"
        "                items.add(item);\n"
        "                item = new Item(FILTER_TYPE);\n"
        "                item.filterIndex = 7;\n"
        "                items.add(item);\n" % MARKER
    )
    replace_once(path, old, new, "вкладки фильтров Фото/Видео/GIF", failed, done)


def patch_filtered_search_view(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "FilteredSearchView.java")
    replace_once(
        path,
        "                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();\n"
        "                req.q = finalQuery;\n"
        "                req.limit = 20;\n",
        "                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();\n"
        "                req.q = finalQuery;\n"
        "                req.limit = 100; /* %s: в 5 раз больше результатов */\n" % MARKER,
        "лимит поиска в чате 20 → 100", failed, done,
    )
    replace_once(
        path,
        "                final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();\n"
        "                req.limit = 20;\n",
        "                final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();\n"
        "                req.limit = 100; /* %s: глобальный поиск без ограничений */\n" % MARKER,
        "лимит глобального поиска медиа 20 → 100", failed, done,
    )


def patch_photo_feed(java_root, failed, done):
    """Своя лента Sakura: фото/видео/GIF из глобального поиска выдаются
    бесконечной сеткой 3 в ряд (как в Pinterest), даже когда введён запрос.
    Нативный Telegram при непустом запросе переключался на скучный список
    сообщений — именно поэтому «красивой ленты фото» там не было."""
    path = os.path.join(java_root, "org", "telegram", "ui", "FilteredSearchView.java")
    old = (
        "                            case FiltersView.FILTER_TYPE_MEDIA:\n"
        "                                if (TextUtils.isEmpty(currentDataQuery)) {\n"
        "                                    adapter = sharedPhotoVideoAdapter;\n"
        "                                } else {\n"
        "                                    adapter = dialogsAdapter;\n"
        "                                }\n"
        "                                break;\n"
    )
    new = (
        "                            case FiltersView.FILTER_TYPE_MEDIA:\n"
        "                                /* %s: своя фото-лента Sakura — бесконечная\n"
        "                                   сетка 3 в ряд из того, что нашёл глобальный поиск\n"
        "                                   по всем каналам, даже когда введён запрос. */\n"
        "                                adapter = sharedPhotoVideoAdapter;\n"
        "                                break;\n" % MARKER
    )
    replace_once(path, old, new, "бесконечная фото-лента при запросе", failed, done)


def patch_dialogs_search_adapter(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "Adapters", "DialogsSearchAdapter.java")
    replace_once(
        path,
        "        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();\n"
        "        req.limit = 20;\n",
        "        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();\n"
        "        req.limit = 100; /* %s */\n" % MARKER,
        "лимит поиска сообщений 20 → 100", failed, done,
    )
    replace_once(
        path,
        "        req.users_only = (currentMessagesFilter.flags & 8) != 0;\n"
        "        req.limit = 20;\n",
        "        req.users_only = (currentMessagesFilter.flags & 8) != 0;\n"
        "        req.limit = 100; /* %s: глобальный поиск по всем каналам */\n" % MARKER,
        "лимит searchGlobal 20 → 100", failed, done,
    )
    # Мгновенный поиск: 300 мс ожидания убираем и для обычного, и для #поиска.
    replace_once(
        path,
        "                        searchTopics(text);\n"
        "                        searchMessagesInternal(text, searchId);\n"
        "                        searchForumMessagesInternal(text, searchId);\n"
        "                    }\n"
        "                });\n"
        "            }, 300);\n",
        "                        searchTopics(text);\n"
        "                        searchMessagesInternal(text, searchId);\n"
        "                        searchForumMessagesInternal(text, searchId);\n"
        "                    }\n"
        "                });\n"
        "            }, 0); /* %s: поиск сразу после первого символа */\n" % MARKER,
        "поиск без задержки 300 мс", failed, done,
    )
    replace_once(
        path,
        "                            notifyDataSetChanged();\n"
        "                        }\n"
        "                    }));\n"
        "                }, 300);\n",
        "                            notifyDataSetChanged();\n"
        "                        }\n"
        "                    }));\n"
        "                }, 0); /* %s: #поиск и публичные посты — мгновенно */\n" % MARKER,
        "#поиск без задержки 300 мс", failed, done,
    )


def patch_search_adapter_helper(java_root, failed, done):
    path = os.path.join(java_root, "org", "telegram", "ui", "Adapters", "SearchAdapterHelper.java")
    replace_once(
        path,
        "                TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();\n"
        "                req.q = query;\n"
        "                req.limit = 20;\n",
        "                TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();\n"
        "                req.q = query;\n"
        "                req.limit = 50; /* %s: больше глобальных людей/каналов */\n" % MARKER,
        "лимит глобального поиска людей 20 → 50", failed, done,
    )


def patch_posts_container(java_root, failed, done):
    # Поиск по постам ВСЕХ публичных каналов (channels.searchPosts) без
    # премиум-заглушки, без «бесплатных запросов в день» и без оплаты звёздами:
    # поиск стартует сразу после ввода, клиентские лимиты игнорируются.
    path = os.path.join(java_root, "org", "telegram", "ui", "Components", "PostsSearchContainer.java")
    replace_once(
        path,
        "        if (!UserConfig.getInstance(currentAccount).isPremium()) {\n"
        "            emptyImageView.setVisibility(View.GONE);\n"
        "            emptyTitleView.setText(getString(R.string.SearchPostsTitle));\n"
        "            emptyTextView.setText(getString(R.string.SearchPostsText));\n"
        "\n"
        "            emptyButton.setVisibility(View.VISIBLE);\n"
        "            emptyButton.setText(getString(R.string.SearchPostsButtonPremium), true);\n"
        "            emptyButton.setSubText(null, true);\n"
        "            emptyButton.setOnClickListener(v -> {\n"
        "                fragment.presentFragment(\n"
        "                    new PremiumPreviewFragment(\"search\")\n"
        "                );\n"
        "            });\n"
        "            emptyUnderButtonTextView.setVisibility(View.VISIBLE);\n"
        "            emptyUnderButtonTextView.setText(getString(R.string.SearchPostsPremium));\n"
        "        } else if (!TextUtils.isEmpty(lastQuery) && messages.isEmpty() && endReached) {\n",
        "        /* %s: глобальный поиск постов доступен всем, витрина Premium\n"
        "           вместо поиска больше не показывается. */\n"
        "        if (!TextUtils.isEmpty(lastQuery) && messages.isEmpty() && endReached) {\n" % MARKER,
        "поиск постов без Premium-заглушки", failed, done,
    )
    replace_once(
        path,
        "                flood = (TLRPC.SearchPostsFlood) res;\n"
        "                if (flood.query_is_free) {\n"
        "                    load(false);\n"
        "                } else {\n"
        "                    updateEmptyView();\n"
        "                    listView.adapter.update(true);\n"
        "                }\n",
        "                flood = (TLRPC.SearchPostsFlood) res;\n"
        "                /* %s: «бесплатные запросы в день» не повод\n"
        "                   показывать заглушку — поиск едет всегда. */\n"
        "                load(false);\n" % MARKER,
        "лимит «бесплатных запросов» не блокирует поиск", failed, done,
    )
    replace_once(
        path,
        "        if (!news && flood == null) {\n"
        "            return;\n"
        "        }\n",
        "        /* %s: проверка лимитов не должна отменять поиск. */\n" % MARKER,
        "поиск не ждёт проверку лимитов", failed, done,
    )
    replace_once(
        path,
        "        } else if (!TextUtils.isEmpty(lastQuery) && flood != null && (flood.flags & 2) != 0 && now < flood.wait_till) {\n",
        "        } else if (false && !TextUtils.isEmpty(lastQuery) && flood != null && (flood.flags & 2) != 0 && now < flood.wait_till) {\n"
        "            /* %s: ветка «лимит достигнут, плати звёздами» отключена. */\n" % MARKER,
        "оплата звёздами за поиск отключена", failed, done,
    )


PUBLIC_FEED_METHOD = '''
    /* KAMIGRAM_PUBLIC_SEARCH_R102: глобальный поиск по постам ВСЕХ публичных
       каналов (channels.searchPosts), включая те, где пользователь не состоит.
       Результаты вплетаются в медиа-ленту фильтров Фото/Видео/GIF и т.д. */
    private int publicPostsQueryId;
    private int publicPostsRate;
    private long publicPostsLastId;
    private long publicPostsLastPeer;
    private boolean publicPostsEndReached;
    private final HashSet<Long> publicFeedKeys = new HashSet<>();

    private void searchPublicPosts(String query, boolean reset) {
        if (reset) {
            publicPostsQueryId++;
            publicPostsRate = 0;
            publicPostsLastId = 0;
            publicPostsLastPeer = 0;
            publicPostsEndReached = false;
            publicFeedKeys.clear();
        }
        if (publicPostsEndReached) {
            return;
        }
        final int queryId = publicPostsQueryId;
        final int account = UserConfig.selectedAccount;
        TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
        req.flags |= 2;
        req.query = query;
        req.limit = 50;
        if (publicPostsLastId != 0) {
            req.offset_rate = publicPostsRate;
            req.offset_id = (int) publicPostsLastId;
            req.offset_peer = MessagesController.getInstance(account).getInputPeer(publicPostsLastPeer);
        } else {
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (queryId != publicPostsQueryId || !(res instanceof TLRPC.messages_Messages)) {
                return;
            }
            TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
            MessagesController.getInstance(account).putUsers(r.users, false);
            MessagesController.getInstance(account).putChats(r.chats, false);
            boolean added = false;
            for (int i = 0; i < r.messages.size(); i++) {
                TLRPC.Message msg = r.messages.get(i);
                if (!isPublicFeedMedia(msg)) {
                    continue;
                }
                long peer = MessageObject.getPeerId(msg.peer_id);
                long key = peer * 4294967296L + (msg.id & 0xFFFFFFFFL);
                if (messagesById.indexOfKey(msg.id) >= 0 || !publicFeedKeys.add(key)) {
                    continue;
                }
                MessageObject messageObject = new MessageObject(account, msg, false, true);
                messages.add(messageObject);
                messagesById.put(msg.id, messageObject);
                added = true;
            }
            if (!r.messages.isEmpty()) {
                TLRPC.Message last = r.messages.get(r.messages.size() - 1);
                publicPostsRate = r.next_rate;
                publicPostsLastId = last.id & 0xFFFFFFFFL;
                publicPostsLastPeer = MessageObject.getPeerId(last.peer_id);
            }
            if (r.messages.isEmpty() || !(r instanceof TLRPC.TL_messages_messagesSlice)) {
                publicPostsEndReached = true;
            }
            if (added && adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }));
    }

    private boolean isPublicFeedMedia(TLRPC.Message msg) {
        if (msg == null || msg.media == null) {
            return false;
        }
        boolean photo = msg.media instanceof TLRPC.TL_messageMediaPhoto;
        boolean video = false;
        boolean gif = false;
        if (msg.media instanceof TLRPC.TL_messageMediaDocument && msg.media.document != null) {
            for (int i = 0; i < msg.media.document.attributes.size(); i++) {
                TLRPC.DocumentAttribute attribute = msg.media.document.attributes.get(i);
                if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    gif = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    video = true;
                }
            }
        }
        if (currentSearchFilter != null && currentSearchFilter.filter instanceof TLRPC.TL_inputMessagesFilterPhotos) {
            return photo;
        }
        if (currentSearchFilter != null && currentSearchFilter.filter instanceof TLRPC.TL_inputMessagesFilterVideo) {
            return video || gif;
        }
        if (currentSearchFilter != null && currentSearchFilter.filter instanceof TLRPC.TL_inputMessagesFilterGif) {
            return gif;
        }
        return photo || video || gif;
    }

'''


def patch_public_feed(java_root, failed, done):
    # Лента фильтров Фото/Видео/GIF получает результаты из ВСЕХ публичных
    # каналов (channels.searchPosts), а не только из своих чатов.
    path = os.path.join(java_root, "org", "telegram", "ui", "FilteredSearchView.java")
    replace_once(
        path,
        "import java.util.HashMap;\n",
        "import java.util.HashMap;\nimport java.util.HashSet;\n",
        "импорт HashSet для ленты", failed, done,
    )
    replace_once(
        path,
        "            lastMessagesSearchString = finalQuery;\n",
        "            /* KAMIGRAM_PUBLIC_SEARCH_R102: параллельно глобальному поиску\n"
        "               спрашиваем посты всех публичных каналов, даже без подписки. */\n"
        "            if (dialogId == 0 && communityId == 0 && currentSearchFilter != null\n"
        "                    && !TextUtils.isEmpty(finalQuery)) {\n"
        "                searchPublicPosts(finalQuery, clearOldResults);\n"
        "            }\n"
        "            lastMessagesSearchString = finalQuery;\n",
        "вызов поиска по публичным каналам из ленты", failed, done,
    )
    replace_once(
        path,
        "    boolean ignoreRequestLayout;\n",
        PUBLIC_FEED_METHOD + "    boolean ignoreRequestLayout;\n",
        "метод поиска по публичным каналам", failed, done,
    )
    replace_once(
        path,
        "                if (!isLoading && visibleItemCount > 0 && lastVisibleItem >= totalItemCount - 10 && !endReached) {\n",
        "                if (!isLoading && visibleItemCount > 0 && lastVisibleItem >= totalItemCount - 10\n"
        "                        && !(endReached && publicPostsEndReached)) { /* KAMIGRAM_PUBLIC_SEARCH_R102 */\n",
        "лента продолжает листаться за счёт публичных постов", failed, done,
    )


def patch_strings(res_root, failed, done):
    for folder, strings in (("values", STRINGS_EN), ("values-ru", STRINGS_RU)):
        path = os.path.join(res_root, folder, "strings.xml")
        if not os.path.isfile(path):
            continue
        source = read(path)
        if "SakuraPhotosFilter" in source:
            done.append("строки фильтров (%s): уже есть" % folder)
            continue
        block = ""
        for name, value in strings:
            block += '    <string name="%s">%s</string>\n' % (name, value)
        if "</resources>" not in source:
            failed.append("%s/strings.xml: нет </resources>" % folder)
            continue
        write(path, source.replace("</resources>", block + "</resources>", 1))
        done.append("строки фильтров (%s): Фото/Видео/GIF" % folder)


def verify(java_root, res_root):
    problems = []
    checks = (
        ("org/telegram/ui/DialogsActivity.java", "return onlySelect;"),
        ("org/telegram/ui/Adapters/FiltersView.java", "TL_inputMessagesFilterPhotos"),
        ("org/telegram/ui/Adapters/FiltersView.java", "TL_inputMessagesFilterGif"),
        ("org/telegram/ui/Components/SearchViewPager.java", "item.filterIndex = 7;"),
        ("org/telegram/ui/FilteredSearchView.java", "req.limit = 100;"),
        ("org/telegram/ui/FilteredSearchView.java", "своя фото-лента Sakura"),
        ("org/telegram/ui/Adapters/DialogsSearchAdapter.java", "req.limit = 100;"),
        ("org/telegram/ui/Adapters/SearchAdapterHelper.java", "req.limit = 50;"),
        ("org/telegram/ui/Components/PostsSearchContainer.java", "глобальный поиск постов доступен всем"),
        ("org/telegram/ui/FilteredSearchView.java", "searchPublicPosts"),
        ("org/telegram/messenger/kamigram/KamiGramNetFilter.java", "KAMIGRAM_SEARCH_NO_LIMITS_R101"),
    )
    for relative, needle in checks:
        path = os.path.join(java_root, *relative.split("/"))
        if not os.path.isfile(path):
            problems.append("нет файла %s" % relative)
            continue
        if needle not in read(path):
            problems.append("в %s нет «%s»" % (relative, needle))

    dialogs = os.path.join(java_root, "org", "telegram", "ui", "Adapters", "DialogsSearchAdapter.java")
    if os.path.isfile(dialogs) and "}, 300);" in read(dialogs):
        problems.append("в DialogsSearchAdapter осталась задержка 300 мс")

    net_filter = os.path.join(java_root, "org", "telegram", "messenger", "kamigram", "KamiGramNetFilter.java")
    if os.path.isfile(net_filter) and '"TL_contacts_search"' in read(net_filter):
        problems.append("TL_contacts_search всё ещё в блок-листе — глобальный поиск не работает")

    strings = os.path.join(res_root, "values", "strings.xml")
    if os.path.isfile(strings):
        source = read(strings)
        for name, _value in STRINGS_EN:
            if name not in source:
                problems.append("в values/strings.xml нет %s" % name)
    return problems


def main(argv):
    if len(argv) != 2:
        sys.stderr.write("usage: apply_search_power.py <TMessagesProj/src/main>\n")
        return 2
    main_root = argv[1]
    java_root = os.path.join(main_root, "java")
    res_root = os.path.join(main_root, "res")
    if not os.path.isdir(java_root):
        sys.stderr.write("SEARCH: ожидался путь .../TMessagesProj/src/main\n")
        return 2

    failed = []
    done = []
    patch_dialogs_activity(java_root, failed, done)
    patch_filters_view(java_root, failed, done)
    patch_search_view_pager(java_root, failed, done)
    patch_filtered_search_view(java_root, failed, done)
    patch_photo_feed(java_root, failed, done)
    patch_dialogs_search_adapter(java_root, failed, done)
    patch_search_adapter_helper(java_root, failed, done)
    patch_posts_container(java_root, failed, done)
    patch_public_feed(java_root, failed, done)
    patch_strings(res_root, failed, done)

    for item in done:
        print("SEARCH: ✓ %s" % item)
    for item in failed:
        sys.stderr.write("SEARCH: ✗ %s\n" % item)
    if failed:
        return 1

    problems = verify(java_root, res_root)
    for problem in problems:
        sys.stderr.write("SEARCH: %s\n" % problem)
    if problems:
        return 1
    print("SEARCH: глобальный поиск без ограничений, фильтры Фото/Видео/GIF, лимиты 100, отклик мгновенный")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
