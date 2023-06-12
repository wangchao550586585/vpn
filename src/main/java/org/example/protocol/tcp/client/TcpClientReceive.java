package org.example.protocol.tcp.client;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.tcp.entity.TCB;
import org.example.protocol.tcp.entity.TcpFrameProto;
import org.example.protocol.tcp.entity.TcpStatus;

import java.nio.ByteBuffer;

public class TcpClientReceive extends AbstractHandler {
    TCB tcb;


    public TcpClientReceive(ChannelWrapped channelWrapped, TCB tcb) {
        super(channelWrapped);
        this.tcb = tcb;
    }

    @Override
    protected void exec() throws Exception {
        byte[] bytes = channelWrapped.cumulation().readAllByte();
        TcpFrameProto.TcpFrame receive = TcpFrameProto.TcpFrame.parseFrom(bytes);
        LOGGER.info("receive {}", receive.toString());
        //二次握手成功
        if (receive.getSYN() == 1 && receive.getACK() == 1) {
            //下一次发送的==服务端期待下一次接收的
            if (tcb.SND_NXT() == receive.getAcknowledgmentNumber()) {
                int rcv_next = receive.getSequenceNumber() + 1;
                TcpFrameProto.TcpFrame tcpFrame = TcpFrameProto.TcpFrame.newBuilder()
                        .setSourcePort(tcb.SourcePort())
                        //目标端口8090
                        .setDestinationPort(tcb.DestinationPort())
                        //如果上一次发送的报文是 SYN 报文或者 FIN 报文，则改为 + 1
                        //seq=lastSeq+1
                        .setSequenceNumber(tcb.SND_NXT())
                        //如果收到的是 SYN 报文或者 FIN 报文，则改为 + 1
                        //ack=server-seq+1
                        .setAcknowledgmentNumber(rcv_next)
                        //表示确认接收到数据
                        .setACK(1)
                        .build();
                channelWrapped.channel().write(ByteBuffer.wrap(tcpFrame.toByteArray()));
                LOGGER.info("send {}", tcpFrame.toString());

                //发送完SYN标志位之后，等待对方发送ACK标志
                tcb
                        //更新发送但尚确认的
                        .SND_UNA(tcb.SND_NXT())
                        //因为协议升级成功，可以省略这个了
                        //.SND_NXT(tcb.SND_NXT()+1)
                        //发送窗口
                        .SND_WND(65535)
                        //因为协议升级成功，可以省略这个了
                        .RCV_NXT(rcv_next)
                        //接收窗口
                        .RCV_WND(65535)
                        //连接已经建立，用户进程可以收发数据。
                        .TcpStatus(TcpStatus.ESTABLISHED);
                LOGGER.info("update {}", tcb.toString());
            }
        } else if (receive.getACK() == 1) {
            if (receive.getSequenceNumber() == tcb.RCV_NXT()
                    && receive.getAcknowledgmentNumber() == tcb.SND_NXT()) {
                //非fin，所以等于字符长度+seq
                int rcv_nxt = receive.getSequenceNumber() + receive.getData().size();
                //发送完SYN标志位之后，等待对方发送ACK标志
                tcb
                        //更新发送但尚确认的
                        .SND_UNA(tcb.SND_NXT())
                        //发送窗口
                        .SND_WND(65535)
                        .RCV_NXT(rcv_nxt)
                        .RCV_WND(65535);
                LOGGER.info("update {}", tcb.toString());
            }
        }
    }
}
