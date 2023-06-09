package org.example;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.reactor.SelectorStrategy;

import java.net.*;
import java.nio.channels.*;
import java.util.*;

/**
 * Hello world!
 */
public class App {
    protected final static Logger LOGGER = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        new App().vpnStart();
    }

    private void vpnStart() {
        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(8090));
            LOGGER.info("MasterReactor bind success");
            serverSocketChannel.configureBlocking(false);
            Selector masterReactor = Selector.open();
            LOGGER.info("MasterReactor selector open success");
            SelectorStrategy selectorStrategy = new SelectorStrategy();
            LOGGER.info("slaveReactor open Selector all success");
            serverSocketChannel.register(masterReactor, SelectionKey.OP_ACCEPT);
            LOGGER.info("MasterReactor channel register success");
            while (true) {
                int n = masterReactor.select();
                if (n == 0) {
                    continue;
                }
                Set<SelectionKey> selectionKeys = masterReactor.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        selectorStrategy.getSlaveReactor().register(childChannel);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("MasterReactor exec fail", e);
            throw new RuntimeException(e);
        }
    }

}
