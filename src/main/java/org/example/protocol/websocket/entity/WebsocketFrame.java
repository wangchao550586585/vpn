package org.example.protocol.websocket.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.util.Pair;
import org.example.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Objects;

import static org.example.util.Utils.copy;

public class WebsocketFrame {
    protected static final Logger LOGGER = LogManager.getLogger(WebsocketFrame.class);

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
    public final static byte DEFAULT_MASK = 0x00;
    public final static byte DEFAULT_HAS_MASK = 0x01;
    private final static byte[] DEFAULT_PAYLOAD_LEN = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    public static WebsocketFrame builder() {
        return new WebsocketFrame();
    }

    public static void defaultFrame(OpcodeEnum opcode, byte mask, byte[] payloadLen, byte[] payloadLenExtended, byte[] maskingKey, byte[] payloadData, SocketChannel channel, String uuid) throws IOException {
        WebsocketFrame.builder()//构建状态行
                .fin(DEFAULT_FIN)//最后一个包含数据的帧的 FIN （ FIN 帧）字段必须设置为 1 。
                .rsv(DEFAULT_RSV)//固定
                .opcode(opcode.send())  //构建响应头
                .mask(mask)//客户端需要掩码
                .payloadLen(payloadLen)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(maskingKey)
                .payloadData(payloadData)//构建响应体
                .write(channel, uuid);
    }

