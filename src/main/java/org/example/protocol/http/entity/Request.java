package org.example.protocol.http.entity;

import java.util.List;
import java.util.Map;

public class Request {
    private StartLine startLine;
    private RequestHeaders requestHeaders;
    private String requestBody;
    private Map<String, String> params;
    private List<Multipart> multiparts;
    public Request(StartLine startLine, RequestHeaders requestHeaders) {
        this.startLine = startLine;
        this.requestHeaders = requestHeaders;
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

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    public List<Multipart> getMultiparts() {
        return multiparts;
    }

    public void setMultiparts(List<Multipart> multiparts) {
        this.multiparts = multiparts;
    }
}
