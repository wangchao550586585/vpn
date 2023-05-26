package org.example.entity;

public class Attr {
    private String uuid;
    private  Attr self() {
        return this;
    }

    public  Attr uuid(String uuid) {
        this.uuid = uuid;
        return self();
    }

    public String getUuid() {
        return uuid;
    }
}
