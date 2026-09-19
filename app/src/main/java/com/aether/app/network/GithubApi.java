package com.aether.app.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GithubApi {
    private final OkHttpClient client;

    public GithubApi() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public static class Repo {
        public String fullName;
        public String name;
        public boolean isPrivate;
        public String defaultBranch;
        public String description;
        public String language;
    }

    public static class User {
        public String login;
        public String name;
        public String avatarUrl;
    }

    public static class AuthResult {
        public boolean connected;
        public User user;
        public List<Repo> repos;
        public String error;
    }

    public AuthResult authenticate(String token) {
        AuthResult result = new AuthResult();
        try {
            Request userReq = new Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .build();
            try (Response resp = client.newCall(userReq).execute()) {
                if (!resp.isSuccessful()) {
                    result.connected = false;
                    result.error = "HTTP " + resp.code() + " - проверьте токен";
                    return result;
                }
                String body = resp.body() != null ? resp.body().string() : "{}";
                JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                User user = new User();
                user.login = obj.has("login") ? obj.get("login").getAsString() : "";
                user.name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : null;
                user.avatarUrl = obj.has("avatar_url") ? obj.get("avatar_url").getAsString() : "";
                result.user = user;

                // fetch repos
                Request reposReq = new Request.Builder()
                        .url("https://api.github.com/user/repos?per_page=100&sort=updated")
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .build();
                try (Response reposResp = client.newCall(reposReq).execute()) {
                    List<Repo> repos = new ArrayList<>();
                    if (reposResp.isSuccessful() && reposResp.body() != null) {
                        String reposBody = reposResp.body().string();
                        JsonArray arr = JsonParser.parseString(reposBody).getAsJsonArray();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject r = arr.get(i).getAsJsonObject();
                            Repo repo = new Repo();
                            repo.fullName = r.has("full_name") ? r.get("full_name").getAsString() : "";
                            repo.name = r.has("name") ? r.get("name").getAsString() : "";
                            repo.isPrivate = r.has("private") && r.get("private").getAsBoolean();
                            repo.defaultBranch = r.has("default_branch") ? r.get("default_branch").getAsString() : "main";
                            repo.description = r.has("description") && !r.get("description").isJsonNull() ? r.get("description").getAsString() : null;
                            repo.language = r.has("language") && !r.get("language").isJsonNull() ? r.get("language").getAsString() : null;
                            repos.add(repo);
                        }
                    }
                    result.repos = repos;
                }
                result.connected = true;
                return result;
            }
        } catch (Exception e) {
            result.connected = false;
            result.error = e.getMessage();
            return result;
        }
    }
}
