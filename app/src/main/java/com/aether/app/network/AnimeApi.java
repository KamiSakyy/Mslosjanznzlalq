package com.aether.app.network;

import com.aether.app.models.AnimeCard;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AnimeApi {
    private static final String SHIKI = "https://shikimori.one/api";
    private static final String UA = "AetherApp/1.0";
    private final OkHttpClient client;

    public AnimeApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    private String shikiImage(JsonObject anime) {
        if (!anime.has("image") || anime.get("image").isJsonNull()) return "";
        JsonObject img = anime.getAsJsonObject("image");
        String path = "";
        if (img.has("original")) path = img.get("original").getAsString();
        else if (img.has("preview")) path = img.get("preview").getAsString();
        if (path.isEmpty()) return "";
        return path.startsWith("http") ? path : "https://shikimori.one" + path;
    }

    private AnimeCard map(JsonObject a) {
        AnimeCard card = new AnimeCard();
        card.id = a.has("id") ? a.get("id").getAsLong() : 0;
        card.name = a.has("name") ? a.get("name").getAsString() : "";
        card.russian = a.has("russian") ? a.get("russian").getAsString() : card.name;
        card.image = shikiImage(a);
        card.score = a.has("score") ? a.get("score").getAsDouble() : 0;
        card.episodes = a.has("episodes") ? a.get("episodes").getAsInt() : 0;
        card.episodesAired = a.has("episodes_aired") ? a.get("episodes_aired").getAsInt() : 0;
        card.status = a.has("status") ? a.get("status").getAsString() : "";
        card.kind = a.has("kind") ? a.get("kind").getAsString() : "";
        card.airedOn = a.has("aired_on") && !a.get("aired_on").isJsonNull() ? a.get("aired_on").getAsString() : null;
        card.releasedOn = a.has("released_on") && !a.get("released_on").isJsonNull() ? a.get("released_on").getAsString() : null;
        card.url = "https://shikimori.one" + (a.has("url") ? a.get("url").getAsString() : "/animes/" + card.id);
        return card;
    }

    public List<AnimeCard> search(String query, int limit) throws Exception {
        Request req = new Request.Builder()
                .url(SHIKI + "/animes?search=" + java.net.URLEncoder.encode(query, "UTF-8") + "&limit=" + limit + "&order=popularity")
                .header("User-Agent", UA)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return new ArrayList<>();
            String body = resp.body() != null ? resp.body().string() : "[]";
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            List<AnimeCard> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) list.add(map(arr.get(i).getAsJsonObject()));
            return list;
        }
    }

    public List<AnimeCard> browse(String order, String status, String kind, int page, int limit) throws Exception {
        String url = SHIKI + "/animes?limit=" + limit + "&page=" + page + "&order=" + (order != null ? order : "popularity");
        if (status != null) url += "&status=" + status;
        if (kind != null) url += "&kind=" + kind;
        Request req = new Request.Builder().url(url).header("User-Agent", UA).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return new ArrayList<>();
            String body = resp.body() != null ? resp.body().string() : "[]";
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            List<AnimeCard> list = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) list.add(map(arr.get(i).getAsJsonObject()));
            return list;
        }
    }

    public AnimeCard getDetails(long id) throws Exception {
        Request req = new Request.Builder().url(SHIKI + "/animes/" + id).header("User-Agent", UA).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return null;
            String body = resp.body() != null ? resp.body().string() : "{}";
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            AnimeCard card = map(obj);
            card.description = obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString().replaceAll("\\[[^\\]]*\\]", "").trim() : "";
            return card;
        }
    }

    public static class CalendarEntry {
        public long animeId;
        public String name;
        public String russian;
        public String image;
        public int nextEpisode;
        public String nextEpisodeAt;
    }

    public List<CalendarEntry> getCalendar(int limit) throws Exception {
        Request req = new Request.Builder().url(SHIKI + "/calendar").header("User-Agent", UA).build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful()) return new ArrayList<>();
            String body = resp.body() != null ? resp.body().string() : "[]";
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            List<CalendarEntry> list = new ArrayList<>();
            for (int i = 0; i < Math.min(arr.size(), limit); i++) {
                JsonObject c = arr.get(i).getAsJsonObject();
                JsonObject anime = c.has("anime") ? c.getAsJsonObject("anime") : new JsonObject();
                CalendarEntry e = new CalendarEntry();
                e.animeId = anime.has("id") ? anime.get("id").getAsLong() : 0;
                e.name = anime.has("name") ? anime.get("name").getAsString() : "";
                e.russian = anime.has("russian") ? anime.get("russian").getAsString() : e.name;
                e.image = "";
                if (anime.has("image")) {
                    JsonObject img = anime.getAsJsonObject("image");
                    String p = img.has("original") ? img.get("original").getAsString() : "";
                    e.image = p.startsWith("http") ? p : "https://shikimori.one" + p;
                }
                e.nextEpisode = c.has("next_episode") ? c.get("next_episode").getAsInt() : 0;
                e.nextEpisodeAt = c.has("next_episode_at") ? c.get("next_episode_at").getAsString() : "";
                list.add(e);
            }
            return list;
        }
    }
}
