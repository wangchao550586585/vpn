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
                        String VER;
                        String[] msg = null;
                        // TODO: 2023/5/23  处理粘包问题 
                        switch (status) {
                            case AUTH:
                                while (childChannel.read(buffer) > 0) {
                                    buffer.flip();
                                    msg = Utils.bytes2hexFirst(buffer);
                                }
                                if (null == msg || msg.length <= 0) {
                                    System.out.println(uuid + " AUTH 数据包不够，关闭channel");
                                    msg = Utils.bytes2hexFirst(buffer);
                                    key.cancel();
                                    childChannel.close();
                                }
                                try {
                                    VER = msg[0];
                                } catch (Exception e) {
                                    break;
                                }

                                if (!"05".equals(VER) || Integer.parseInt(VER) < 5) {
                                    System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                                }
                                if (msg.length < 3) {
                                    System.out.println(uuid + " 数据包不符合规定");
                                    key.cancel();
                                    childChannel.close();
                                }
                                String NMETHODS = msg[1];
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
                                while (childChannel.read(buffer) > 0) {
                                    buffer.flip();
                                    msg = Utils.bytes2hexFirst(buffer);
                                }
                                if (null == msg || msg.length <= 0) {
                                    System.out.println(uuid + " CONNECTION 数据包不够，关闭channel");
                                    key.cancel();
                                    childChannel.close();
                                }
                                try {
                                    VER = msg[0];
                                } catch (Exception e) {
                                    break;
                                }
                                if (!"05".equals(VER) || Integer.parseInt(VER) < 5) {
                                    System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                                }
                                String CMD = msg[1];
                                if (!"01".equals(CMD)) {
                                    System.out.println(uuid + " 协议格式不对");
                                }
                                String RSV = msg[2];
                                String ATYP = msg[3];
                                String host = null;
                                Integer port = 0;
                                if ("01".equals(ATYP)) {//IPV4
                                    int h1 = Integer.parseInt(msg[4], 16);
                                    int h2 = Integer.parseInt(msg[5], 16);
                                    int h3 = Integer.parseInt(msg[6], 16);
                                    int h4 = Integer.parseInt(msg[7], 16);
                                    host = h1 + "." + h2 + "." + h3 + "." + h4;
                                    port = Integer.parseInt(msg[8] + msg[9], 16);
                                    System.out.println(uuid + " IPV4 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
                                } else if ("03".equals(ATYP)) {//域名
                                    int hostnameSize = Integer.parseInt(msg[4], 16);
                                    String[] hostArr = Arrays.copyOfRange(msg, 4 + 1, hostnameSize + 4 + 1);
                                    host = Utils.hexToAscii(hostArr);
                                    //按照大端
                                    String[] portArr = Arrays.copyOfRange(msg, hostnameSize + 4 + 1, hostnameSize + 4 + 1 + 2);
                                    port = Integer.parseInt(portArr[0] + portArr[1], 16);
                                    System.out.println(uuid + " 域名访问 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
                                } else if ("04".equals(ATYP)) {//IPV6
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
                                writeBuffer.put((byte) Integer.parseInt(ATYP, 16));
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
