package org.example.protocol.websocket.client;

import org.example.entity.ChannelWrapped;
import org.example.entity.CompositeByteBuf;
import org.example.protocol.AbstractHandler;
import org.example.protocol.http.entity.HttpStatus;
import org.example.protocol.http.entity.*;
import org.example.util.Utils;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WsClientUpgradeHandler extends AbstractHandler {
    Request request;

    public WsClientUpgradeHandler(ChannelWrapped channelWrapped, Request request) {
        super(channelWrapped);
        this.request = request;
    }

    /**
     * 客户端必须通过以下的规则验证服务端的请求：
     * 1. 如果客户端收到的服务端返回状态码不是101，客户端需要处理每个HTTP请求的响应。特别的是，客户端需要在收到401状态码的时候可能需要进行验证；服务端可能会通过3xx的状态码来将客户端进行重定向（但是客户端不要求遵守这些）等。否则，遵循下面的步骤。
     * 2. 如果客户端收到的响应缺少一个`Upgrade`header字段或者`Upgrade`header字段包含一个不是"websocket"的值（该值不区分大小写），那么客户端必须关闭连接。
     * 3. 如果客户端收到的响应缺少一个`Connection`header字段或者`Connection`header字段不包含"Upgrade"的值（该值不区分大小写），那么客户端必须关闭连接。
     * 4. 如果客户端收到的`Sec-WebSocket-Accept`header字段或者`Sec-WebSocket-Accept`header字段不等于通过`Sec-WebSocket-Key`字段的值（作为一个字符串，而不是base64解码后）和"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"串联起来，忽略所有前后空格进行base64 SHA-1编码的值，那么客户端必须关闭连接。
     * 5. 如果客户端收到的响应包含一个`Sec-WebSocket-Extensions`header字段，并且这个字段使用的extension值在客户端的握手请求里面不存在（即服务端使用了一个客户端请求中不存在的值），那么客户端必须关闭连接。（解析这个header字段来确定使用哪个扩展在9.1节中有讨论。）
     * 6. 如果客户端收到的响应包含一个`Sec-WebSocket-Protocol`header字段，并且这个字段包含了一个没有在客户端握手中出现的子协议（即服务端使用了一个客户端请求中子协议字段不存在的值），那么客户端必须关闭连接。
     *
     * @throws Exception
     */
    @Override
    protected void exec() throws Exception {
        Response response = getResponse();
        String uuid = channelWrapped.uuid();
        if (Objects.isNull(request)) {
            return;
        }
        if (!response.getHttpStatus().equals(HttpStatus.UPGRADE)) {
            // 关闭客户端
            closeChildChannel();
            return;
        }
        if (!"websocket".equals(response.getUpgrade())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        if (!"Upgrade".equals(response.getConnection())) {
            //  关闭客户端
            closeChildChannel();
            return;
        }
        RequestHeaders requestHeaders = request.getRequestHeaders();
        if (!Utils.getKey(requestHeaders.getSecWebSocketKey()).equals(response.getSecWebSocketAccept())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        if (Objects.nonNull(response.getSecWebSocketExtensions())) {
            if (!requestHeaders.getSecWebSocketExtensions().contains(response.getSecWebSocketExtensions())) {
                //关闭客户端
                closeChildChannel();
                return;
            }
        }
        if (!response.getSecWebSocketProtocol().contains(requestHeaders.getSecWebSocketProtocol())) {
            //关闭客户端
            closeChildChannel();
            return;
        }
        Heartbeat heartbeat = new Heartbeat(channelWrapped);
        new ScheduledThreadPoolExecutor(1, r -> new Thread(r, "ping" + r.hashCode()))
                .scheduleAtFixedRate(heartbeat, 1, 5, TimeUnit.SECONDS);
        //协议升级
        WsSend websocketClientUpgrade = new WsSend(channelWrapped, request, heartbeat);
        channelWrapped.key().attach(websocketClientUpgrade);
        //清除读取的数据
        channelWrapped.cumulation().clear();
        LOGGER.info("websocket upgrade success {}", uuid);
    }

    private Response getResponse() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        if (cumulation.remaining() <= 0) {
            LOGGER.info("{} request empty", uuid);
            return null;
        }
        cumulation.mark();
        cumulation.print(uuid);
        //1:读取status line
        String readLine = cumulation.readLine();
        LOGGER.debug("statusLine {} {} ", readLine, uuid);

        //2:读取请求头
        //通常会在一个 POST 请求中带有一个 Content-Length 头字段
        Response response = null;
        try {
            response = Utils.parse(cumulation, Response.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOGGER.debug("headerFields {} {}", response, uuid);
        if (Objects.nonNull(response)) {
            //设置status line
            String[] readLineArr = readLine.split(" ");
            response.httpVersion(readLineArr[0]).httpStatus(HttpStatus.match(Integer.parseInt(readLineArr[1])));
        } else {
            cumulation.reset();
        }
        return response;
    }
}
