package com.aether.app.network;

import com.aether.app.models.CharacterProfile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CharacterApi {
    private final OkHttpClient client;
    private static final String UA = "AetherApp/1.0";

    public CharacterApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public CharacterProfile search(String query) {
        // Try Shikimori first
        try {
            String clean = query.replaceAll("(?i)\\s+(из|in|from)\\s+.+", "")
                    .replaceAll("(?i)(персонажа?|героя?|героин\\w*|аниме)\\s*", "")
                    .replaceAll("[«»\"']", "").trim();
            Request req = new Request.Builder()
                    .url("https://shikimori.one/api/characters/search?search=" + java.net.URLEncoder.encode(clean, "UTF-8"))
                    .header("User-Agent", UA)
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String body = resp.body().string();
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    if (arr.size() > 0) {
                        long id = arr.get(0).getAsJsonObject().get("id").getAsLong();
                        return fetchShikiProfile(id);
                    }
                }
            }
        } catch (Exception ignored) {}
        // Jikan fallback
        try {
            Request req = new Request.Builder()
                    .url("https://api.jikan.moe/v4/characters?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&limit=1")
                    .header("User-Agent", UA)
                    .build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String body = resp.body().string();
                    JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray data = obj.getAsJsonArray("data");
                    if (data != null && data.size() > 0) {
                        JsonObject c = data.get(0).getAsJsonObject();
                        CharacterProfile p = new CharacterProfile();
                        p.malId = c.get("mal_id").getAsLong();
                        p.name = c.get("name").getAsString();
                        p.romName = p.name;
                        p.url = c.has("url") ? c.get("url").getAsString() : "https://myanimelist.net/character/" + p.malId;
                        if (c.has("images")) {
                            JsonObject images = c.getAsJsonObject("images");
                            if (images.has("jpg")) p.image = images.getAsJsonObject("jpg").get("image_url").getAsString();
                        }
                        if (c.has("about") && !c.get("about").isJsonNull()) p.about = c.get("about").getAsString();
                        p.nicknames = new ArrayList<>();
                        if (c.has("nicknames")) {
                            JsonArray nicks = c.getAsJsonArray("nicknames");
                            for (int i = 0; i < nicks.size(); i++) p.nicknames.add(nicks.get(i).getAsString());
                        }
                        return p;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private CharacterProfile fetchShikiProfile(long id) throws Exception {
        Request req = new Request.Builder()
                .url("https://shikimori.one/api/characters/" + id)
                .header("User-Agent", UA)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String body = resp.body().string();
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            CharacterProfile p = new CharacterProfile();
            p.malId = id;
            p.name = obj.has("russian") && !obj.get("russian").isJsonNull() && !obj.get("russian").getAsString().isEmpty() ? obj.get("russian").getAsString() : obj.has("name") ? obj.get("name").getAsString() : "";
            p.romName = obj.has("name") ? obj.get("name").getAsString() : "";
            p.nameKanji = obj.has("japanese") && !obj.get("japanese").isJsonNull() ? obj.get("japanese").getAsString() : "";
            p.url = "https://shikimori.one" + (obj.has("url") ? obj.get("url").getAsString() : "/characters/" + id);
            if (obj.has("image")) {
                JsonObject img = obj.getAsJsonObject("image");
                String path = img.has("original") ? img.get("original").getAsString() : "";
                p.image = path.startsWith("http") ? path : "https://shikimori.one" + path;
            }
            p.nicknames = new ArrayList<>();
            if (obj.has("altname") && !obj.get("altname").isJsonNull()) {
                String alt = obj.get("altname").getAsString();
                if (!alt.isEmpty()) p.nicknames.add(alt);
            }
            if (obj.has("description") && !obj.get("description").isJsonNull()) p.about = obj.get("description").getAsString();
            return p;
        }
    }

    public String toMarkdown(CharacterProfile c) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(c.name);
        if (c.nameKanji != null && !c.nameKanji.isEmpty()) sb.append(" (").append(c.nameKanji).append(")");
        sb.append("\n");
        if (c.romName != null && !c.romName.equalsIgnoreCase(c.name)) sb.append("**Имя (лат.):** ").append(c.romName).append("\n");
        if (c.nicknames != null && !c.nicknames.isEmpty()) sb.append("**Прозвища:** ").append(String.join(", ", c.nicknames)).append("\n");
        if (c.about != null) {
            String about = c.about.split("\n")[0];
            if (about.length() > 900) about = about.substring(0, 900) + "…";
            sb.append("\n").append(about).append("\n");
        }
        sb.append("\n[Полная карточка →](").append(c.url).append(")");
        return sb.toString();
    }
}
