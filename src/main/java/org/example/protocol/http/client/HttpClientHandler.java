package org.example.protocol.http.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.protocol.http.entity.Request;
import org.example.protocol.http.entity.RequestHeaders;
import org.example.protocol.http.entity.StartLine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Random;

public class HttpClientHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private static final Random r = new Random();
    ChannelWrapped channelWrapped;
    final String host;
    final Integer port;
    final HttpClient httpClient;
    public HttpClientHandler(ChannelWrapped channelWrapped, String host, Integer port, HttpClient httpClient) {
        this.channelWrapped = channelWrapped;
        this.host=host;
        this.port=port;
        this.httpClient=httpClient;
    }

    @Override
    public void run() {
        try {
            String uuid = channelWrapped.uuid();
            String hosts = host + ":" + port;
            Request request = Request.builder()//构建状态行
                    .startLine(StartLine.builder().method("CONNECT")
                            .requestTarget(hosts)
                            .httpVersion("HTTP/1.1"))
                    .requestHeaders(RequestHeaders.builder()
                            .host(hosts)
                            .proxyConnection("keep-alive")//值必须包含"websocket"。
                    );
            request.write(channelWrapped.channel(), uuid);
            HttpProxyHandler websocketClientUpgrade = new HttpProxyHandler(channelWrapped, request,httpClient);
            SelectionKey key = channelWrapped.key();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            channelWrapped.key().attach(websocketClientUpgrade);
            LOGGER.info("connect http proxy success {}", uuid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}