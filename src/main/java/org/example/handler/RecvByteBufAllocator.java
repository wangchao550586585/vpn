package org.example.handler;

import java.nio.ByteBuffer;

public class RecvByteBufAllocator {
    private int lastBytesRead;
    private int attemptedBytesRead;

    public ByteBuffer allocate() {
        this.attemptedBytesRead = 4096 * 5;
        return ByteBuffer.allocate(attemptedBytesRead);
    }

    public void lastBytesRead(int lastBytesRead) {
        this.lastBytesRead = lastBytesRead;
    }

    //是否还有更多的数据可以读取
    public boolean maybeMoreDataSupplier() {
        return attemptedBytesRead == lastBytesRead;
    }

    public void release() {
    }
}
