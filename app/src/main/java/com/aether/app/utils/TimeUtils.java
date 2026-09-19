package com.aether.app.utils;

public class TimeUtils {
    public static String relativeTime(String value) {
        if (value == null) return "сейчас";
        try {
            long ts = Long.parseLong(value);
            long diff = System.currentTimeMillis() - ts;
            long minutes = Math.max(0, diff / 60000);
            if (minutes < 2) return "сейчас";
            if (minutes < 60) return minutes + " мин";
            if (minutes < 1440) return (minutes / 60) + " ч";
            return (minutes / 1440) + " д";
        } catch (NumberFormatException e) {
            try {
                // Try ISO8601 via SimpleDateFormat
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                long ts = sdf.parse(value.substring(0, Math.min(value.length(), 19))).getTime();
                long diff = System.currentTimeMillis() - ts;
                long minutes = Math.max(0, diff / 60000);
                if (minutes < 2) return "сейчас";
                if (minutes < 60) return minutes + " мин";
                if (minutes < 1440) return (minutes / 60) + " ч";
                return (minutes / 1440) + " д";
            } catch (Exception ex) {
                return "сейчас";
            }
        }
    }

    public static String formatLatency(Long ms) {
        if (ms == null) return "";
        if (ms < 1000) return ms + " мс";
        return String.format("%.1f с", ms / 1000f);
    }
}
