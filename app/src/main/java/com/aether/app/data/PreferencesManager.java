package com.aether.app.data;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {
    private static final String PREFS = "aether_prefs";
    private static PreferencesManager instance;
    private final SharedPreferences prefs;

    private PreferencesManager(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized PreferencesManager getInstance(Context ctx) {
        if (instance == null) instance = new PreferencesManager(ctx);
        return instance;
    }

    public void setGithubToken(String token) {
        prefs.edit().putString("github_token", token).apply();
    }

    public String getGithubToken() {
        return prefs.getString("github_token", null);
    }

    public void setGroqKey(String key) {
        prefs.edit().putString("groq_key", key).apply();
    }

    public String getGroqKey() {
        return prefs.getString("groq_key", null);
    }

    public void setOpenRouterKey(String key) {
        prefs.edit().putString("openrouter_key", key).apply();
    }

    public String getOpenRouterKey() {
        return prefs.getString("openrouter_key", null);
    }

    public void setActiveSessionId(long id) {
        prefs.edit().putLong("active_session", id).apply();
    }

    public long getActiveSessionId() {
        return prefs.getLong("active_session", -1);
    }

    public void setSimulateFailover(boolean v) {
        prefs.edit().putBoolean("simulate_failover", v).apply();
    }

    public boolean getSimulateFailover() {
        return prefs.getBoolean("simulate_failover", false);
    }
}
