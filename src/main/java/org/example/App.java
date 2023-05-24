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

    public static void main(String[] args) throws Exception {
        new App().vpnStart();
    }

    private void vpnStart() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(1080));
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(4096 * 5);
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        while (true) {
            int n = selector.select();
            if (n == 0) {
                continue;
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            try {
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (!key.isValid()) {
                        Attr attr = (Attr) key.attachment();
                        String uuid = attr.getUuid();
                        System.out.println(uuid);
                        key.cancel();
                        continue;
                    }
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        childChannel.configureBlocking(false);
                        SelectionKey register = childChannel.register(selector, 0);
                        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                        register.attach(new Attr().status(Status.AUTH).uuid(uuid));
                        register.interestOps(register.interestOps() & ~SelectionKey.OP_ACCEPT | SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel childChannel = (SocketChannel) key.channel();
                        Attr attr = (Attr) key.attachment();
                        String uuid = attr.getUuid();
                        Status status = attr.getStatus();
                        String[] msg = null;
                        // TODO: 2023/5/23  处理粘包问题
                        int len;
                        byte VER;
                        switch (status) {
                            case AUTH:
                                len = childChannel.read(buffer);
                                if (len == -1) {
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
                                buffer.flip();
                                VER = buffer.get();
                                if (0x05 > VER) {
                                    System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
                                byte NMETHODS = buffer.get();
                                //2~255
                                key.attach(attr.status(Status.CONNECTION));
                                writeBuffer.put((byte) 5);
                                writeBuffer.put((byte) 0);
                                writeBuffer.flip();
                                childChannel.write(writeBuffer);
                                writeBuffer.clear();
                                System.out.println(uuid + " 鉴权成功");
                                break;
                            case CONNECTION:
                                len = 0;
                                try {
                                    len = childChannel.read(buffer);
                                } catch (Exception e) {
                                    System.out.println(uuid);
                                    e.printStackTrace();
                                }
                                if (len == -1) {
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
                                buffer.flip();
                                VER = buffer.get();
                                if (0x05 > VER) {
                                    System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
                                byte CMD = buffer.get();
                                if (0x01 != CMD) {
                                    System.out.println(uuid + " 协议格式不对");
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
                                byte RSV = buffer.get();
                                byte ATYP = buffer.get();
                                String host = null;
                                Integer port = 0;
                                if (0x01 == ATYP) {//IPV4
                                    host = Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get());
                                    port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());
                                    System.out.println(uuid + " IPV4 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
                                } else if (0x03 == ATYP) {//域名
                                    byte hostnameSize = buffer.get();
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
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                } else {
                                    System.out.println("不知道的访问方式");
                                    key.cancel();
                                    childChannel.close();
                                    continue;
                                }
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
                                key.attach(attr.status(Status.RECOVE));
                                //建立异步连接
                                connect(host, port, attr.getUuid(), childChannel, key);
                                break;
                            case RECOVE:
                                Resource resource = channelMap.get(uuid);
                                if (Objects.isNull(resource)) {
                                    // 走到这里说明连接远端地址失败，因为他会关闭流，所以跳过即可。
                                    System.out.println(uuid + " exception child  close");
                                    continue;
                                }
                                SocketChannel remoteClient = resource.getRemoteClient();
                                try {
                                    //获取服务端数据
                                    if (!childChannel.isOpen()) {
                                        System.out.println(uuid + " channel 已经关闭");
                                        break;
                                    }
                                    int read = childChannel.read(buffer);
                                    if (read < 0) {
                                        System.out.println(uuid + " child  close");
                                        remoteClient.close();
                                        //resource.getSelector().wakeup();
                                        resource.getSelector().close(); //close调用会调用wakeup
                                        key.cancel();
                                        childChannel.close();
                                    } else {
                                        do {
                                            buffer.flip();
                                            //nPrint(buffer, uuid + ": 写入远程服务器数据为：");
                                            //nPrintByte(buffer, uuid + " child -> remote ：");
                                            remoteClient.write(buffer);
                                            buffer.flip();
                                        } while (childChannel.read(buffer) > 0);
                                        System.out.println(uuid + " child -> remote  end");
                                    }
                                } catch (Exception exception) {
                                    System.out.println(uuid + " error child  close" + exception.getMessage());
                                    remoteClient.close();
                                    resource.getSelector().close();
                                    key.cancel();
                                    childChannel.close();
                                    //exception.printStackTrace();
                                }
                                break;
                        }
                        buffer.clear();
                    }
                    iterator.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                                    allocate.flip();
                                } while (channel.read(allocate) > 0);
                                if (read < 0) {
                                    System.out.println(uuid + " remote -> read end so close channel and select");
                                    channel.close();
                                    finalSelector.close();
                                    selectionKey1.cancel();
                                    childChannel1.close();
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
