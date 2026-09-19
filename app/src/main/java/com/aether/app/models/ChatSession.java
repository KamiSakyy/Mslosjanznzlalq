package com.aether.app.models;

public class ChatSession {
    public long id;
    public String title;
    public String routingMode;
    public String systemPersona;
    public String chatMode; // chat, battle, dialogue, github
    public String repoFullName;
    public String repoBranch;
    public int messageCount;
    public int totalTokens;
    public boolean isPinned;
    public String updatedAt;

    public ChatSession() {}

    public ChatSession(long id, String title, String chatMode) {
        this.id = id;
        this.title = title;
        this.chatMode = chatMode;
        this.routingMode = "auto";
        this.systemPersona = "universal";
        this.updatedAt = String.valueOf(System.currentTimeMillis());
    }
}
