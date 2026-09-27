package com.github.sylvainlaurent.maven.yamljsonvalidator;

/** Maps a schema identifier, or a prefix ending in '*', to a retrieval location. */
public class SchemaMapping {
    private String from;
    private String to;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }
}
