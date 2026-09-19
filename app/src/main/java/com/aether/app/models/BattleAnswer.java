package com.aether.app.models;

public class BattleAnswer {
    public String speaker;
    public String providerSlug;
    public String providerName;
    public String modelId;
    public String content;
    public long latencyMs;

    public BattleAnswer() {}
    public BattleAnswer(String speaker, String slug, String name, String modelId, String content, long latency) {
        this.speaker = speaker;
        this.providerSlug = slug;
        this.providerName = name;
        this.modelId = modelId;
        this.content = content;
        this.latencyMs = latency;
    }
}
