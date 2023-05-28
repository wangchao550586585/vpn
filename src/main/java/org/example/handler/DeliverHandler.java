package org.example.handler;

import org.example.CompositeByteBuf;
import org.example.entity.Resource;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class DeliverHandler extends AbstractHandler {
    public DeliverHandler(SelectionKey key, SocketChannel childChannel, String uuid, CompositeByteBuf cumulation) {
        super(key, childChannel, uuid);
        super.cumulation = cumulation;
    }

    public void exec() throws IOException {
        if (Objects.isNull(cumulation)) {
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
        //获取服务端数据
        if (!childChannel.isOpen()) {
            LOGGER.warn("channel 已经关闭 {}", uuid);
            return;
        }
        cumulation.write(remoteClient);
        LOGGER.info("child -> remote  end {}", uuid);
    }

    @Override
    public void after() {
        Resource resource = channelMap.get(uuid);
        if (Objects.isNull(resource)) {
            // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
            LOGGER.warn("exception child  close {}", uuid);
            return;
        }
        try {
            resource.getRemoteClient().close();
            resource.getSelector().close(); //close调用会调用wakeup
            channelMap.remove(uuid);
        } catch (IOException e) {
            LOGGER.error("child  close " + uuid, e);
        }
    }
}
