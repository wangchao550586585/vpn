package org.example.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.Attr;
import org.example.entity.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
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

    public void closeChildChannel() {
        String uuid = attr.getUuid();
        byteBufferMap.remove(uuid);
        try {
            childChannel.close();
        } catch (IOException e) {
            LOGGER.error("close childChannel "+uuid, e);
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
