package org.example.handler;

import org.example.Attr;
import org.example.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractHandler implements Runnable {
    protected static Map<String, ByteBuffer> byteBufferMap = new ConcurrentHashMap<String, ByteBuffer>();
    protected static Map<String, Resource> channelMap = new ConcurrentHashMap<String, Resource>();
    protected final Attr attr;
    protected final SelectionKey key;
    protected final SocketChannel childChannel;

    public AbstractHandler(Attr attr, SelectionKey key, SocketChannel childChannel) {
        this.attr = attr;
        this.key = key;
        this.childChannel = childChannel;
    }

    protected static void close(String uuid, SelectionKey key, SocketChannel childChannel) {
        byteBufferMap.remove(uuid);
        key.cancel();
        try {
            childChannel.close();
        } catch (IOException e) {
            System.out.println(uuid + " error close childChannel");
            throw new RuntimeException(e);
        }
    }

    protected void swapReadMode(ByteBuffer buffer) {
        buffer.flip();
        buffer.mark();

    }

    protected void swapWriteMode(ByteBuffer buffer) {
        buffer.reset();
        buffer.flip();
    }

    public Attr getAttr() {
        return attr;
    }
}
