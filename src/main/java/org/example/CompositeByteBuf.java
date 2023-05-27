package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * 只提供读和恢复
 */
public class CompositeByteBuf {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private final List<ByteBuffer> buffers = new LinkedList<>();
    private int readIndex;
    private int lastReadIndex;

    public CompositeByteBuf(ByteBuffer buffer) {
        buffers.add(buffer);
        lastReadIndex = readIndex = 0;
    }

    public void composite(ByteBuffer buffer) {
        buffers.add(buffer);
    }

    public int remaining() {
        return buffers.stream().mapToInt(ByteBuffer::remaining).sum();
    }


    public void mark() {
        buffers.forEach(it -> {
            it.mark();
            lastReadIndex = readIndex;
        });
    }

    public void reset() {
        buffers.forEach(it -> {
            it.reset();
            readIndex = lastReadIndex;
        });
    }

    public byte get() {
        ByteBuffer byteBuffer = buffers.get(readIndex);
        byte value = byteBuffer.get();
        //说明读到结尾处
        if (byteBuffer.remaining() <= 0) {
            if (++readIndex >= buffers.size()) {
                LOGGER.info("read end ");
            }
        }
        return value;
    }

    public void clear() {
        for (int i = 0; i < readIndex; i++) {
            buffers.remove(i);
        }
        lastReadIndex = readIndex = 0;
    }

    public void write(SocketChannel remoteClient) throws IOException {
        for (int i = readIndex; i < buffers.size(); i++) {
            ByteBuffer byteBuffer = buffers.get(i);
            //nPrint(buffer, uuid + ": 写入远程服务器数据为：");
            //nPrintByte(buffer, uuid + " child -> remote ：");
            remoteClient.write(byteBuffer);
        }
        clearAll();
    }

    public void clearAll() {
        lastReadIndex = readIndex = 0;
        buffers.clear();
    }
}
