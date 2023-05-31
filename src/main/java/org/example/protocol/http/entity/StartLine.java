package org.example.protocol.http.entity;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class StartLine {
    private String method;
    private String requestTarget;
    private String httpVersion;

    public StartLine(String method, String requestTarget, String httpVersion) {
        this.method = method;
        this.requestTarget = requestTarget;
        this.httpVersion = httpVersion;
    }


    public static StartLine parse(String readLine) {
        String[] readLineArr = readLine.split(" ");
        String requestTarget;
        try {
            requestTarget = URLDecoder.decode(readLineArr[1], "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return new StartLine(readLineArr[0], requestTarget, readLineArr[2]);
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestTarget() {
        return requestTarget;
    }

    public void setRequestTarget(String requestTarget) {
        this.requestTarget = requestTarget;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    @Override
    public String toString() {
        return "StartLine{" +
                "method='" + method + '\'' +
                ", requestTarget='" + requestTarget + '\'' +
                ", httpVersion='" + httpVersion + '\'' +
                '}';
    }
}
