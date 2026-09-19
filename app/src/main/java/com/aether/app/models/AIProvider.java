package com.aether.app.models;

public class AIProvider {
    public long id;
    public String slug;
    public String name;
    public String vendor;
    public String gateway;
    public String modelId;
    public String endpoint;
    public String publicKeyLabel;
    public String category;
    public String description;
    public String badgeText;
    public boolean supportsVision;
    public boolean supportsStream;
    public int contextWindow;
    public boolean isEnabled;
    public int priority;
    public long avgLatencyMs;
    public double successRate;
    public int totalRequests;
    public int failedRequests;
    public String lastStatus;

    public AIProvider() {}

    public AIProvider(String slug, String name, String vendor, String gateway, String modelId,
                      String endpoint, String category, String description, String badgeText,
                      boolean supportsVision, boolean supportsStream, int priority, long avgLatency) {
        this.slug = slug;
        this.name = name;
        this.vendor = vendor;
        this.gateway = gateway;
        this.modelId = modelId;
        this.endpoint = endpoint;
        this.category = category;
        this.description = description;
        this.badgeText = badgeText;
        this.supportsVision = supportsVision;
        this.supportsStream = supportsStream;
        this.contextWindow = 128000;
        this.isEnabled = true;
        this.priority = priority;
        this.avgLatencyMs = avgLatency;
        this.successRate = 100.0;
        this.totalRequests = 0;
        this.failedRequests = 0;
        this.lastStatus = "online";
        this.publicKeyLabel = gateway + ".free-tier";
    }
}
