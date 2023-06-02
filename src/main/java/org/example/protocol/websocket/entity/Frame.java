package org.example.protocol.websocket.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;

public class Frame {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());

    public enum OpcodeEnum {
        //定义“有效负载数据”的解释。
        //%x1 表示一个文本帧
        SEND_UTF(new byte[]{0x00, 0x00, 0x00, 0x01}),
        //%x2 表示一个二进制帧
        SEND_Binary(new byte[]{0x00, 0x00, 0x01, 0x00}),
        //%x8 表示一个连接关闭包
        CLOSE(new byte[]{0x01, 0x00, 0x00, 0x00}),
        //%x9 表示一个ping包
        PING(new byte[]{0x01, 0x00, 0x00, 0x01}),
        //%xA 表示一个pong包
        PONG(new byte[]{0x01, 0x00, 0x01, 0x00});
        byte[] send;

        OpcodeEnum(byte[] send) {
            this.send = send;
        }

        public byte[] send() {
            return send;
        }
    }

    byte fin;
    byte[] rsv;
    byte[] opcode;
    byte mask;
    byte[] payloadLen;  //字节的长度，而不是二进制数据的长度
    byte[] payloadLenExtended;
    byte[] maskingKey;
    byte[] payloadData;
    private int length;
    private final static byte DEFAULT_FIN = 0x01;
    private final static byte[] DEFAULT_RSV = {0x00, 0x00, 0x00};
    private final static byte DEFAULT_MASK = 0x00;
    private final static byte[] DEFAULT_PAYLOAD_LEN = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public static Frame builder() {
        return new Frame();
    }

    public static void defaultFrame(OpcodeEnum opcode, byte[] payloadData, byte[] payloadLen, byte[] payloadLenExtended, SocketChannel channel, String uuid) throws IOException {
        Frame.builder()//构建状态行
                .fin(DEFAULT_FIN)//最后一个包含数据的帧的 FIN （ FIN 帧）字段必须设置为 1 。
                .rsv(DEFAULT_RSV)//固定
                .opcode(opcode.send())  //构建响应头
                .mask(DEFAULT_MASK)//服务端不需要发送掩码
                .payloadLen(payloadLen)
                .payloadLenExtended(payloadLenExtended)
                .payloadData(payloadData)//构建响应体
                .write(channel, uuid);
    }

    public byte[] build() {
        int off = 0;
        byte[] bytes = new byte[length];
        bytes[off++] = fin;
        off = copy(off, bytes, rsv);
        off = copy(off, bytes, opcode);
        bytes[off++] = mask;
        off = copy(off, bytes, payloadLen);
        if (Objects.nonNull(payloadLenExtended)) {
            off = copy(off, bytes, payloadLenExtended);
        }
        if (Objects.nonNull(maskingKey)) {
            off = copy(off, bytes, maskingKey);
        }
        if (Objects.nonNull(payloadData)) {
            off = copy(off, bytes, payloadData);
        }
        return Utils.binary2Bytes(bytes);
    }

    private int copy(int off, byte[] bytes, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            bytes[off++] = target[i];
        }
        return off;
    }

    private Frame self() {
        return this;
    }

    public Frame fin(byte fin) {
        this.fin = fin;
        length += 1;
        return self();
    }

    public Frame rsv(byte[] rsv) {
        this.rsv = rsv;
        length += rsv.length;
        return self();
    }

    public Frame opcode(byte[] opcode) {
        this.opcode = opcode;
        length += opcode.length;
        return self();
    }

    public Frame mask(byte mask) {
        this.mask = mask;
        length += 1;
        return self();
    }

    public Frame payloadLen(byte[] payloadLen) {
        if (Objects.nonNull(payloadLen)) {
            this.payloadLen = payloadLen;
            length += payloadLen.length;
        } else {
            this.payloadLen = DEFAULT_PAYLOAD_LEN;
            length += DEFAULT_PAYLOAD_LEN.length;
        }
        return self();
    }

    public Frame payloadLenExtended(byte[] payloadLenExtended) {
        if (Objects.nonNull(payloadLenExtended)) {
            this.payloadLenExtended = payloadLenExtended;
            length += payloadLenExtended.length;
        }
        return self();
    }

    public Frame maskingKey(byte[] maskingKey) {
        if (Objects.nonNull(maskingKey)) {
            this.maskingKey = maskingKey;
            length += maskingKey.length;
        }
        return self();
    }

    public Frame payloadData(byte[] payloadData) {
        if (Objects.nonNull(payloadData)) {
            this.payloadData = payloadData;
            length += payloadData.length;
        }
        return self();
    }

    public byte fin() {
        return fin;
    }

    public byte[] rsv() {
        return rsv;
    }

    public byte[] opcode() {
        return opcode;
    }

    public byte mask() {
        return mask;
    }

    public byte[] payloadLen() {
        return payloadLen;
    }

    public byte[] payloadLenExtended() {
        return payloadLenExtended;
    }

    public byte[] maskingKey() {
        return maskingKey;
    }

    public byte[] payloadData() {
        return payloadData;
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        byte[] response = build();
        ByteBuffer byteBuffer = ByteBuffer.wrap(response);
        channel.write(byteBuffer);
        LOGGER.info(" response frame {} {} ", this.toString(), uuid);
    }

    @Override
    public String toString() {
        return "Frame{" +
                "fin=" + fin +
                ", rsv=" + Arrays.toString(rsv) +
                ", opcode=" + Arrays.toString(opcode) +
                ", mask=" + mask +
                ", payloadLen=" + Arrays.toString(payloadLen) +
                ", payloadLenExtended=" + Arrays.toString(payloadLenExtended) +
                ", maskingKey=" + Arrays.toString(maskingKey) +
                ", payloadData=" + Arrays.toString(payloadData) +
                '}';
    }
}
