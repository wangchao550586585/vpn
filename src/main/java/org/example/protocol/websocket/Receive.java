package org.example.protocol.websocket;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.Request;
import org.example.protocol.websocket.entity.Frame;
import org.example.util.Utils;

import java.util.Arrays;

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
        byte[] frame = channelWrapped.cumulation().binaryString();
        String frameString = Utils.printBinary(frame);
        LOGGER.info("frame {} {} ", frameString, uuid);

        //表示这是消息的最后一个片段。第一个片段也有可能是最后一个片段。
        int off = 0;
        byte fin = frame[off];
        //必须设置为0，除非扩展了非0值含义的扩展。如果收到了一个非0值但是没有扩展任何非0值的含义，接收终端必须断开WebSocket连接。
        off++;
        byte[] rsv = Arrays.copyOfRange(frame, off, off + 3);
        off += 3;
        for (int i = 0; i < rsv.length; i++) {
            if (rsv[i] != 0x00) {
                //协议错误，断开连接
                return;
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
        int code = Utils.binary2Int(opcode);

        off += 4;
        //定义“有效负载数据”是否添加掩码。默认1，掩码的键值存在于Masking-Key中
        byte mask = frame[off];
        off++;
        byte[] payload = Arrays.copyOfRange(frame, off, off + 7);
        byte[] extendedPayloadLength;
        off += 7;
        int payloadLen = Utils.binary2Int(payload);
        if (payloadLen <= 125) {
            //如果值为0-125，那么就表示负载数据的长度。
        } else if (payloadLen == 126) {
            // TODO: 2023/6/1
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            extendedPayloadLength = Arrays.copyOfRange(frame, off, (off + 2 * 8));
            off += 2 * 8;
            payloadLen = Utils.binary2Int(extendedPayloadLength);
        } else {
            // TODO: 2023/6/1
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            extendedPayloadLength = Arrays.copyOfRange(frame, off, (off + 8 * 8));
            off += 8 * 8;
            payloadLen = Utils.binary2Int(extendedPayloadLength);
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
        switch (code) {
            case 0x00:
                break;
            case 0x01:
                //“负载字段”是用UTF-8编码的文本数据。
                String msg;
                if (mask == 1) {
                    msg = Utils.unmask(payloadData, maskingKey);
                } else {
                    msg = new String(payloadData, "utf-8");
                }
                LOGGER.info("receive msg {} {} ", msg, uuid);
                break;
            case 0x02:
                //二进制帧
                break;
            case 0x08:
                /**
                 * 关闭帧可能包含内容（body）（帧的“应用数据”部分）来表明连接关闭的原因，例如终端的断开，
                 * 或者是终端收到了一个太大的帧，或者是终端收到了一个不符合预期的格式的内容。
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
                 */

                break;
            case 0x09:
                /**
                 * 如果收到了一个心跳Ping帧，那么终端必须发送一个心跳Pong 帧作为回应，除非已经收到了一个关闭帧。终端应该尽快回复Pong帧。
                 */
                break;
            case 0x10:
                /**
                 * 作为回应发送的Pong帧必须完整携带Ping帧中传递过来的“应用数据”字段。
                 * 如果终端收到一个Ping帧但是没有发送Pong帧来回应之前的ping帧，那么终端可能选择用Pong帧来回复最近处理的那个Ping帧。
                 * Pong帧可以被主动发送。这会作为一个单向的心跳。预期外的Pong包的响应没有规定。
                 */
                break;
            default:
                break;
        }

        //响应数据，掩码
        byte[] payloadData2 = "接收成功".getBytes();
        //构建长度
        byte[] payloadLen2 = null;
        if (payloadData2.length < 126) {
            payloadLen2 = Utils.bytes2Binary((byte) payloadData2.length);
            //这里len只有7位
            payloadLen2 = Arrays.copyOfRange(payloadLen2, 1, payloadLen2.length);
        } else if (payloadData2.length < 127) {
            //如果是126，那么接下来的2个bytes解释为16bit的无符号整形作为负载数据的长度。
            //字节长度量以网络字节顺序表示
            // TODO: 2023/6/1
        } else {
            //如果是127，那么接下来的8个bytes解释为一个64bit的无符号整形（最高位的bit必须为0）作为负载数据的长度。
            // TODO: 2023/6/1
        }
        payloadData2 = Utils.bytes2Binary(payloadData2);
        byte[] payloadLenExtended = null;
        //服务端不需要发送掩码
        mask = 0x00;
        Frame.builder()//构建状态行
                .fin(fin)
                .rsv(rsv)
                .opcode(opcode)  //构建响应头
                .mask(mask)
                .payloadLen(payloadLen2)
                .payloadLenExtended(payloadLenExtended)
                .maskingKey(null)
                .payloadData(payloadData2)//构建响应体
                .write(channelWrapped.channel(), channelWrapped.uuid());
        //最后一个包含数据的帧的 FIN （ FIN 帧）字段必须设置为 1 。
        //如果数据被发送到了客户端，数据帧必须和第 5.3 节中定义的一样添加掩码。
        //读取结束则清除本次读取数据
        channelWrapped.cumulation().clearAll();
    }

}
