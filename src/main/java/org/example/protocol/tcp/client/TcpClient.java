package org.example.protocol.tcp.client;

import com.google.protobuf.ByteString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.protocol.tcp.entity.TCB;
import org.example.protocol.tcp.entity.TcpFrameProto;
import org.example.protocol.tcp.entity.TcpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class TcpClient implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    final String host;
    final Integer port;
    Selector remoteSelector;
    SocketChannel remoteChannel;
    volatile boolean flag;
    TCB tcb;

    public static void main(String[] args) {
        new TcpClient("127.0.0.1", 8090).connect().write();
    }

    private void write() {
        Scanner s = new Scanner(System.in);
        LOGGER.info("请输入字符串:");
        while (true) {
            String line = s.nextLine();
            if (line.equals("ok")) {
                //执行结束
                doClose();
                break;
            }
            doWrite(line);
        }
    }

    private void doClose() {
        TcpFrameProto.TcpFrame tcpFrame = TcpFrameProto.TcpFrame.newBuilder()
                .setSourcePort(tcb.SourcePort())
                //目标端口8090
                .setDestinationPort(tcb.DestinationPort())
                //如果上一次发送的报文是 SYN 报文或者 FIN 报文，则改为 + 1
                //seq=lastSeq+1
                .setSequenceNumber(tcb.SND_NXT())
                //如果收到的是 SYN 报文或者 FIN 报文，则改为 + 1
                //ack=server-seq+1
                .setAcknowledgmentNumber(tcb.RCV_NXT())
                //结束
                .setFIN(1)
                //表示确认接收到数据
                .setACK(1)
                .build();
        try {
            LOGGER.info("send {}", tcpFrame.toString());
            tcb
                    //更新发送但尚确认的
                    .SND_UNA(tcb.SND_NXT())
                    //下一次序列号为初始序号+data.length
                    .SND_NXT(tcb.SND_NXT() + 1)
                    //发送窗口
                    .SND_WND(65535)
                    //接收窗口
                    .RCV_WND(65535)
                    //连接已经建立，用户进程可以收发数据。
                    .TcpStatus(TcpStatus.FIN_WAIT_1);
            LOGGER.info("update {}", tcb.toString());
            remoteChannel.write(ByteBuffer.wrap(tcpFrame.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void doWrite(String msg) {
        ByteString data = ByteString.copyFrom(msg.getBytes());
        TcpFrameProto.TcpFrame tcpFrame = TcpFrameProto.TcpFrame.newBuilder()
                .setSourcePort(tcb.SourcePort())
                //目标端口8090
                .setDestinationPort(tcb.DestinationPort())
                //如果上一次发送的报文是 SYN 报文或者 FIN 报文，则改为 + 1
                //seq=lastSeq+1
                .setSequenceNumber(tcb.SND_NXT())
                //如果收到的是 SYN 报文或者 FIN 报文，则改为 + 1
                //ack=server-seq+1
                .setAcknowledgmentNumber(tcb.RCV_NXT())
                //表示确认接收到数据
                .setACK(1)
                //推送标志位
                .setPSH(1)
                .setData(data)
                .build();
        try {
            LOGGER.info("send {}", tcpFrame.toString());
            tcb
                    //更新发送但尚确认的
                    .SND_UNA(tcb.SND_NXT())
                    //下一次序列号为初始序号+data.length
                    .SND_NXT(tcb.SND_NXT() + data.size())
                    //发送窗口
                    .SND_WND(65535)
                    //接收窗口
                    .RCV_WND(65535)
                    //连接已经建立，用户进程可以收发数据。
                    .TcpStatus(TcpStatus.ESTABLISHED);
            LOGGER.info("update {}", tcb.toString());
            remoteChannel.write(ByteBuffer.wrap(tcpFrame.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public TcpClient(String host, Integer port) {
        this.host = host;
        this.port = port;
        this.flag = true;
    }

    public TcpClient connect() {
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
            this.tcb = new TCB().ISS(0).SourcePort(3000).DestinationPort(port);
            Runnable handler = new HandsharkHandler(channelWrapped, tcb);
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
            return null;
        }
        new Thread(this).start();
        return this;
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
        } finally {
            if (Objects.nonNull(remoteChannel)) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (Objects.nonNull(remoteSelector)) {
                try {
                    remoteSelector.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}