    /**
     * 服务端默认发送
     *
     * @param msg
     * @throws IOException
     */
    public static void serverSendUTF(String msg, SocketChannel channel, String uuid) throws IOException {
        byte[] payloadData = msg.getBytes();
        //测试超过126位
        //payloadData = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes();
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length);
        payloadData = Utils.bytes2Binary(payloadData);
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_UTF,
                DEFAULT_MASK,
                pair.getFirst(),
                pair.getSecond(),
                null,
                payloadData,
                channel,
                uuid);
    }
    /**
     * 客户端默认发送
     *
     * @param msg
     * @param channel
     * @param uuid
     * @throws IOException
     */
    public static void clientSendUTF(String msg, SocketChannel channel, String uuid) throws IOException {
        //发送一个hello
        byte[] payloadData = msg.getBytes();
        //构建长度
        Pair<byte[], byte[]> pair = getLength(payloadData.length);

        //4字节
        byte[] maskingKey = Utils.buildMask();
        payloadData = Utils.mask(payloadData, maskingKey);
        maskingKey = Utils.bytes2Binary(maskingKey);
        payloadData = Utils.bytes2Binary(payloadData);

        //“负载字段”是用UTF-8编码的文本数据。
        WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_UTF,
                DEFAULT_HAS_MASK,
                pair.getFirst(),
                pair.getSecond(),
                maskingKey,
                payloadData,
                channel,
                uuid);
    }
    /**
     * 获取长度和扩展字段
     *
     * @param length
     * @return
     */
    private static Pair<byte[], byte[]> getLength(int length) {
        byte[] payloadLenExtended = null;
        byte[] payloadLen = null;
        if (length < 126) {
            payloadLen = Utils.bytes2Binary((byte) length);
            //这里len只有7位
            payloadLen = Arrays.copyOfRange(payloadLen, 1, payloadLen.length);
        } else if (length >= 126 && length <= 65535) {
            payloadLen = Utils.bytes2Binary((byte) 126);
            //这里len只有7位
            payloadLen = Arrays.copyOfRange(payloadLen, 1, payloadLen.length);
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            //字节长度量以网络字节顺序表示
            payloadLenExtended = Utils.int2BinaryA2Byte(length);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            // TODO: 2023/6/1 超过65535太长了，用不着
        }
        return new Pair<>(payloadLen, payloadLenExtended);
    }

    public static WebsocketFrame parse(ChannelWrapped channelWrapped) {
        byte[] frame = channelWrapped.cumulation().binaryString();
        String s = Utils.buildBinaryReadable(frame);
        LOGGER.info("receive frame {} {}", s, channelWrapped.uuid());
        //表示这是消息的最后一个片段。第一个片段也有可能是最后一个片段。
        int off = 0;
        byte fin = frame[off];
        //必须设置为0，除非扩展了非0值含义的扩展。如果收到了一个非0值但是没有扩展任何非0值的含义，接收终端必须断开WebSocket连接。
        off++;
        byte[] rsv = Arrays.copyOfRange(frame, off, off + 3);
        off += 3;
        for (int i = 0; i < rsv.length; i++) {
            if (rsv[i] != 0x00) {
                return null;
            }
        }
        /**
         * 定义“有效负载数据”的解释。如果收到一个未知的操作码，接收终端必须断开WebSocket连接。下面的值是被定义过的。
         * %x0 表示一个持续帧
         * %x1 表示一个文本帧
         * %x2 表示一个二进制帧
         * %x3-7 预留给以后的非控制帧
         * %x8 表示一个连接关闭包
         * %x9 表示一个ping包
         * %xA 表示一个pong包
         * %xB-F 预留给以后的控制帧
         */
        byte[] opcode = Arrays.copyOfRange(frame, off, off + 4);
        off += 4;
        //定义“有效负载数据”是否添加掩码。默认1，掩码的键值存在于Masking-Key中
        byte mask = frame[off];
        off++;
        byte[] payloadLenBinary = Arrays.copyOfRange(frame, off, off + 7);
        byte[] payloadLenExtended = null;
        off += 7;
        //表示有多少个字节，而不是01。
        int payloadLen = Utils.binary2Int(payloadLenBinary);
        if (payloadLen <= 125) {
            //如果值为0-125，那么就表示负载数据的长度。
        } else if (payloadLen == 126) {
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            payloadLenExtended = Arrays.copyOfRange(frame, off, (off + 2 * 8));
            off += 2 * 8;
            payloadLen = Utils.binary2Int(payloadLenExtended);
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            payloadLenExtended = Arrays.copyOfRange(frame, off, (off + 8 * 8));
            off += 8 * 8;
            payloadLen = Utils.binary2Int(payloadLenExtended);
        }
        //所有从客户端发往服务端的数据帧都已经与一个包含在这一帧中的32 bit的掩码进行过了运算。
        //如果mask标志位（1 bit）为1，那么这个字段存在，如果标志位为0，那么这个字段不存在。
        byte[] maskingKey = null;
        if (mask == 1) {
            maskingKey = Arrays.copyOfRange(frame, off, (off + 4 * 8));
            off += 4 * 8;
        }
        //“有效负载数据”是指“扩展数据”和“应用数据”。
        byte[] payloadData = Arrays.copyOfRange(frame, off, (off + payloadLen * 8));
        off += payloadLen * 8;
        return WebsocketFrame.builder()
                .fin(fin)
                .rsv(rsv)
                .opcode(opcode)
                .mask(mask)
                .payloadLen(payloadLenBinary)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(maskingKey)
                .payloadData(payloadData);
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

    private WebsocketFrame self() {
        return this;
    }

    public WebsocketFrame fin(byte fin) {
        this.fin = fin;
        length += 1;
        return self();
    }

    public WebsocketFrame rsv(byte[] rsv) {
        this.rsv = rsv;
        length += rsv.length;
        return self();
    }

    public WebsocketFrame opcode(byte[] opcode) {
        this.opcode = opcode;
        length += opcode.length;
        return self();
    }

    public WebsocketFrame mask(byte mask) {
        this.mask = mask;
        length += 1;
        return self();
    }

    public WebsocketFrame payloadLen(byte[] payloadLen) {
        if (Objects.nonNull(payloadLen)) {
            this.payloadLen = payloadLen;
            length += payloadLen.length;
        } else {
            this.payloadLen = DEFAULT_PAYLOAD_LEN;
            length += DEFAULT_PAYLOAD_LEN.length;
        }
        return self();
    }

    public WebsocketFrame payloadLenExtended(byte[] payloadLenExtended) {
        if (Objects.nonNull(payloadLenExtended)) {
            this.payloadLenExtended = payloadLenExtended;
            length += payloadLenExtended.length;
        }
        return self();
    }

    public WebsocketFrame maskingKey(byte[] maskingKey) {
        if (Objects.nonNull(maskingKey)) {
            this.maskingKey = maskingKey;
            length += maskingKey.length;
        }
        return self();
    }

    public WebsocketFrame payloadData(byte[] payloadData) {
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
