package org.example.protocol.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Base64;

public class StartLine {
    private String method;
    private String requestTarget;
    private String httpVersion;

    public StartLine(String method, String requestTarget, String httpVersion) {
        this.method = method;
        this.requestTarget = requestTarget;
        this.httpVersion = httpVersion;
    }

    /**
     * 请求行 request-line，开始于一个方法标识 method，
     * 紧接着一个空白 SP，
     * 然后是请求目标 request-target，
     * 另一个空白 SP，
     * 之后是协议版本 HTTP-version，
     * 最后是回车换行符 CRLF。
     * 例子如下：GET /index.jsp?name=%E5%BC%A0%E4%B8%89&value=10 HTTP/1.1
     *
     * @param readLine
     * @return
     */
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
