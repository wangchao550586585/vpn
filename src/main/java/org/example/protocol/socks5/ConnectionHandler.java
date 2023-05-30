package org.example.protocol.socks5;

import org.example.CompositeByteBuf;
import org.example.RemoteConnect;
import org.example.entity.ChannelWrapped;
import org.example.entity.Resource;
import org.example.protocol.AbstractHandler;
import org.example.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class ConnectionHandler extends AbstractHandler {
    public ConnectionHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
    }

    public void exec() throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        String uuid = channelWrapped.uuid();
        int len = cumulation.remaining();
        //协议最少5位
        if (len < 5) {
            LOGGER.warn("数据包不完整 {}", uuid);
            return;
        }
        cumulation.mark();
        byte VER = cumulation.get();
        if (0x05 > VER) {
            closeChildChannel();
            LOGGER.warn("版本号错误或版本过低，只能支持5 {}", uuid);
            return;
        }
        byte CMD = cumulation.get();
        if (0x01 != CMD) {
            closeChildChannel();
            LOGGER.warn("协议格式不对 {}", uuid);
            return;
        }
        byte RSV = cumulation.get();
        byte ATYP = cumulation.get();
        String host = null;
        Integer port = 0;
        SocketChannel channel = channelWrapped.channel();
        if (0x01 == ATYP) {//IPV4
            if (cumulation.remaining() + 1 < 6) {
                cumulation.reset();
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            host = Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get());
            port = Utils.byteToInt(cumulation.get()) * 256 + Utils.byteToInt(cumulation.get());

            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, channel.getRemoteAddress(), uuid);
        } else if (0x03 == ATYP) {//域名
            byte hostnameSize = cumulation.get();
            if (cumulation.remaining() < hostnameSize) {
                cumulation.reset();
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            byte[] b = new byte[hostnameSize];
            for (int i = 0; i < hostnameSize; i++) {
                b[i] = cumulation.get();
            }
            host = Utils.byteToAscii(b);
            //按照大端
            port = Utils.byteToInt(cumulation.get()) * 256 + Utils.byteToInt(cumulation.get());
            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, channel.getRemoteAddress(), uuid);
        } else if (0x04 == ATYP) {//IPV6
            LOGGER.warn("不支持IPV6访问 {}", uuid);
            closeChildChannel();
            return;
        } else {
            LOGGER.warn("不知道的访问方式 {}", uuid);
            closeChildChannel();
            return;
        }
        //说明正常读取结束，切换为写模式。
        cumulation.clear();
        writeBuffer.put((byte) 5);
        writeBuffer.put((byte) 0);
        writeBuffer.put((byte) 0);
        //这里写死，后面紧接着6位hose和port
        writeBuffer.put((byte) 1);
        //put host
        writeBuffer.put(new byte[]{0, 0, 0, 0});
        //put port
        writeBuffer.put(new byte[]{0, 0});
        writeBuffer.flip();
        channel.write(writeBuffer);
        writeBuffer.clear();
        //建立异步连接
        Resource resource = new RemoteConnect(host, port, uuid, channel,this).connect();
        if (Objects.nonNull(resource)) {
            //更换附件
            DeliverHandler deliverHandler = new DeliverHandler(channelWrapped, resource);
            channelWrapped.key().attach(deliverHandler);
        } else {
            closeChildChannel();
        }
    }
}
