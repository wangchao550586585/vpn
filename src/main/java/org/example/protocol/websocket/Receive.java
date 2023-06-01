package org.example.protocol.websocket;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.Request;

public class Receive extends AbstractHandler {
    private final Request request;

    public Receive(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
    }

    @Override
    protected void exec() throws Exception {
        //没有读到对应的终止符则返回接着继续读。


        //读取结束则清除本次读取数据
        channelWrapped.cumulation().clear();
    }

}
