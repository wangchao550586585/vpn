package org.example.protocol.socks5;

import org.example.CompositeByteBuf;
import org.example.entity.ChannelWrapped;
import org.example.entity.Resource;
import org.example.protocol.AbstractHandler;

import java.io.IOException;
import java.util.Objects;

public class DeliverHandler extends AbstractHandler {
    Resource resource;

    public DeliverHandler(ChannelWrapped channelWrapped, Resource resource) {
        super(channelWrapped);
        this.resource = resource;
    }

    public void exec() throws IOException {
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        String uuid = channelWrapped.uuid();
        //判断当前channel是否已经关闭了
        if (!channelWrapped.channel().isOpen()) {
            LOGGER.warn("channel 已经关闭 {}", uuid);
            return;
        }
        //获取服务端数据
        cumulation.write(resource.remoteChannel());
        LOGGER.info("child -> remote  end {}", uuid);
    }

    public void after() {
        String uuid = channelWrapped.uuid();
        if (Objects.isNull(resource)) {
            // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
            LOGGER.warn("exception child  close {}", uuid);
            return;
        }
        try {
            resource.closeRemote();
        } catch (IOException e) {
            LOGGER.error("child  close " + uuid, e);
        }
    }

}
