package com.nike.wingtips.springboot.support;

public class Greeting {

    private final long id;
    private final String content;

    public Greeting() {
        this.id = 0;
        this.content = null;
    }

    public Greeting(long id, String content) {
        this.id = id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
}
