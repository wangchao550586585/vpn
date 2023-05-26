package org.example.handler;

import org.example.entity.Attr;
import org.example.entity.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class DeliverHandler extends AbstractHandler {
    public DeliverHandler(Attr attr, SelectionKey key, SocketChannel childChannel) {
        super(attr, key, childChannel);
    }
    public void run() {
        try {
            deliver();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void deliver() throws IOException {
        String uuid = attr.getUuid();
        ByteBuffer buffer = byteBufferMap.get(uuid);
        if (Objects.isNull(buffer)) {
            LOGGER.warn("exception deliver get byte {}", uuid);
            return;
        }
        Resource resource = channelMap.get(uuid);
        if (Objects.isNull(resource)) {
            // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
            LOGGER.warn("exception child  close {}", uuid);
            return;
        }
        SocketChannel remoteClient = resource.getRemoteClient();
        try {
            //获取服务端数据
            if (!childChannel.isOpen()) {
                LOGGER.warn("channel 已经关闭 {}", uuid);
                return;
            }
            int read = childChannel.read(buffer);
            if (read < 0) {
                LOGGER.info("child read end {}", uuid);
                remoteClient.close();
                resource.getSelector().close(); //close调用会调用wakeup
                close(uuid, key, childChannel);
            } else {
                do {
                    buffer.flip();
                    //nPrint(buffer, uuid + ": 写入远程服务器数据为：");
                    //nPrintByte(buffer, uuid + " child -> remote ：");
                    remoteClient.write(buffer);
                    buffer.clear();
                } while (childChannel.read(buffer) > 0);
                LOGGER.info("child -> remote  end {}", uuid);
            }
        } catch (Exception exception) {
            LOGGER.error("child  close "+uuid, exception);
            remoteClient.close();
            resource.getSelector().close();
            close(uuid, key, childChannel);
            // TODO: 2023/5/26 这里不能往上抛异常
        }
    }

}
