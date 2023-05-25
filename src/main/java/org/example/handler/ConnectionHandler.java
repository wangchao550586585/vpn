package org.example.handler;

import org.example.Attr;
import org.example.Resource;
import org.example.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ConnectionHandler extends AbstractHandler {
    public ConnectionHandler(Attr attr, SelectionKey key, SocketChannel childChannel) {
        super(attr, key, childChannel);
    }

    public void run() {
        try {
            connection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void connection() throws IOException {
        String uuid = attr.getUuid();
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        ByteBuffer buffer = byteBufferMap.get(uuid);
        if (Objects.isNull(buffer)) {
            LOGGER.warn("buffer为空 {}", uuid);
            return;
        }
        int len = childChannel.read(buffer);
        if (len == -1) {
            LOGGER.warn("读取结束退出 {}", uuid);
            close(uuid, key, childChannel);
            return;
        }
        //协议最少5位
        if (len < 5) {
            LOGGER.warn("数据包不完整 {}", uuid);
            return;
        }
        swapReadMode(buffer);
        byte VER = buffer.get();
        if (0x05 > VER) {
            close(uuid, key, childChannel);
            LOGGER.warn("版本号错误或版本过低，只能支持5 {}", uuid);
            return;
        }
        byte CMD = buffer.get();
        if (0x01 != CMD) {
            close(uuid, key, childChannel);
            LOGGER.warn("协议格式不对 {}", uuid);
            return;
        }
        byte RSV = buffer.get();
        byte ATYP = buffer.get();
        String host = null;
        Integer port = 0;
        if (0x01 == ATYP) {//IPV4
            if (buffer.remaining() + 1 < 6) {
                swapWriteMode(buffer);
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            host = Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get());
            port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());

            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, childChannel.getRemoteAddress(), uuid);
        } else if (0x03 == ATYP) {//域名
            byte hostnameSize = buffer.get();
            if (buffer.remaining() < hostnameSize) {
                swapWriteMode(buffer);
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            byte[] b = new byte[hostnameSize];
            for (int i = 0; i < hostnameSize; i++) {
                b[i] = buffer.get();
            }
            host = Utils.byteToAscii(b);
            //按照大端
            port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());
            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, childChannel.getRemoteAddress(), uuid);
        } else if (0x04 == ATYP) {//IPV6
            LOGGER.warn("不支持IPV6访问 {}", uuid);
            close(uuid, key, childChannel);
            return;
        } else {
            LOGGER.warn("不知道的访问方式 {}", uuid);
            close(uuid, key, childChannel);
            return;
        }
        //说明正常读取结束，切换为写模式。
        buffer.clear();

        writeBuffer.put((byte) 5);
        writeBuffer.put((byte) 0);
        writeBuffer.put((byte) 0);
        writeBuffer.put(ATYP);
        //put host
        writeBuffer.put(new byte[]{0, 0, 0, 0});
        //put port
        writeBuffer.put(new byte[]{0, 0});
        writeBuffer.flip();
        childChannel.write(writeBuffer);
        writeBuffer.clear();
        //建立异步连接
        connect(host, port, attr.getUuid(), childChannel, key);

        //更换附件
        DeliverHandler deliverHandler = new DeliverHandler(attr, key, childChannel);
        key.attach(deliverHandler);
        LOGGER.info("连接成功 {}", uuid);
    }

    private void connect(final String host, final Integer port, final String uuid, SocketChannel childChannel, SelectionKey key) {
        Selector selector = null;
        try {
            SocketChannel socketChannel = SocketChannel.open();
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (socketChannel == null || !socketChannel.isConnected()) {
                        try {
                            socketChannel.close();
                            LOGGER.error("remote connect fail {}", uuid);
                            key.cancel();
                            childChannel.close();
                        } catch (Exception e) {
                            LOGGER.error("remote connect fail , so cancel fail " + uuid, e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, 300);
            socketChannel.connect(new InetSocketAddress(host, port));
            LOGGER.info("remote connect success {}", uuid);
            timer.cancel();
            socketChannel.configureBlocking(false);
            while (!socketChannel.finishConnect()) {
            }
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_READ);
            channelMap.put(uuid, new Resource().remoteClient(socketChannel).selector(selector).childChannel(childChannel).childSKey(key));
            LOGGER.info("连接远端成功 {}", uuid);
        } catch (Exception exception) {
            LOGGER.error("remote connect fail fail " + uuid, exception);
            throw new RuntimeException(exception);
        }
        Selector finalSelector = selector;
        Thread thread = new Thread(() -> {
            if (null == finalSelector) {
                return;
            }
            Resource resource = channelMap.get(uuid);
            SocketChannel childChannel1 = resource.getChildChannel();
            SelectionKey selectionKey1 = resource.childSKey();
            try {
                while (true) {
                    if (!finalSelector.isOpen()) {
                        LOGGER.warn("elector 正常退出 {}", uuid);
                        break;
                    }
                    int n = finalSelector.select();
                    if (n == 0) {
                        if (!finalSelector.isOpen()) {
                            LOGGER.warn("elector 正常退出 {}", uuid);
                            break;
                        }
                        continue;
                    }
                    if (n > 1) {
                        LOGGER.warn("监听过多 {}", uuid);
                    }
                    Set<SelectionKey> selectionKeys = finalSelector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        SocketChannel channel = (SocketChannel) selectionKey.channel();
                        if (selectionKey.isReadable()) {
                            try {
                                ByteBuffer allocate = ByteBuffer.allocate(4096 * 5);
                                int read = channel.read(allocate);
                                if (read < 0) {
                                    LOGGER.info("remote read end {}", uuid);
                                    channel.close();
                                    finalSelector.close();
                                    close(uuid, selectionKey1, childChannel1);
                                    break;
                                }
                                do {
                                    allocate.flip();
                                    childChannel1.write(allocate);
                                    allocate.clear();
                                } while (channel.read(allocate) > 0);
                                LOGGER.info("remote  -> child end {}", uuid);
                                //这里不能直接通知远端刷，因为异步通知远端后，读事件执行结束。后面select时，因为channel数据还没被读取，会导致再次select出来。
                            } catch (Exception exception) {
                                LOGGER.error("remote " + uuid, exception);
                                throw new RuntimeException(exception);
                            }
                        }
                        iterator.remove();
                    }
                }
            } catch (Exception exception) {
                LOGGER.error("remote select fail " + uuid, exception);
                throw new RuntimeException(exception);
            }
        });
        thread.start();
    }
}
