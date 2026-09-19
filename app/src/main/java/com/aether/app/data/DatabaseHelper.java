package com.aether.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.aether.app.models.AIProvider;
import com.aether.app.models.ChatMessage;
import com.aether.app.models.ChatSession;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "aether.db";
    private static final int DB_VERSION = 5;
    private static DatabaseHelper instance;
    private final Gson gson = new Gson();

    public static synchronized DatabaseHelper getInstance(Context ctx) {
        if (instance == null) instance = new DatabaseHelper(ctx.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, routingMode TEXT, systemPersona TEXT, chatMode TEXT, repoFullName TEXT, repoBranch TEXT, messageCount INTEGER DEFAULT 0, totalTokens INTEGER DEFAULT 0, isPinned INTEGER DEFAULT 0, updatedAt TEXT)");
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sessionId INTEGER, role TEXT, kind TEXT, content TEXT, imageUrl TEXT, attachmentDataUrl TEXT, groupKey TEXT, reasoningTrace TEXT, providerSlug TEXT, providerName TEXT, modelId TEXT, latencyMs INTEGER, tokensPerSec REAL, tokenCount INTEGER, routingReason TEXT, failoverLog TEXT, animeData TEXT, imagesData TEXT, createdAt TEXT)");
        db.execSQL("CREATE TABLE providers (id INTEGER PRIMARY KEY AUTOINCREMENT, slug TEXT UNIQUE, name TEXT, vendor TEXT, gateway TEXT, modelId TEXT, endpoint TEXT, publicKeyLabel TEXT, category TEXT, description TEXT, badgeText TEXT, supportsVision INTEGER, supportsStream INTEGER, contextWindow INTEGER, isEnabled INTEGER, priority INTEGER, avgLatencyMs INTEGER, successRate REAL, totalRequests INTEGER, failedRequests INTEGER, lastStatus TEXT)");
        db.execSQL("CREATE TABLE lumi_subs (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, shikimoriId INTEGER, anilibriaId INTEGER, poster TEXT, totalEpisodes INTEGER, watchedEpisodes INTEGER, lastChecked TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 5) {
            try { db.execSQL("DROP TABLE IF EXISTS sessions"); } catch (Exception ignored) {}
            try { db.execSQL("DROP TABLE IF EXISTS messages"); } catch (Exception ignored) {}
            try { db.execSQL("DROP TABLE IF EXISTS providers"); } catch (Exception ignored) {}
            try { db.execSQL("DROP TABLE IF EXISTS lumi_subs"); } catch (Exception ignored) {}
            onCreate(db);
        }
    }

    // Sessions
    public long createSession(ChatSession s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", s.title);
        cv.put("routingMode", s.routingMode);
        cv.put("systemPersona", s.systemPersona);
        cv.put("chatMode", s.chatMode);
        cv.put("repoFullName", s.repoFullName);
        cv.put("repoBranch", s.repoBranch);
        cv.put("messageCount", s.messageCount);
        cv.put("totalTokens", s.totalTokens);
        cv.put("isPinned", s.isPinned ? 1 : 0);
        cv.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        return db.insert("sessions", null, cv);
    }

    public List<ChatSession> getAllSessions() {
        List<ChatSession> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAt DESC", null);
        while (c.moveToNext()) {
            ChatSession s = new ChatSession();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.title = c.getString(c.getColumnIndexOrThrow("title"));
            s.routingMode = c.getString(c.getColumnIndexOrThrow("routingMode"));
            s.systemPersona = c.getString(c.getColumnIndexOrThrow("systemPersona"));
            s.chatMode = c.getString(c.getColumnIndexOrThrow("chatMode"));
            s.repoFullName = c.getString(c.getColumnIndexOrThrow("repoFullName"));
            s.repoBranch = c.getString(c.getColumnIndexOrThrow("repoBranch"));
            s.messageCount = c.getInt(c.getColumnIndexOrThrow("messageCount"));
            s.totalTokens = c.getInt(c.getColumnIndexOrThrow("totalTokens"));
            s.isPinned = c.getInt(c.getColumnIndexOrThrow("isPinned")) == 1;
            s.updatedAt = c.getString(c.getColumnIndexOrThrow("updatedAt"));
            list.add(s);
        }
        c.close();
        return list;
    }

    public ChatSession getSession(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM sessions WHERE id=?", new String[]{String.valueOf(id)});
        ChatSession s = null;
        if (c.moveToFirst()) {
            s = new ChatSession();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.title = c.getString(c.getColumnIndexOrThrow("title"));
            s.routingMode = c.getString(c.getColumnIndexOrThrow("routingMode"));
            s.systemPersona = c.getString(c.getColumnIndexOrThrow("systemPersona"));
            s.chatMode = c.getString(c.getColumnIndexOrThrow("chatMode"));
            s.repoFullName = c.getString(c.getColumnIndexOrThrow("repoFullName"));
            s.repoBranch = c.getString(c.getColumnIndexOrThrow("repoBranch"));
            s.messageCount = c.getInt(c.getColumnIndexOrThrow("messageCount"));
            s.isPinned = c.getInt(c.getColumnIndexOrThrow("isPinned")) == 1;
            s.updatedAt = c.getString(c.getColumnIndexOrThrow("updatedAt"));
        }
        c.close();
        return s;
    }

    public void updateSession(ChatSession s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", s.title);
        cv.put("routingMode", s.routingMode);
        cv.put("systemPersona", s.systemPersona);
        cv.put("chatMode", s.chatMode);
        cv.put("isPinned", s.isPinned ? 1 : 0);
        cv.put("messageCount", s.messageCount);
        cv.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        if (s.repoFullName != null) cv.put("repoFullName", s.repoFullName);
        if (s.repoBranch != null) cv.put("repoBranch", s.repoBranch);
        db.update("sessions", cv, "id=?", new String[]{String.valueOf(s.id)});
    }

    public void deleteSession(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "sessionId=?", new String[]{String.valueOf(id)});
        db.delete("sessions", "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAllSessions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", null, null);
        db.delete("sessions", null, null);
    }

    public void clearSessionMessages(long sessionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("messages", "sessionId=?", new String[]{String.valueOf(sessionId)});
        ContentValues cv = new ContentValues();
        cv.put("messageCount", 0);
        cv.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        db.update("sessions", cv, "id=?", new String[]{String.valueOf(sessionId)});
    }

    // Messages
    public long insertMessage(ChatMessage m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("sessionId", m.sessionId);
        cv.put("role", m.role);
        cv.put("kind", m.kind);
        cv.put("content", m.content);
        cv.put("imageUrl", m.imageUrl);
        cv.put("attachmentDataUrl", m.attachmentDataUrl);
        cv.put("groupKey", m.groupKey);
        cv.put("reasoningTrace", m.reasoningTrace);
        cv.put("providerSlug", m.providerSlug);
        cv.put("providerName", m.providerName);
        cv.put("modelId", m.modelId);
        if (m.latencyMs != null) cv.put("latencyMs", m.latencyMs);
        if (m.tokensPerSec != null) cv.put("tokensPerSec", m.tokensPerSec);
        if (m.tokenCount != null) cv.put("tokenCount", m.tokenCount);
        cv.put("routingReason", m.routingReason);
        if (m.failoverLog != null) cv.put("failoverLog", gson.toJson(m.failoverLog));
        cv.put("createdAt", m.createdAt != null ? m.createdAt : String.valueOf(System.currentTimeMillis()));
        long id = db.insert("messages", null, cv);
        // update session count
        db.execSQL("UPDATE sessions SET messageCount = (SELECT COUNT(*) FROM messages WHERE sessionId=?) , updatedAt=? WHERE id=?",
                new Object[]{m.sessionId, String.valueOf(System.currentTimeMillis()), m.sessionId});
        return id;
    }

    public List<ChatMessage> getMessagesForSession(long sessionId) {
        List<ChatMessage> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM messages WHERE sessionId=? ORDER BY id ASC", new String[]{String.valueOf(sessionId)});
        while (c.moveToNext()) {
            ChatMessage m = new ChatMessage();
            m.id = c.getLong(c.getColumnIndexOrThrow("id"));
            m.sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId"));
            m.role = c.getString(c.getColumnIndexOrThrow("role"));
            m.kind = c.getString(c.getColumnIndexOrThrow("kind"));
            m.content = c.getString(c.getColumnIndexOrThrow("content"));
            m.imageUrl = c.getString(c.getColumnIndexOrThrow("imageUrl"));
            m.attachmentDataUrl = c.getString(c.getColumnIndexOrThrow("attachmentDataUrl"));
            m.groupKey = c.getString(c.getColumnIndexOrThrow("groupKey"));
            m.reasoningTrace = c.getString(c.getColumnIndexOrThrow("reasoningTrace"));
            m.providerSlug = c.getString(c.getColumnIndexOrThrow("providerSlug"));
            m.providerName = c.getString(c.getColumnIndexOrThrow("providerName"));
            m.modelId = c.getString(c.getColumnIndexOrThrow("modelId"));
            if (!c.isNull(c.getColumnIndexOrThrow("latencyMs"))) m.latencyMs = c.getLong(c.getColumnIndexOrThrow("latencyMs"));
            m.createdAt = c.getString(c.getColumnIndexOrThrow("createdAt"));
            String failoverJson = c.getString(c.getColumnIndexOrThrow("failoverLog"));
            if (failoverJson != null) {
                try {
                    m.failoverLog = gson.fromJson(failoverJson, new TypeToken<List<com.aether.app.models.FailoverHop>>(){}.getType());
                } catch (Exception ignored) {}
            }
            list.add(m);
        }
        c.close();
        return list;
    }

    // Providers
    public void upsertProviders(List<AIProvider> providers) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (AIProvider p : providers) {
                ContentValues cv = new ContentValues();
                cv.put("slug", p.slug);
                cv.put("name", p.name);
                cv.put("vendor", p.vendor);
                cv.put("gateway", p.gateway);
                cv.put("modelId", p.modelId);
                cv.put("endpoint", p.endpoint);
                cv.put("publicKeyLabel", p.publicKeyLabel);
                cv.put("category", p.category);
                cv.put("description", p.description);
                cv.put("badgeText", p.badgeText);
                cv.put("supportsVision", p.supportsVision ? 1 : 0);
                cv.put("supportsStream", p.supportsStream ? 1 : 0);
                cv.put("contextWindow", p.contextWindow);
                cv.put("isEnabled", p.isEnabled ? 1 : 0);
                cv.put("priority", p.priority);
                cv.put("avgLatencyMs", p.avgLatencyMs);
                cv.put("successRate", p.successRate);
                cv.put("totalRequests", p.totalRequests);
                cv.put("failedRequests", p.failedRequests);
                cv.put("lastStatus", p.lastStatus);
                int updated = db.update("providers", cv, "slug=?", new String[]{p.slug});
                if (updated == 0) db.insert("providers", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<AIProvider> getAllProviders() {
        List<AIProvider> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM providers ORDER BY priority ASC", null);
        while (c.moveToNext()) {
            AIProvider p = new AIProvider();
            p.id = c.getLong(c.getColumnIndexOrThrow("id"));
            p.slug = c.getString(c.getColumnIndexOrThrow("slug"));
            p.name = c.getString(c.getColumnIndexOrThrow("name"));
            p.vendor = c.getString(c.getColumnIndexOrThrow("vendor"));
            p.gateway = c.getString(c.getColumnIndexOrThrow("gateway"));
            p.modelId = c.getString(c.getColumnIndexOrThrow("modelId"));
            p.endpoint = c.getString(c.getColumnIndexOrThrow("endpoint"));
            p.publicKeyLabel = c.getString(c.getColumnIndexOrThrow("publicKeyLabel"));
            p.category = c.getString(c.getColumnIndexOrThrow("category"));
            p.description = c.getString(c.getColumnIndexOrThrow("description"));
            p.badgeText = c.getString(c.getColumnIndexOrThrow("badgeText"));
            p.supportsVision = c.getInt(c.getColumnIndexOrThrow("supportsVision")) == 1;
            p.supportsStream = c.getInt(c.getColumnIndexOrThrow("supportsStream")) == 1;
            p.contextWindow = c.getInt(c.getColumnIndexOrThrow("contextWindow"));
            p.isEnabled = c.getInt(c.getColumnIndexOrThrow("isEnabled")) == 1;
            p.priority = c.getInt(c.getColumnIndexOrThrow("priority"));
            p.avgLatencyMs = c.getLong(c.getColumnIndexOrThrow("avgLatencyMs"));
            p.successRate = c.getDouble(c.getColumnIndexOrThrow("successRate"));
            p.totalRequests = c.getInt(c.getColumnIndexOrThrow("totalRequests"));
            p.failedRequests = c.getInt(c.getColumnIndexOrThrow("failedRequests"));
            p.lastStatus = c.getString(c.getColumnIndexOrThrow("lastStatus"));
            list.add(p);
        }
        c.close();
        return list;
    }

    public void toggleProvider(String slug, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("isEnabled", enabled ? 1 : 0);
        db.update("providers", cv, "slug=?", new String[]{slug});
    }

    public void resetProviderStats() {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("totalRequests", 0);
        cv.put("failedRequests", 0);
        cv.put("successRate", 100.0);
        cv.put("avgLatencyMs", 1000);
        db.update("providers", cv, null, null);
    }
}
