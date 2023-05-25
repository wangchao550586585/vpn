package org.example;

import org.jctools.queues.atomic.MpscChunkedAtomicArrayQueue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class SlaveReactor implements Runnable {
    private Selector slaveReactor;
    private Queue<Runnable> taskQueue;
    private volatile int state = ST_NOT_STARTED;
    private static final int ST_NOT_STARTED = 1;
    private static final int ST_STARTED = 2;
    private static final int ST_SHUTDOWN = 4;
    private static final AtomicIntegerFieldUpdater<SlaveReactor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SlaveReactor.class, "state");
    private static Map<String, ByteBuffer> byteBufferMap = new ConcurrentHashMap<String, ByteBuffer>();
    private static Map<String, Resource> channelMap = new ConcurrentHashMap<String, Resource>();

    public SlaveReactor() {
        try {
            this.slaveReactor = Selector.open();
            this.taskQueue = new MpscChunkedAtomicArrayQueue<Runnable>(1024, 1024 * 2);
        } catch (IOException e) {
            System.out.println("打开Selector 失败" + e.getMessage());
        }
    }

    public void start() {
        if (state == ST_NOT_STARTED) {
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    new Thread(this).start();
                    success = true;
                } catch (Exception e) {
                    System.out.println("启动SlaveReactor失败");
                    e.printStackTrace();
                } finally {
                    if (!success) {
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        } else {
            slaveReactor.wakeup();
        }
    }

    @Override
    public void run() {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        while (true) {
            try {
                //醒来可能是有io就绪任务，也可能是普通任务。优先执行io就绪任务。
                //1:有io就绪，处理io就绪。没有task。
                //1:有io就绪，处理io就绪。有task，处理task。
                //2：没io就绪，没有task。。
                //2：没io就绪，有task，处理task。
                int n = -1;
                if (taskQueue.isEmpty()) {
                    n = slaveReactor.select();
                } else {
                    n = slaveReactor.selectNow();
                }
                if (n > 0) {
                    processIO(writeBuffer);
                }
                if (!taskQueue.isEmpty()) {
                    Runnable task = null;
                    while (null != (task = taskQueue.poll())) {
                        task.run();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processIO(ByteBuffer writeBuffer) throws IOException {
        Set<SelectionKey> selectionKeys = slaveReactor.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (!key.isValid()) {
                Attr attr = (Attr) key.attachment();
                String uuid = attr.getUuid();
                System.out.println(uuid);
                key.cancel();
                continue;
            }
            if (key.isReadable()) {
                SocketChannel childChannel = (SocketChannel) key.channel();
                Attr attr = (Attr) key.attachment();
                String uuid = attr.getUuid();
                Status status = attr.getStatus();
                //处理粘包问题
                ByteBuffer buffer = byteBufferMap.get(uuid);
                if (buffer == null) {
                    buffer = ByteBuffer.allocate(4096 * 5);
                    System.out.println(uuid + " 1 buffer position" + buffer.position());
                    buffer.clear();
                    byteBufferMap.put(uuid, buffer);

                }
                //ByteBuffer buffer = byteBufferMap.getOrDefault(uuid, allocate1);
                System.out.println(uuid + " 2 buffer position" + buffer.position());
                int len;
                byte VER;
                switch (status) {
                    case AUTH:
                        len = childChannel.read(buffer);
                        System.out.println(uuid + " 3 buffer position" + buffer.position());
                        if (len == -1) {
                            close(uuid, key, childChannel);
                            continue;
                        }
                        //auth协议最少有3位
                        if (len < 3) {
                            System.out.println(uuid + " 数据包不完整");
                            continue;
                        }
                        swapReadMode(buffer);
                        VER = buffer.get();
                        if (0x05 > VER) {
                            System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                            close(uuid, key, childChannel);
                            continue;
                        }
                        byte NMETHODS = buffer.get();
                        //读取数据不够，接着重读
                        if (buffer.remaining() < NMETHODS) {
                            swapWriteMode(buffer);
                            System.out.println(uuid + " 数据包不完整");
                            continue;
                        }
                        for (int i = 0; i < NMETHODS; i++) {
                            buffer.get();
                        }
                        //说明读取正常,后面的method不校验了，直接clean。
                        buffer.clear();
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
                        len = childChannel.read(buffer);
                        if (len == -1) {
                            close(uuid, key, childChannel);
                            continue;
                        }
                        //协议最少5位
                        if (len < 5) {
                            System.out.println(uuid + " 数据包不完整");
                            continue;
                        }
                        swapReadMode(buffer);
                        VER = buffer.get();
                        if (0x05 > VER) {
                            close(uuid, key, childChannel);
                            System.out.println(uuid + " 版本号错误或版本过低，只能支持5");
                            continue;
                        }
                        byte CMD = buffer.get();
                        if (0x01 != CMD) {
                            close(uuid, key, childChannel);
                            System.out.println(uuid + " 协议格式不对");
                            continue;
                        }
                        byte RSV = buffer.get();
                        byte ATYP = buffer.get();
                        String host = null;
                        Integer port = 0;
                        if (0x01 == ATYP) {//IPV4
                            if (buffer.remaining() + 1 < 6) {
                                swapWriteMode(buffer);
                                System.out.println(uuid + " 数据包不完整");
                                continue;
                            }
                            host = Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get()) + "." + Utils.byteToInt(buffer.get());
                            port = Utils.byteToInt(buffer.get()) * 256 + Utils.byteToInt(buffer.get());
                            System.out.println(uuid + " IPV4 host: " + host + " port:" + port + " remoteAddress" + childChannel.getRemoteAddress());
                        } else if (0x03 == ATYP) {//域名
                            byte hostnameSize = buffer.get();
                            if (buffer.remaining() < hostnameSize) {
                                swapWriteMode(buffer);
                                System.out.println(uuid + " 数据包不完整");
                                continue;
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
                            continue;
                        } else {
                            System.out.println("不知道的访问方式");
                            close(uuid, key, childChannel);
                            continue;
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
                                resource.getSelector().close(); //close调用会调用wakeup
                                close(uuid, key, childChannel);
                            } else {
                                do {
                                    buffer.flip();
                                    //nPrint(buffer, uuid + ": 写入远程服务器数据为：");
                                    //nPrintByte(buffer, uuid + " child -> remote ：");
                                    remoteClient.write(buffer);
                                    buffer.clear();
                                } while (childChannel.read(buffer) > 0);
                                System.out.println(uuid + " child -> remote  end");
                            }
                        } catch (Exception exception) {
                            System.out.println(uuid + " error child  close" + exception.getMessage());
                            remoteClient.close();
                            resource.getSelector().close();
                            close(uuid, key, childChannel);
                            //exception.printStackTrace();
                        }
                        break;
                }
            }
            iterator.remove();
        }
    }

    private static void close(String uuid, SelectionKey key, SocketChannel childChannel) {
        byteBufferMap.remove(uuid);
        key.cancel();
        try {
            childChannel.close();
        } catch (IOException e) {
            System.out.println(uuid + " error close childChannel");
            throw new RuntimeException(e);
        }
    }

    private void swapReadMode(ByteBuffer buffer) {
        buffer.flip();
        buffer.mark();

    }

    private void swapWriteMode(ByteBuffer buffer) {
        buffer.reset();
        buffer.flip();
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

    public void register(SocketChannel childChannel) throws IOException {
        taskQueue.offer(() -> {
            try {
                childChannel.configureBlocking(false);
                //select和register会造成阻塞
                SelectionKey selectionKey = childChannel.register(slaveReactor, 0);
                String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                selectionKey.attach(new Attr().status(Status.AUTH).uuid(uuid));
                selectionKey.interestOps(SelectionKey.OP_READ);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        //初次进来会启动reactor
        start();
    }
}
