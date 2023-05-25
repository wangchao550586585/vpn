package org.example;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hello world!
 */
public class App {
    private static Map<String, Resource> channelMap = new ConcurrentHashMap<String, Resource>();
    private static Map<String, ByteBuffer> byteBufferMap = new ConcurrentHashMap<String, ByteBuffer>();

    public static void main(String[] args) throws Exception {
        new App().vpnStart();
    }

    private void vpnStart() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(1080));
        serverSocketChannel.configureBlocking(false);
        Selector masterReactor = Selector.open();
        SelectorStrategy selectorStrategy = new SelectorStrategy(6);
        serverSocketChannel.register(masterReactor, SelectionKey.OP_ACCEPT);
        while (true) {
            int n = masterReactor.select();
            if (n == 0) {
                continue;
            }
            Set<SelectionKey> selectionKeys = masterReactor.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            try {
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        System.out.println("into getSlaveReactor");
                        selectorStrategy.getSlaveReactor().register(childChannel);
                    }
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
