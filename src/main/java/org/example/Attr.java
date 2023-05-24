package org.example;

public class Attr {
    private static  Attr attr = new  Attr();
    private  Status status;
    private String uuid;
    private  Attr self() {
        return this;
    }

    public  Status getStatus() {
        return status;
    }

    public  Attr status( Status status) {
        this.status = status;
        return self();
    }


    public  Attr uuid(String uuid) {
        this.uuid = uuid;
        return self();
    }

    public String getUuid() {
        return uuid;
    }
}
