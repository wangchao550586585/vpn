package org.example;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Hello world!
 */
public class App {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());

    public static void main(String[] args) {
        new App().vpnStart();
    }

    private void vpnStart() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(1080));
            LOGGER.debug("MasterReactor bind success");
            serverSocketChannel.configureBlocking(false);
            Selector masterReactor = Selector.open();
            LOGGER.debug("MasterReactor selector open success");
            SelectorStrategy selectorStrategy = new SelectorStrategy();
            LOGGER.debug("slaveReactor open Selector all success");
            serverSocketChannel.register(masterReactor, SelectionKey.OP_ACCEPT);
            LOGGER.debug("MasterReactor channel register success");
            while (true) {
                int n = masterReactor.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = masterReactor.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        selectorStrategy.getSlaveReactor().register(childChannel);
                    }
                    iterator.remove();
                }
            }
        } catch (Exception e) {
            LOGGER.error("MasterReactor exec fail", e);
            throw new RuntimeException(e);
        }
    }

}
