package org.example.protocol.websocket.client;

import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.Request;
import org.example.protocol.websocket.entity.WebsocketFrame;
import org.example.util.Utils;
import java.util.Objects;

import static org.example.protocol.websocket.entity.WebsocketFrame.DEFAULT_MASK;

/**
 * 这里可以进行websocket通信了
 */
public class WsSend extends AbstractHandler {
    Request request;

    public WsSend(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
    }

    @Override
    protected void exec() throws Exception {
        String uuid = channelWrapped.uuid();
        WebsocketFrame frame = WebsocketFrame.parse(channelWrapped);
        //协议错误，断开连接
        if (Objects.isNull(frame)) {
            closeChildChannel();
            return;
        }
        LOGGER.info("Receive {} {} ", frame.toString(), uuid);
        String msg = "";
        byte[] tempPayloadData = frame.payloadData();
        switch (Utils.binary2Int(frame.opcode())) {
            case 0x00:
                break;
            case 0x01:
                //“负载字段”是用UTF-8编码的文本数据。
                if (tempPayloadData.length > 0) {
                    byte[] result = Utils.binary2Bytes(tempPayloadData);
                    msg = new String(result, "utf-8");
                    LOGGER.info("receive msg {} {} ", msg, uuid);
                }
                //WebsocketFrame.clientSendUTF( "接收成功", channelWrapped.channel(), channelWrapped.uuid());
                break;
            case 0x02:
                //二进制帧
                break;
            case 0x08:
                break;
            case 0x10:
                /**
                 * 作为回应发送的Pong帧必须完整携带Ping帧中传递过来的“应用数据”字段。
                 * 如果终端收到一个Ping帧但是没有发送Pong帧来回应之前的ping帧，那么终端可能选择用Pong帧来回复最近处理的那个Ping帧。
                 * Pong帧可以被主动发送。这会作为一个单向的心跳。预期外的Pong包的响应没有规定。
                 */
                WebsocketFrame.defaultFrame(WebsocketFrame.OpcodeEnum.PING, DEFAULT_MASK, null, null, null, null, channelWrapped.channel(), channelWrapped.uuid());
                break;
            default:
                break;
        }
        //读取结束则清除本次读取数据
        channelWrapped.cumulation().clearAll();
    }
}
