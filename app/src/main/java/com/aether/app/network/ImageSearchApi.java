package com.aether.app.network;

import com.aether.app.models.ChatMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Порт image-search.ts: ищет арты через Danbooru / Gelbooru подобные API.
 * Для простоты используем Pollinations image search mock + fallback на safebooru.
 */
public class ImageSearchApi {
    private final OkHttpClient client;

    public ImageSearchApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public List<ChatMessage.ImageData> search(String query, int limit) {
        List<ChatMessage.ImageData> result = new ArrayList<>();
        try {
            // Try Danbooru
            String url = "https://danbooru.donmai.us/posts.json?tags=" + java.net.URLEncoder.encode(query, "UTF-8") + "+rating:safe&limit=" + limit;
            Request req = new Request.Builder().url(url).header("User-Agent", "AetherApp/1.0").build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String body = resp.body().string();
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String fileUrl = obj.has("file_url") ? obj.get("file_url").getAsString() : "";
                        String preview = obj.has("preview_file_url") ? obj.get("preview_file_url").getAsString() : fileUrl;
                        if (fileUrl.isEmpty()) continue;
                        ChatMessage.ImageData data = new ChatMessage.ImageData();
                        data.image = fileUrl;
                        data.thumbnail = preview;
                        data.title = query;
                        data.source = "Danbooru";
                        result.add(data);
                    }
                }
            }
        } catch (Exception e) {
            // fallback: generate placeholder via pollinations image
            for (int i = 0; i < Math.min(limit, 3); i++) {
                ChatMessage.ImageData data = new ChatMessage.ImageData();
                data.image = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(query + " anime art, high quality", java.nio.charset.StandardCharsets.UTF_8) + "?nologo=true&seed=" + (System.currentTimeMillis() + i);
                data.thumbnail = data.image;
                data.title = query;
                data.source = "Pollinations";
                result.add(data);
            }
        }
        if (result.isEmpty()) {
            for (int i = 0; i < Math.min(limit, 3); i++) {
                ChatMessage.ImageData data = new ChatMessage.ImageData();
                data.image = "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(query + " anime art", java.nio.charset.StandardCharsets.UTF_8) + "?nologo=true&seed=" + (System.currentTimeMillis() + i);
                data.thumbnail = data.image;
                data.title = query;
                data.source = "Pollinations";
                result.add(data);
            }
        }
        return result;
    }

    public String generateImageUrl(String prompt) {
        return "https://image.pollinations.ai/prompt/" + java.net.URLEncoder.encode(prompt, java.nio.charset.StandardCharsets.UTF_8) + "?width=1024&height=1024&nologo=true&seed=" + System.currentTimeMillis();
    }
}
