package org.example.protocol.http.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;


public class HttpClient implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    //远端的host
    final String host;
    //远端的port
    final Integer port;
    //最终访问的host
    final String targetHost;
    //最终访问的port
    final Integer targetPort;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    SelectionKey selectionKey;
    volatile boolean flag;

    public HttpClient(String host, Integer port, String targetHost, Integer targetPort) {
        this.host = host;
        this.port = port;
        this.flag = true;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public HttpClient connect() {
        try {
            this.remoteChannel = SocketChannel.open();
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.debug("remote connect success remoteAddress {} ", remoteChannel.getRemoteAddress());
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            this.remoteSelector = Selector.open();
            selectionKey = remoteChannel.register(remoteSelector, 0);
            ChannelWrapped channelWrapped = ChannelWrapped.builder().key(selectionKey).channel(remoteChannel);
            Runnable handler = new HttpClientHandler(channelWrapped, targetHost, targetPort, this);
            selectionKey.attach(handler);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            LOGGER.debug("remote register success");
        } catch (Exception exception) {
            //对方主动关闭，自己主动超时关闭
            if (exception instanceof AsynchronousCloseException || exception instanceof ClosedChannelException) {
                LOGGER.debug("remote connect fail");
            } else {
                LOGGER.error("remote connect fail ", exception);
            }
            LOGGER.info("remote close");
            //这里不能往上抛异常
            return null;
        }
        new Thread(this).start();
        return this;
    }

    @Override
    public void run() {
        try {
            while (flag) {
                int n = remoteSelector.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = remoteSelector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    Runnable runnable = (Runnable) selectionKey.attachment();
                    runnable.run();
                }
            }
        } catch (Exception exception) {
            LOGGER.error("remote select fail ", exception);
            throw new RuntimeException(exception);
        } finally {
            if (Objects.nonNull(remoteSelector)) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    public void write(ByteBuffer wrap) throws IOException {
        remoteChannel.write(wrap);
    }

    public void close() {
        try {
            //删除channel
            //这里可能在升级状态中客户端关闭
            Object attachment = selectionKey.attachment();
            if (attachment instanceof HttpClientHandler) {
                remoteChannel.close();
            } else {
                HttpProxyHandler handler = (HttpProxyHandler) attachment;
                ChannelWrapped channelWrapped = handler.getChannelWrapped();
                channelWrapped.channel().close();
                channelWrapped.cumulation().clearAll();
            }
            //最后删除selector
            closeSelector();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 是否主动删除
     */
    public void closeSelector() {
        //最后删除selector
        flag = false;
        remoteSelector.wakeup();
    }

}
