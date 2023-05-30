package org.example.protocol.http;

import java.util.Map;

public class Request {
    private StartLine startLine;
    private RequestHeaders requestHeaders;
    private String requestBody;

    private Map<String, String> params;

    public Request(StartLine startLine, RequestHeaders requestHeaders, String requestBody, Map<String, String> params) {
        this.startLine = startLine;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.params = params;
    }

    public StartLine getStartLine() {
        return startLine;
    }

    public void setStartLine(StartLine startLine) {
        this.startLine = startLine;
    }

    public RequestHeaders getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(RequestHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }
}
