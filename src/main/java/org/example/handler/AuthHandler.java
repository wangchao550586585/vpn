package org.example.handler;

import org.example.Attr;
import org.example.Status;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class AuthHandler extends AbstractHandler {


    public AuthHandler(Attr attr, SelectionKey key, SocketChannel childChannel) {
        super(attr, key, childChannel);
    }

    @Override
    public void run() {
        try {
            auth();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void auth() throws IOException {
        String uuid = attr.getUuid();
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        ByteBuffer buffer = byteBufferMap.get(uuid);
        if (buffer == null) {
            buffer = ByteBuffer.allocate(4096 * 5);
            byteBufferMap.put(uuid, buffer);
        }
        byte VER;
        int len;
        len = childChannel.read(buffer);
        if (len == -1) {
            close(uuid, key, childChannel);
            return;
        }
        //auth协议最少有3位
        if (len < 3) {
            System.out.println(uuid + " 数据包不完整");
            return;
        }
        swapReadMode(buffer);
        VER = buffer.get();
        if (0x05 > VER) {
            System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
            close(uuid, key, childChannel);
            return;
        }
        byte NMETHODS = buffer.get();
        //读取数据不够，接着重读
        if (buffer.remaining() < NMETHODS) {
            swapWriteMode(buffer);
            System.out.println(uuid + " 数据包不完整");
            return;
        }
        for (int i = 0; i < NMETHODS; i++) {
            buffer.get();
        }
        //说明读取正常,后面的method不校验了，直接clean。
        buffer.clear();
        //2~255
        key.attach(attr.status(Status.CONNECTION));
        writeBuffer.put((byte) 5);
        writeBuffer.put((byte) 0);
        writeBuffer.flip();
        childChannel.write(writeBuffer);
        writeBuffer.clear();
        System.out.println(uuid + " 鉴权成功");

        //更换附件
        ConnectionHandler connectionHandler = new ConnectionHandler(attr, key, childChannel);
        key.attach(connectionHandler);
    }

}
