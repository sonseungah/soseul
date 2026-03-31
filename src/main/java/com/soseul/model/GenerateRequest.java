package com.soseul.model;

public class GenerateRequest {
    private String genre;
    private String setting;
    private String characters;
    private String events;
    private String conditions;
    private int length = 1500;

    public String getGenre()       { return genre; }
    public String getSetting()     { return setting; }
    public String getCharacters()  { return characters; }
    public String getEvents()      { return events; }
    public String getConditions()  { return conditions; }
    public int    getLength()      { return length; }

    public void setGenre(String v)       { this.genre = v; }
    public void setSetting(String v)     { this.setting = v; }
    public void setCharacters(String v)  { this.characters = v; }
    public void setEvents(String v)      { this.events = v; }
    public void setConditions(String v)  { this.conditions = v; }
    public void setLength(int v)         { this.length = v; }
}
