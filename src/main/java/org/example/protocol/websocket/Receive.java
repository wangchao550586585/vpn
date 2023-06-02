package org.example.protocol.websocket;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.Request;
import org.example.protocol.websocket.entity.WebsocketFrame;
import org.example.util.Utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-------+-+-------------+-------------------------------+
 * |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
 * |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
 * |N|V|V|V|       |S|             |   (if payload len==126/127)   |
 * | |1|2|3|       |K|             |                               |
 * +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
 * |     Extended payload length continued, if payload len == 127  |
 * + - - - - - - - - - - - - - - - +-------------------------------+
 * |                               |Masking-key, if MASK set to 1  |
 * +-------------------------------+-------------------------------+
 * | Masking-key (continued)       |          Payload Data         |
 * +-------------------------------- - - - - - - - - - - - - - - - +
 * :                     Payload Data continued ...                :
 * + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
 * |                     Payload Data continued ...                |
 * +---------------------------------------------------------------+
 */
public class Receive extends AbstractHandler {
    private final Request request;

    public Receive(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
    }

    @Override
    protected void exec() throws Exception {
        //没有读到对应的终止符则返回接着继续读。
        /*客户端必须在它发送到服务器的所有帧中添加掩码（Mask）（具体细节见5.3节）。
        （注意：无论WebSocket协议是否使用了TLS，帧都需要添加掩码）。服务端收到没有添加掩码的数据帧以后，
        必须立即关闭连接。在这种情况下，服务端可以发送一个在7.4.1节定义的状态码为1002（协议错误）的关闭帧。
        服务端禁止在发送数据帧给客户端时添加掩码。
        客户端如果收到了一个添加了掩码的帧，必须立即关闭连接。
        在这种情况下，它可以使用第7.4.1节定义的1002（协议错误）状态码。（*/
        String uuid = channelWrapped.uuid();
        WebsocketFrame frame = parse(channelWrapped);
        //协议错误，断开连接
        if (Objects.isNull(frame)) {
            closeChildChannel();
            return;
        }
        LOGGER.info("Receive {} {} ", frame.toString(), uuid);
        String msg = "";
        byte[] sendPayloadData;
        byte[] sendPayloadLen = null;
        byte[] tempPayloadData = frame.payloadData();
        byte[] payloadLenExtended = null;
        switch (Utils.binary2Int(frame.opcode())) {
            case 0x00:
                break;
            case 0x01:
                //响应数据，掩码
                sendPayloadData = "接收成功".getBytes();
                //“负载字段”是用UTF-8编码的文本数据。
                if (tempPayloadData.length > 0) {
                    if (frame.mask() == 1) {
                        msg = Utils.unmask(tempPayloadData, frame.maskingKey());
                    } else {
                        msg = new String(tempPayloadData, "utf-8");
                    }
                    if (msg.equals("ping")) {
                        sendPayloadData = "pong".getBytes();
                    }
                    LOGGER.info("receive msg {} {} ", msg, uuid);
                }
                //测试超过126位
                //sendPayloadData = "123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456".getBytes();
                //构建长度
                if (sendPayloadData.length < 126) {
                    sendPayloadLen = Utils.bytes2Binary((byte) sendPayloadData.length);
                    //这里len只有7位
                    sendPayloadLen = Arrays.copyOfRange(sendPayloadLen, 1, sendPayloadLen.length);
                } else if (sendPayloadData.length >= 126 && sendPayloadData.length <= 65535) {
                    sendPayloadLen = Utils.bytes2Binary((byte) 126);
                    //这里len只有7位
                    sendPayloadLen = Arrays.copyOfRange(sendPayloadLen, 1, sendPayloadLen.length);
                    //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
                    //字节长度量以网络字节顺序表示
                    payloadLenExtended = Utils.int2BinaryA2Byte(sendPayloadData.length);
                } else {
                    //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
                    // TODO: 2023/6/1 超过65535太长了，用不着
                }
                sendPayloadData = Utils.bytes2Binary(sendPayloadData);
                WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.SEND_UTF, sendPayloadData, sendPayloadLen, payloadLenExtended, channelWrapped.channel(), channelWrapped.uuid());
                break;
            case 0x02:
                //二进制帧
                break;
            case 0x08:
                /**
                 * 关闭帧可能包含内容（body）（帧的“应用数据”部分）来表明连接关闭的原因，
                 * 如果这个内容存在，内容的前两个字节必须是一个无符号整型（按照网络字节序）来代表定义的状态码。
                 * 跟在这两个整型字节之后的可以是UTF-8编码的的数据值（原因）
                 *
                 * 从客户端发送给服务端的控制帧必须添加掩码
                 * 应用禁止在发送了关闭的控制帧后再发送任何的数据帧。
                 * 如果终端收到了一个关闭的控制帧并且没有在以前发送一个关闭帧，那么终端必须发送一个关闭帧作为回应。
                 *
                 * 当发送一个关闭帧作为回应时，终端通常会输出它收到的状态码）响应的关闭帧应该尽快发送。
                 * 终端可能会推迟发送关闭帧直到当前的消息都已经发送完成（例如：如果大多数分片的消息已经发送了，
                 * 终端可能会在发送关闭帧之前将剩余的消息片段发送出去）。然而，已经发送关闭帧的终端不能保证会继续处理收到的消息。
                 *
                 * 在已经发送和收到了关闭帧后，终端认为WebSocket连接已经关闭了，并且必须关闭底层的TCP连接。
                 * 服务端必须马上关闭底层的TCP连接，客户端应该等待服务端关闭连接，但是也可以在收到关闭帧以后任意时间关闭连接。
                 * 例如：如果在合理的时间段内没有收到TCP关闭指令。
                 * 如果客户端和服务端咋同一个时间发送了关闭帧，两个终端都会发送和接收到一条关闭的消息，并且应该认为WebSocket连接已经关闭，
                 * 同时关闭底层的TCP连接。
                 *
                 * 在底层的 TCP 连接中，通常大多数情况下，服务端应该先关闭，所以是服务端而不是客户端保持 TIME_WAIT 状态
                 * （因为客户端先关闭的话，这会阻止服务端在2 MSL 内重新打开这条连接，而如果服务器处于 TIME_WAIT 状态下，
                 * 如果收到了一个带有更大序列号的新的 SYN 包时，也能够立即响应重新打开连接，从而不会对服务器产生影响）。
                 * 反常情况（例如在合理的时间后，服务端收到一个 TCP 关闭包）下，客户端应该开始关闭 TCP 连接。像这样的，
                 * 当服务端进入关闭 WebSocket 连接状态时，它应该立刻准备关闭 TCP 连接，然后当客户端客户端准备关闭连接时，
                 * 他应该等待服务端的 TCP 关闭包。
                 */
                //可省略关闭帧，也会自动关闭
                //1:解码
                if (tempPayloadData.length > 0) {
                    if (frame.mask() == 1) {
                        tempPayloadData = Utils.unmaskBytes(tempPayloadData, frame.maskingKey());
                    }
                    //2:前两个字节必须是一个无符号整型（按照网络字节序）来代表定义的状态码。
                    int statusCode = Utils.binary2Int(Utils.bytes2Binary(Arrays.copyOfRange(tempPayloadData, 0, 2)));
                    //3：跟在这两个整型字节之后的可以是UTF-8编码的的数据值（原因）
                    if (tempPayloadData.length > 8) {
                        byte[] bytes = Arrays.copyOfRange(tempPayloadData, 2, tempPayloadData.length);
                        msg = new String(bytes);
                    }
                    if (msg.isEmpty()) {
                        msg = getCloseCause(msg, statusCode);
                    }
                    //“负载字段”是用UTF-8编码的文本数据。
                    LOGGER.info("close websocket statusCode {} msg {} {}", statusCode, msg, uuid);
                } else {
                    LOGGER.info("close websocket empty statusCode msg  {}", uuid);
                }
                //1000表示正常关闭
                sendPayloadData = Utils.int2BinaryA2Byte(1000);
                sendPayloadLen = Utils.bytes2Binary((byte) 2);
                //这里len只有7位
                sendPayloadLen = Arrays.copyOfRange(sendPayloadLen, 1, sendPayloadLen.length);
                //响应关闭
                WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.CLOSE, sendPayloadData, sendPayloadLen, null, channelWrapped.channel(), channelWrapped.uuid());
                break;
            case 0x09:
                /**
                 * 如果收到了一个心跳Ping帧，那么终端必须发送一个心跳Pong 帧作为回应，除非已经收到了一个关闭帧。终端应该尽快回复Pong帧。
                 */
                WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PONG, null, null, null, channelWrapped.channel(), channelWrapped.uuid());
                break;
            case 0x10:
                /**
                 * 作为回应发送的Pong帧必须完整携带Ping帧中传递过来的“应用数据”字段。
                 * 如果终端收到一个Ping帧但是没有发送Pong帧来回应之前的ping帧，那么终端可能选择用Pong帧来回复最近处理的那个Ping帧。
                 * Pong帧可以被主动发送。这会作为一个单向的心跳。预期外的Pong包的响应没有规定。
                 */
                //不需要实现
                WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PING, null, null, null, channelWrapped.channel(), channelWrapped.uuid());
                break;
            default:
                break;
        }
        //读取结束则清除本次读取数据
        channelWrapped.cumulation().clearAll();
    }

    /**
     * 举例几个关闭的原因
     *
     * @param msg
     * @param statusCode
     * @return
     */
    private static String getCloseCause(String msg, int statusCode) {
        switch (statusCode) {
            case 1000:
                msg = "正常关闭";
                break;
            case 1001:
                msg = "服务器停机了或者在浏览器中离开了这个页面";
                break;
            case 1002:
                msg = "终端由于协议错误中止了连接。";
                break;
            case 1003:
                msg = "终端由于收到了一个不支持的数据类型的数据（如终端只能怪理解文本数据，但是收到了一个二进制数据）从而关闭连接。";
                break;
            case 1007:
                msg = "终端因为收到了类型不连续的消息（如非 UTF-8 编码的文本消息）导致的连接关闭。";
                break;
        }
        return msg;
    }

    private WebsocketFrame parse(ChannelWrapped channelWrapped) {
        byte[] frame = channelWrapped.cumulation().binaryString();
        String s = Utils.printBinary(frame);
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

}
