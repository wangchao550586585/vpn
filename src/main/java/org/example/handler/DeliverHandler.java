package org.example.handler;

import org.example.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class DeliverHandler extends AbstractHandler {
    public void deliver(SelectionKey key, SocketChannel childChannel, String uuid) throws IOException {
        ByteBuffer buffer = byteBufferMap.get(uuid);
        if (Objects.isNull(buffer)){
            System.out.println(uuid + " exception deliver get byte  ");
            return;
        }
        Resource resource = channelMap.get(uuid);
        if (Objects.isNull(resource)) {
            // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
            System.out.println(uuid + " exception child  close");
            return;
        }
        SocketChannel remoteClient = resource.getRemoteClient();
        try {
            //获取服务端数据
            if (!childChannel.isOpen()) {
                System.out.println(uuid + " channel 已经关闭");
                return;
            }
            int read = childChannel.read(buffer);
            if (read < 0) {
                System.out.println(uuid + " child  close");
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
                System.out.println(uuid + " child -> remote  end");
            }
        } catch (Exception exception) {
            System.out.println(uuid + " exception child  close" + exception.getMessage());
            remoteClient.close();
            resource.getSelector().close();
            close(uuid, key, childChannel);
            //exception.printStackTrace();
        }
    }

}
