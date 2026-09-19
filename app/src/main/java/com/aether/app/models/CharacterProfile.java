package com.aether.app.models;

import java.util.List;

public class CharacterProfile {
    public long malId;
    public String name;
    public String romName;
    public String nameKanji;
    public List<String> nicknames;
    public String about;
    public int favorites;
    public String url;
    public String image;
    public List<Role> roles;

    public static class Role {
        public String title;
        public String url;
        public String type; // anime, manga
        public double score;
    }
}
