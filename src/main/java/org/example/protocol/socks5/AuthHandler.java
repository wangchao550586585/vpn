package org.example.protocol.socks5;

import org.example.entity.CompositeByteBuf;
import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AuthHandler extends AbstractHandler {
    public AuthHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
    }

    public void exec() throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        String uuid = channelWrapped.uuid();
        byte VER;
        int len = cumulation.remaining();
        //auth协议最少有3位
        if (len < 3) {
            LOGGER.warn("数据包不完整 {}", uuid);
            return;
        }
        //切换读取模式
        cumulation.mark();
        VER = cumulation.get();
        if (0x05 > VER) {
            LOGGER.warn("版本号错误或版本过低，只能支持5 {}", uuid);
            closeChildChannel();
            return;
        }
        byte NMETHODS = cumulation.get();
        //读取数据不够，接着重读
        if (cumulation.remaining() < NMETHODS) {
            cumulation.reset();
            LOGGER.warn("数据包不完整 {}", uuid);
            return;
        }
        //说明读取正常,后面的method不校验了，直接clean。
        cumulation.clearAll();
        //2~255
        writeBuffer.put((byte) 5);
        writeBuffer.put((byte) 0);
        writeBuffer.flip();
        channelWrapped.channel().write(writeBuffer);
        writeBuffer.clear();
        //更换附件
        ConnectionHandler connectionHandler = new ConnectionHandler(channelWrapped);
        channelWrapped.key().attach(connectionHandler);
        LOGGER.info("鉴权成功 {}", uuid);
    }

}
