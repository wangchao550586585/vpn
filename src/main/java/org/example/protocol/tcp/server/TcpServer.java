package org.example.protocol.tcp.server;


import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.tcp.entity.TCB;
import org.example.protocol.tcp.entity.TcpFrameProto;
import org.example.protocol.tcp.entity.TcpStatus;

import java.nio.ByteBuffer;

/**
 * 一个连接通过带有SYN标志位的到达段以及由OPEN调用创建的等待TCB条目启动。本地和外部套接字的匹配决定了何时启动连接。当序列号已在两个方向上同步时，连接将变为“已建立”。
 */

/*
Frame 54: 78 bytes on wire (624 bits), 78 bytes captured (624 bits) on interface en0, id 0
        Ethernet II, Src: Apple_16:99:38 (3c:06:30:16:99:38), Dst: Tp-LinkT_21:a2:b8 (60:3a:7c:21:a2:b8)
        Internet Protocol Version 4, Src: 192.168.1.102, Dst: 36.155.189.213
        Transmission Control Protocol, Src Port: 62231, Dst Port: 80, Seq: 0, Len: 0
        Source Port: 62231
        Destination Port: 80
        [Stream index: 2]
        [Conversation completeness: Complete, WITH_DATA (31)]
        [TCP Segment Len: 0]
        Sequence Number: 0    (relative sequence number)
        Sequence Number (raw): 1502734453
        [Next Sequence Number: 1    (relative sequence number)]
        Acknowledgment Number: 0
        Acknowledgment number (raw): 0
        1011 .... = Header Length: 44 bytes (11)
        Flags: 0x002 (SYN)
        Window: 65535
        [Calculated window size: 65535]
        Checksum: 0x388d [unverified]
        [Checksum Status: Unverified]
        Urgent Pointer: 0
        Options: (24 bytes), Maximum segment size, No-Operation (NOP), Window scale, No-Operation (NOP), No-Operation (NOP), Timestamps, SACK permitted, End of Option List (EOL), End of Option List (EOL)
        [Timestamps]
*/
public class TcpServer extends AbstractHandler {
    TCB tcb;

    public TcpServer(ChannelWrapped channelWrapped, TCB tcb) {
        super(channelWrapped);
        this.tcb = tcb;
    }

    @Override
    protected void exec() throws Exception {
        byte[] bytes = channelWrapped.cumulation().readAllByte();
        TcpFrameProto.TcpFrame receive = TcpFrameProto.TcpFrame.parseFrom(bytes);
        LOGGER.info("receive {}", receive.toString());
        //说明握手
        if (receive.getSYN() == 1) {
            int rcv_nxt = receive.getSequenceNumber() + 1;
            TcpFrameProto.TcpFrame tcpFrame = TcpFrameProto.TcpFrame.newBuilder()
                    .setSourcePort(tcb.SourcePort())
                    .setDestinationPort(receive.getSourcePort())
                    //握手，初次seq为0
                    .setSequenceNumber(tcb.IRS())
                    //如果上一次发送的报文是 SYN 报文或者 FIN 报文，则改为 + 1
                    //ack=client-seq+1
                    .setAcknowledgmentNumber(rcv_nxt)
                    //表示确认接收到数据
                    .setACK(1)
                    //创建一个连接
                    .setSYN(1)
                    .build();
            channelWrapped.channel().write(ByteBuffer.wrap(tcpFrame.toByteArray()));
            tcb
                    //更新发送但尚确认的
                    .SND_UNA(tcb.IRS())
                    //syn下一次序列号为初始序号+1
                    .SND_NXT(tcb.IRS() + 1)
                    //发送窗口
                    .SND_WND(65535)
                    //期待下次接收序列号
                    .RCV_NXT(rcv_nxt)
                    .RCV_WND(65535)
                    //等待接收之前发送的syn的ack。
                    .TcpStatus(TcpStatus.SYN_RECEIVED);
            LOGGER.info("update {}", tcb.toString());
            LOGGER.info("send {}", tcpFrame.toString());
        } else if (receive.getACK() == 1) {
            if (receive.getSequenceNumber() == tcb.RCV_NXT()
                    && receive.getAcknowledgmentNumber() == tcb.SND_NXT()) {

                //等待接收之前发送的syn的ack。
                tcb.TcpStatus(TcpStatus.ESTABLISHED);
                LOGGER.info("update {}", tcb.toString());
            }
        }


    }
}
