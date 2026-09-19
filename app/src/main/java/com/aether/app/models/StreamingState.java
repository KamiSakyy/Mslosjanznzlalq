package com.aether.app.models;

import java.util.ArrayList;
import java.util.List;

public class StreamingState {
    public String providerName = "";
    public String modelId = "";
    public int attempt = 0;
    public String status = "Подбираем модель";
    public String content = "";
    public String reasoning = "";
    public List<FailoverHop> hops = new ArrayList<>();

    public StreamingState() {}
}
