package org.example.protocol.tcp.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.protocol.tcp.entity.TCB;
import org.example.protocol.tcp.entity.TcpFrameProto;
import org.example.protocol.tcp.entity.TcpStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

public class HandsharkHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    ChannelWrapped channelWrapped;
    TCB tcb;

    public HandsharkHandler(ChannelWrapped channelWrapped, TCB tcb) {
        this.channelWrapped = channelWrapped;
        this.tcb = tcb;
    }

    @Override
    public void run() {
        TcpFrameProto.TcpFrame tcpFrame = TcpFrameProto.TcpFrame.newBuilder()
                //源端口
                .setSourcePort(tcb.SourcePort())
                //目标端口8090
                .setDestinationPort(tcb.DestinationPort())
                //握手，初次seq为0
                .setSequenceNumber(tcb.ISS())
                //创建一个连接
                .setSYN(1)
                .build();
        try {
            channelWrapped.channel().write(ByteBuffer.wrap(tcpFrame.toByteArray()));
            LOGGER.info("send {}",tcpFrame.toString());

            //发送完SYN标志位之后，等待对方发送ACK标志
            tcb
                    //更新发送但尚确认的
                    .SND_UNA(tcb.ISS())
                    //syn下一次序列号为初始序号+1
                    .SND_NXT(tcb.ISS()+1)
                    //发送窗口
                    .SND_WND(65535)
                    .TcpStatus(TcpStatus.SYN_SENT);
            LOGGER.info("update {}", tcb.toString());


            TcpClientReceive deliverHandler = new TcpClientReceive(channelWrapped, tcb);
            SelectionKey key = channelWrapped.key();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            channelWrapped.key().attach(deliverHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
