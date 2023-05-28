package org.example.handler;

import org.example.CompositeByteBuf;
import org.example.entity.Resource;
import org.example.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ConnectionHandler extends AbstractHandler {
    public ConnectionHandler(SelectionKey key, SocketChannel childChannel, String uuid, CompositeByteBuf cumulation) {
        super(key, childChannel, uuid);
        super.cumulation = cumulation;
    }

    public void exec() throws IOException {
        ByteBuffer writeBuffer = ByteBuffer.allocate(4096 * 5);
        int len = cumulation.remaining();
        //协议最少5位
        if (len < 5) {
            LOGGER.warn("数据包不完整 {}", uuid);
            return;
        }
        cumulation.mark();
        byte VER = cumulation.get();
        if (0x05 > VER) {
            closeChildChannel();
            LOGGER.warn("版本号错误或版本过低，只能支持5 {}", uuid);
            return;
        }
        byte CMD = cumulation.get();
        if (0x01 != CMD) {
            closeChildChannel();
            LOGGER.warn("协议格式不对 {}", uuid);
            return;
        }
        byte RSV = cumulation.get();
        byte ATYP = cumulation.get();
        String host = null;
        Integer port = 0;
        if (0x01 == ATYP) {//IPV4
            if (cumulation.remaining() + 1 < 6) {
                cumulation.reset();
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            host = Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get()) + "." + Utils.byteToInt(cumulation.get());
            port = Utils.byteToInt(cumulation.get()) * 256 + Utils.byteToInt(cumulation.get());

            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, childChannel.getRemoteAddress(), uuid);
        } else if (0x03 == ATYP) {//域名
            byte hostnameSize = cumulation.get();
            if (cumulation.remaining() < hostnameSize) {
                cumulation.reset();
                LOGGER.warn("数据包不完整 {}", uuid);
                return;
            }
            byte[] b = new byte[hostnameSize];
            for (int i = 0; i < hostnameSize; i++) {
                b[i] = cumulation.get();
            }
            host = Utils.byteToAscii(b);
            //按照大端
            port = Utils.byteToInt(cumulation.get()) * 256 + Utils.byteToInt(cumulation.get());
            LOGGER.info("IPV4 host:{}  port:{}  remoteAddress:{} {}", host, port, childChannel.getRemoteAddress(), uuid);
        } else if (0x04 == ATYP) {//IPV6
            LOGGER.warn("不支持IPV6访问 {}", uuid);
            closeChildChannel();
            return;
        } else {
            LOGGER.warn("不知道的访问方式 {}", uuid);
            closeChildChannel();
            return;
        }
        //说明正常读取结束，切换为写模式。
        cumulation.clear();

        writeBuffer.put((byte) 5);
        writeBuffer.put((byte) 0);
        writeBuffer.put((byte) 0);
        //这里写死，后面紧接着6位hose和port
        writeBuffer.put((byte) 1);
        //put host
        writeBuffer.put(new byte[]{0, 0, 0, 0});
        //put port
        writeBuffer.put(new byte[]{0, 0});
        writeBuffer.flip();
        childChannel.write(writeBuffer);
        writeBuffer.clear();
        //建立异步连接
        boolean connectSuccess = connect(host, port, uuid, childChannel);
        if (connectSuccess) {
            //更换附件
            DeliverHandler deliverHandler = new DeliverHandler(key, childChannel, uuid, cumulation);
            key.attach(deliverHandler);
        } else {
            closeChildChannel();
        }
    }

    private boolean connect(final String host, final Integer port, final String uuid, SocketChannel childChannel) {
        Selector remoteSelector = null;
        SocketChannel remoteChannel = null;
        try {
            remoteChannel = SocketChannel.open();
            Timer timer = new Timer();
            final SocketChannel finalSocketChannel = remoteChannel;
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (finalSocketChannel == null || !finalSocketChannel.isConnected()) {
                        try {
                            finalSocketChannel.close();
                            LOGGER.warn("remote connect timeout {}", uuid);
                        } catch (Exception e) {
                            LOGGER.error("remote connect fail , so close fail " + uuid, e);
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, 300);
            remoteChannel.connect(new InetSocketAddress(host, port));
            LOGGER.info("remote connect success {} remoteAddress {} ", uuid, remoteChannel.getRemoteAddress());
            timer.cancel();
            remoteChannel.configureBlocking(false);
            while (!remoteChannel.finishConnect()) {
            }
            remoteSelector = Selector.open();
            remoteChannel.register(remoteSelector, SelectionKey.OP_READ);
            channelMap.put(uuid, new Resource().remoteClient(remoteChannel).remoteSelector(remoteSelector).childChannel(childChannel));
            LOGGER.info("remote register success {}", uuid);
        } catch (Exception exception) {
            if (exception instanceof AsynchronousCloseException) {
                LOGGER.info("remote connect fail {}", uuid);
            } else {
                LOGGER.error("remote connect fail " + uuid, exception);
            }
            if (null != remoteChannel) {
                try {
                    remoteChannel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (null != remoteSelector) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            //这里不能往上抛异常
            return false;
        }
        Selector finalSelector = remoteSelector;
        Thread thread = new Thread(() -> {
            if (null == finalSelector) {
                return;
            }
            Resource resource = channelMap.get(uuid);
            SocketChannel childChannel1 = resource.getChildChannel();
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
                        iterator.remove();
                        SocketChannel channel = (SocketChannel) selectionKey.channel();
                        if (selectionKey.isReadable()) {
                            ByteBuffer allocate = ByteBuffer.allocate(4096 * 5);
                            int read = channel.read(allocate);
                            if (read < 0) {
                                LOGGER.info("remote read end {}", uuid);
                                channel.close();
                                finalSelector.close();
                                closeChildChannel();
                                break;
                            }
                            do {
                                allocate.flip();
                                childChannel1.write(allocate);
                                allocate.clear();
                            } while (channel.read(allocate) > 0);
                            LOGGER.info("remote  -> child end {}", uuid);
                            //这里不能直接通知远端刷，因为异步通知远端后，读事件执行结束。后面select时，因为channel数据还没被读取，会导致再次select出来。
                        }
                    }
                }
            } catch (Exception exception) {
                closeChildChannel();
                LOGGER.error("remote select fail " + uuid, exception);
                throw new RuntimeException(exception);
            }
        });
        thread.start();
        return true;
    }

}
