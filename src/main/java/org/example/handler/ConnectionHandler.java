package org.example.handler;

import org.example.Attr;
import org.example.Resource;
import org.example.Status;
import org.example.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
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
            System.out.println(uuid + " exception connection get byte ");
            return;
        }
        int len = childChannel.read(buffer);
        if (len == -1) {
            close(uuid, key, childChannel);
            return;
        }
        //协议最少5位
        if (len < 5) {
            System.out.println(uuid + " 数据包不完整");
            return;
        }
        swapReadMode(buffer);
        byte VER = buffer.get();
        if (0x05 > VER) {
            close(uuid, key, childChannel);
            System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
            return;
        }
        byte CMD = buffer.get();
        if (0x01 != CMD) {
            close(uuid, key, childChannel);
            System.out.println(uuid + " 协议格式不对");
            return;
        }
        byte RSV = buffer.get();
        byte ATYP = buffer.get();
        String host = null;
        Integer port = 0;
        if (0x01 == ATYP) {//IPV4
            if (buffer.remaining() + 1 < 6) {
                swapWriteMode(buffer);
                System.out.println(uuid + " 数据包不完整");
                return;
            }
            host = Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get());
            port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());
            System.out.println(uuid + " IPV4 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
        } else if (0x03 == ATYP) {//域名
            byte hostnameSize = buffer.get();
            if (buffer.remaining() < hostnameSize) {
                swapWriteMode(buffer);
                System.out.println(uuid + " 数据包不完整");
                return;
            }
            byte[] b = new byte[hostnameSize];
            for (int i = 0; i < hostnameSize; i++) {
                b[i] = buffer.get();
            }
            host = Utils.byteToAscii(b);
            port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());
            //按照大端
            System.out.println(uuid + " 域名访问 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
        } else if (0x04 == ATYP) {//IPV6
            System.out.println("不支持IPV6访问");
            close(uuid, key, childChannel);
            return;
        } else {
            System.out.println("不知道的访问方式");
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
        key.attach(attr.status(Status.DELIVER));
        //建立异步连接
        connect(host, port, attr.getUuid(), childChannel, key);

        //更换附件
        DeliverHandler deliverHandler = new DeliverHandler(attr, key, childChannel);
        key.attach(deliverHandler);
    }

    private static void connect(final String host, final Integer port, final String uuid, SocketChannel childChannel, SelectionKey key) {
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
                            System.out.println(uuid + " to remote fail");
                            key.cancel();
                            childChannel.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, 300);
            socketChannel.connect(new InetSocketAddress(host, port));
            timer.cancel();
            socketChannel.configureBlocking(false);
            while (!socketChannel.finishConnect()) {
            }
            selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_READ);
            channelMap.put(uuid, new Resource().remoteClient(socketChannel).selector(selector).childChannel(childChannel).childSKey(key));
            System.out.println(uuid + " connect remote success ");
        } catch (Exception exception) {
            if (exception instanceof AsynchronousCloseException) {
            } else {
                exception.printStackTrace();
            }
            return;
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
                        System.out.println(uuid + " selector 正常退出");
                        break;
                    }
                    int n = finalSelector.select();
                    if (n == 0) {
                        if (!finalSelector.isOpen()) {
                            System.out.println(uuid + " selector 正常退出");
                            break;
                        }
                        continue;
                    }
                    if (n > 1) {
                        System.out.println(uuid + " 监听过多");
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
                                do {
                                    allocate.flip();
                                    childChannel1.write(allocate);
                                    allocate.clear();
                                } while (channel.read(allocate) > 0);
                                if (read < 0) {
                                    System.out.println(uuid + " remote -> read end so close channel and select");
                                    channel.close();
                                    finalSelector.close();
                                    close(uuid, selectionKey1, childChannel1);
                                    break;
                                } else {
                                    System.out.println(uuid + " remote  -> child end");
                                }
                                //这里不能直接通知远端刷，因为异步通知远端后，读事件执行结束。后面select时，因为channel数据还没被读取，会导致再次select出来。
                            } catch (Exception exception) {
                                System.out.println(uuid + " remote error " + exception.getMessage());
                                exception.printStackTrace();
                            }
                        }
                        iterator.remove();
                    }
                }

            } catch (Exception exception) {
                exception.printStackTrace();
            }
        });
        thread.start();
    }
}
