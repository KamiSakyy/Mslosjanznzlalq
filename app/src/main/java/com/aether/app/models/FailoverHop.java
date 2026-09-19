package com.aether.app.models;

public class FailoverHop {
    public String providerSlug;
    public String providerName;
    public String modelId;
    public String status; // success, failed, skipped
    public int statusCode;
    public long latencyMs;
    public String error;

    public FailoverHop() {}

    public FailoverHop(String slug, String name, String modelId, String status, long latency) {
        this.providerSlug = slug;
        this.providerName = name;
        this.modelId = modelId;
        this.status = status;
        this.latencyMs = latency;
    }
}
