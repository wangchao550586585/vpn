package org.example.protocol.http;

public enum HttpStatus {
    OK(200, "OK"), NOT_FOUND(400, "NOT_FOUND"),UPGRADE(101, "UPGRADE");;
    private int statusCode;
    private String reasonPhrase;

    HttpStatus(int statusCode, String reasonPhrase) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public void setReasonPhrase(String reasonPhrase) {
        this.reasonPhrase = reasonPhrase;
    }
}
