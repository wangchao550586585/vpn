package org.example.protocol.http.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.CompositeByteBuf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class RequestHeaders {
    protected static final Logger LOGGER = LogManager.getLogger(RequestHeaders.class);
    private String accept;
    private String acceptEncoding;
    private String acceptLanguage;
    private String connection;
    private Integer contentLength;
    private String contentType;
    private String host;
    private String cacheControl;
    private String userAgent;

    public static RequestHeaders parse(CompositeByteBuf cumulation) {
        RequestHeaders requestHeaders = new RequestHeaders();
        while (cumulation.remaining() > 0) {
            String requestLine = cumulation.readLine();
            //说明读取结束，则关闭流，连续读取到2个\r\n则退出
            if (requestLine.length() == 0) {
                break;
            }
            String[] arr = requestLine.split(":");
            try {
                String key = arr[0];
                Object value = arr[1].trim();
                int pre = key.indexOf("-");
                if (pre > 0) {
                    String substring = key.substring(0, pre).toLowerCase();
                    String s = key.substring(pre, key.length()).replaceAll("-", "");
                    key = substring + s;
                } else {
                    key = key.toLowerCase();
                }
                Field field = requestHeaders.getClass().getDeclaredField(key);
                if (field.getType() != String.class) {
                    Class<?> type = field.getType();
                    value = type.getConstructor(String.class).newInstance(value);
                }
                field.setAccessible(true);
                field.set(requestHeaders, value);
            } catch (NoSuchFieldException e) {
                LOGGER.error("NoSuchFieldException {}: {}", arr[0], arr[1]);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("{}: {}", arr[0], arr[1]);
        }
        return requestHeaders;
    }

    public String getAccept() {
        return accept;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    public String getAcceptEncoding() {
        return acceptEncoding;
    }

    public void setAcceptEncoding(String acceptEncoding) {
        this.acceptEncoding = acceptEncoding;
    }

    public String getAcceptLanguage() {
        return acceptLanguage;
    }

    public void setAcceptLanguage(String acceptLanguage) {
        this.acceptLanguage = acceptLanguage;
    }

    public String getConnection() {
        return connection;
    }

    public void setConnection(String connection) {
        this.connection = connection;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getCacheControl() {
        return cacheControl;
    }

    public void setCacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getContentLength() {
        return contentLength;
    }

    public void setContentLength(Integer contentLength) {
        this.contentLength = contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public String toString() {
        return "RequestHeaders{" +
                "accept='" + accept + '\'' +
                ", acceptEncoding='" + acceptEncoding + '\'' +
                ", acceptLanguage='" + acceptLanguage + '\'' +
                ", connection='" + connection + '\'' +
                ", contentLength=" + contentLength +
                ", contentType='" + contentType + '\'' +
                ", host='" + host + '\'' +
                ", cacheControl='" + cacheControl + '\'' +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}