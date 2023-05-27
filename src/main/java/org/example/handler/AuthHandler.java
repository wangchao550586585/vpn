package org.example.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class AuthHandler extends AbstractHandler {
    public AuthHandler(SelectionKey key, SocketChannel childChannel, String uuid) {
        super(key, childChannel, uuid);
    }

    public void exec() throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
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
        childChannel.write(writeBuffer);
        writeBuffer.clear();
        //更换附件
        ConnectionHandler connectionHandler = new ConnectionHandler(key, childChannel, uuid, cumulation);
        key.attach(connectionHandler);
        LOGGER.info("鉴权成功 {}", uuid);
    }

}
