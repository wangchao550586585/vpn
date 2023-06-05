package org.example.protocol.websocket.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class WsClient implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    final String host;
    final Integer port;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    volatile boolean flag;

    public static void main(String[] args) {
        new WsClient("127.0.0.1", 8070).connect();
    }

    public WsClient(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.flag = true;
    }

    public void connect() {
        try {
            this.remoteChannel = SocketChannel.open();
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.debug("remote connect success remoteAddress {} ", remoteChannel.getRemoteAddress());
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            this.remoteSelector = Selector.open();
            SelectionKey selectionKey = remoteChannel.register(remoteSelector, 0);
            ChannelWrapped channelWrapped = ChannelWrapped.builder().key(selectionKey).channel(remoteChannel);
            Runnable handler = new WsClientHandler(channelWrapped);
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
        }
        run();
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
        }finally {
            if (Objects.nonNull(remoteChannel)){
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (Objects.nonNull(remoteSelector)){
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
