package org.example.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.CompositeByteBuf;
import org.example.entity.ChannelWrapped;
import org.example.entity.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    protected static Map<String, Resource> channelMap = new ConcurrentHashMap<String, Resource>();
    protected ChannelWrapped channelWrapped;

    public AbstractHandler(ChannelWrapped channelWrapped) {
        this.channelWrapped = channelWrapped;
    }

    public void closeChildChannel() {
        try {
            channelWrapped.channel().close();
            CompositeByteBuf cumulation = channelWrapped.cumulation();
            if (null != cumulation) {
                cumulation.clear();
                cumulation = null;
            }
            after();
        } catch (IOException e) {
            LOGGER.error("close childChannel " + channelWrapped.uuid(), e);
            throw new RuntimeException(e);
        }
    }

    public void after() {
        String uuid = channelWrapped.uuid();
        Resource resource = channelMap.get(uuid);
        if (Objects.isNull(resource)) {
            // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
            LOGGER.warn("exception child  close {}", uuid);
            return;
        }
        try {
            resource.closeRemote();
            channelMap.remove(uuid);
        } catch (IOException e) {
            LOGGER.error("child  close " + uuid, e);
        }
    }

    public String uuid() {
        return channelWrapped.uuid();
    }

    public void run() {
        SocketChannel channel = channelWrapped.channel();
        String uuid = channelWrapped.uuid();
        try {
            //1.获取分配器
            RecvByteBufAllocator recvByteBufAllocator = channelWrapped.recvByteBufAllocator();
            do {
                //2.分配byteBuff,记录读取数据数量到分配器中
                ByteBuffer buffer = recvByteBufAllocator.allocate();
                //3.读取数据，
                int length = channel.read(buffer);
                recvByteBufAllocator.lastBytesRead(length);
                //4.说明读取结束
                if (length <= 0) {
                    //释放buffer
                    recvByteBufAllocator.release();
                    buffer = null;
                    //说明连接断开
                    if (length < 0) {
                        //说明读取结束
                        LOGGER.info(" child read end {}", uuid);
                        closeChildChannel();
                    }
                    break;
                }
                //5.执行业务方法
                process(buffer);
            } while (recvByteBufAllocator.maybeMoreDataSupplier());
            //如果buffer没装满则退出循环
            //4.通过本次读取数据数量，来判断下次读取数量

        } catch (IOException e) {
            closeChildChannel();
            LOGGER.error("childChannel read fail {} ", uuid);
        }
    }

    private void process(ByteBuffer buffer) throws IOException {
        //粘包处理
        //1.获取累加的bytebuffer
        buffer.flip();
        channelWrapped.cumulation().composite(buffer);
        //2.将buffer数据存储到累加buffer中
        //3.执行exec方法
        exec();
    }

    protected abstract void exec() throws IOException;
}
