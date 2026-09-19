package com.aether.app.models;

import java.util.List;

public class ChatMessage {
    public long id;
    public long sessionId;
    public String role; // user, assistant
    public String kind; // text, image, arena
    public String content;
    public String imageUrl;
    public String attachmentDataUrl;
    public String groupKey;
    public String reasoningTrace;
    public String providerSlug;
    public String providerName;
    public String modelId;
    public Long latencyMs;
    public Double tokensPerSec;
    public Integer tokenCount;
    public String routingReason;
    public List<FailoverHop> failoverLog;
    public AnimeData animeData;
    public List<ImageData> imagesData;
    public String createdAt;

    public ChatMessage() {}

    public ChatMessage(long id, long sessionId, String role, String content) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = String.valueOf(System.currentTimeMillis());
    }

    public boolean isUser() {
        return "user".equals(role);
    }

    public boolean isAssistant() {
        return "assistant".equals(role);
    }

    public static class AnimeData {
        public String type; // cards, calendar
        public List<?> items;
    }

    public static class ImageData {
        public String image;
        public String thumbnail;
        public String title;
        public String source;
    }
}
