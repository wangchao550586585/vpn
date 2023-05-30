package org.example.protocol.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

public final class Response {
    private static final DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss 'GMT'", Locale.US);
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private String httpVersion;
    private HttpStatus httpStatus;
    private String date;
    private String connection;
    private String contentLanguage;
    private String contentType;
    private Integer contentLength;
    private String payload;
    static {
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public static Response builder() {
        return new Response();
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        //build startLine
        sb.append(httpVersion)
                .append(" ")
                .append(httpStatus.getStatusCode())
                .append(" ")
                .append(httpStatus.getReasonPhrase())
                .append("\r\n");
        //build HeaderFields
        sb.append("Date").append(": ").append(date).append("\r\n");
        Optional.ofNullable(connection).ifPresent(it -> {
            sb.append("Connection").append(": ").append(it).append("\r\n");
        });
        sb.append("Content-Language").append(": ").append(contentLanguage).append("\r\n");
        sb.append("Content-Type").append(": ").append(contentType).append("\r\n");
        sb.append("Content-Length").append(": ").append(contentLength).append("\r\n");

        //build requestBody
        sb.append("\r\n").append(payload);
        return sb.toString();
    }

    private Response self() {
        return this;
    }

    public Response httpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
        return self();
    }

    public Response httpStatus(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
        return self();
    }


    public Response date() {
        this.date = df.format(new Date());
        return self();
    }

    public Response connection(String connection) {
        this.connection = connection;
        return self();
    }

    public Response contentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
        return self();
    }

    public Response contentType(String contentType) {
        this.contentType = contentType;
        return self();
    }

    public Response contentLength(Integer contentLength) {
        this.contentLength = contentLength;
        return self();
    }

    public Response payload(String payload) {
        this.payload = payload;
        return self();
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        String response = build();
        ByteBuffer byteBuffer = putString(response);
        byteBuffer.flip();
        channel.write(byteBuffer);
        LOGGER.info("response {} {} ", response, uuid);
    }

    private ByteBuffer putString(String sb) {
        byte[] bytes = sb.getBytes();
        ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
        for (int i = 0; i < bytes.length; i++) {
            writeBuffer.put(bytes[i]);
        }
        return writeBuffer;
    }
}
