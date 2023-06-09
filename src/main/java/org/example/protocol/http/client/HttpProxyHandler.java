package org.example.protocol.http.client;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.Request;

public class HttpProxyHandler extends AbstractHandler {
    final Request request;
    final HttpClient httpClient;

    public HttpProxyHandler(ChannelWrapped channelWrapped, Request request, HttpClient httpClient) {
        super(channelWrapped);
        this.request = request;
        this.httpClient = httpClient;
    }

    @Override
    protected void exec() throws Exception {
        //将数据封装输入websocket发送。
        //序列化值
        byte[] bytes = channelWrapped.cumulation().readAllByte();
        if (bytes.length == 0) {
            LOGGER.info("info is 0");
        }
    }

    @Override
    public void after() {
        httpClient.closeSelector();
    }
}