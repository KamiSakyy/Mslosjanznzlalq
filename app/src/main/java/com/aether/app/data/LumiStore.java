package com.aether.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.aether.app.models.AnimeCard;
import java.util.ArrayList;
import java.util.List;

public class LumiStore {
    private final DatabaseHelper dbHelper;

    public LumiStore(Context ctx) {
        dbHelper = DatabaseHelper.getInstance(ctx);
    }

    public static class Subscription {
        public long id;
        public String title;
        public long shikimoriId;
        public long anilibriaId;
        public String poster;
        public int totalEpisodes;
        public int watchedEpisodes;
        public String lastChecked;
    }

    public long addSubscription(AnimeCard card, int totalEpisodes) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", card.getDisplayTitle());
        cv.put("shikimoriId", card.id);
        cv.put("poster", card.image);
        cv.put("totalEpisodes", totalEpisodes);
        cv.put("watchedEpisodes", 0);
        cv.put("lastChecked", String.valueOf(System.currentTimeMillis()));
        return db.insert("lumi_subs", null, cv);
    }

    public List<Subscription> getAll() {
        List<Subscription> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM lumi_subs ORDER BY lastChecked DESC", null);
        while (c.moveToNext()) {
            Subscription s = new Subscription();
            s.id = c.getLong(c.getColumnIndexOrThrow("id"));
            s.title = c.getString(c.getColumnIndexOrThrow("title"));
            s.shikimoriId = c.getLong(c.getColumnIndexOrThrow("shikimoriId"));
            s.anilibriaId = c.getLong(c.getColumnIndexOrThrow("anilibriaId"));
            s.poster = c.getString(c.getColumnIndexOrThrow("poster"));
            s.totalEpisodes = c.getInt(c.getColumnIndexOrThrow("totalEpisodes"));
            s.watchedEpisodes = c.getInt(c.getColumnIndexOrThrow("watchedEpisodes"));
            s.lastChecked = c.getString(c.getColumnIndexOrThrow("lastChecked"));
            list.add(s);
        }
        c.close();
        return list;
    }

    public void remove(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("lumi_subs", "id=?", new String[]{String.valueOf(id)});
    }

    public void updateChecked(long id, int newTotal) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("totalEpisodes", newTotal);
        cv.put("lastChecked", String.valueOf(System.currentTimeMillis()));
        db.update("lumi_subs", cv, "id=?", new String[]{String.valueOf(id)});
    }
}
