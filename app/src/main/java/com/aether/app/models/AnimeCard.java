package com.aether.app.models;

public class AnimeCard {
    public long id;
    public String name;
    public String russian;
    public String image;
    public double score;
    public int episodes;
    public int episodesAired;
    public String status;
    public String kind;
    public String airedOn;
    public String releasedOn;
    public String url;
    public String description;
    public String[] genres;
    public String[] studios;
    public String rating;
    public int duration;
    public String nextEpisodeAt;

    public AnimeCard() {}

    public String getDisplayTitle() {
        if (russian != null && !russian.isEmpty()) return russian;
        return name != null ? name : "Без названия";
    }
}
