package org.example.protocol.websocket.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.entity.ChannelWrapped;
import org.example.protocol.http.entity.Request;
import org.example.protocol.http.entity.RequestHeaders;
import org.example.protocol.http.entity.StartLine;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Base64;
import java.util.Random;

public class WsClientHandler implements Runnable {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    private static final Random r = new Random();
    ChannelWrapped channelWrapped;

    public WsClientHandler(ChannelWrapped channelWrapped) {
        this.channelWrapped = channelWrapped;
    }


    /**
     * 握手的具体要求如下所示：
     * 1. 握手必须是一个在[RFC2616][2]指定的有效的HTTP请求。
     * 2. 这个请求方法必须是GET，而且HTTP的版本至少需要1.1。
     * 例如：如果WebSocket的URI是"ws://example.com/chat"，那么发送的请求头第一行就应该是"GET /chat HTTP/1.1"。
     * 3. 请求的"Request-URI"部分必须与第三章中定义的资源名称（resource name）匹配，或者必须是一个http/https绝对路径的URI，当解析URI时，有一个资源名称（resource name）、主机（host）和端口（port）与相对应的ws/wss匹配。
     * 4. 请求必须包含一个`Host`header字段，它包含了一个主机（host）字段加上一个紧跟在":"之后的端口（port）字段（如果端口不存在则使用默认端口）。
     * 5. 这个请求必须包含一个`Upgrade`header字段，它的值必须包含"websocket"。
     * 6. 请求必须包含一个`Connection`header字段，它的值必须包含"Upgrade"。
     * 7. 请求必须包含一个名为`Sec-WebSocket-Key`的header字段。这个header字段的值必须是由一个随机生成的16字节的随机数通过base64（见[RFC4648的第四章][3]）编码得到的。每一个连接都必须随机的选择随机数。
     * 注意：例如，如果随机选择的值的字节顺序为0x01 0x02 0x03 0x04 0x05 0x06 0x07 0x08 0x09 0x0a 0x0b 0x0c 0x0d 0x0e 0x0f 0x10，那么header字段的值就应该是"AQIDBAUGBwgJCgsMDQ4PEC=="。
     * 8. 如果这个请求来自一个浏览器，那么请求必须包含一个`Origin`header字段。如果请求是来自一个非浏览器客户端，那么当该客户端这个字段的语义能够与示例中的匹配时，这个请求也可能包含这个字段。这个header字段的值为建立连接的源代码的源地址ASCII序列化后的结果。通过[RFC6454][4]可以知道如何构造这个值。
     * 例如，如果在www.example.com域下面的代码尝试与ww2.example.com这个地址建立连接，那么这个header字段的值就应该是"http://www.example.com"。
     * 9. 这个请求必须包含一个名为`Sec-WebSocket-Version`的字段。这个header字段的值必须为13。
     * 注意：尽管这个文档草案的版本（09，10，11和12）都已经发布（这些协议大部分是编辑上的修改和澄清，而不是对无线协议的修改），9，10，11，12这四个值不被认为是有效的`Sec-WebSocket-Version`的值。这些值被IANA保留，但是没有被用到过，以后也不会被使用。
     * 10. 这个请求可能会包含一个名为`Sec-WebSocket-Protocol`的header字段。如果存在这个字段，那么这个值包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。这些子协议的值必须是一个非空字符串，字符的范围是U+0021到U+007E，但是不包含其中的定义在[RFC2616][5]中的分隔符，并且每个协议必须是一个唯一的字符串。ABNF的这个header字段的值是在[RFC2616][6]定义了构造方法和规则的1#token。
     * 11. 这个请求可能包含一个名为`Sec-WebSocket-Extensions`字段。如果存在这个字段，这个值表示客户端期望使用的协议级别的扩展。这个header字段的具体内容和格式具体见9.1节。
     * 12. 这个请求可能还会包含其他的文档中定义的header字段，如cookie（[RFC6265][7]）或者认证相关的header字段如`Authorization`字段（[RFC2616][8]）。
     * <p>
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
    public void run() {
        try {
            String uuid = channelWrapped.uuid();
            Request request = Request.builder()//构建状态行
                    .startLine(StartLine.builder().method("GET")
                            .requestTarget("/echo?name=%E5%93%88%E5%93%88&value=1111")
                            .httpVersion("HTTP/1.1"))
                    .requestHeaders(RequestHeaders.builder()
                            .host("127.0.0.1:8070")
                            .upgrade("websocket")//值必须包含"websocket"。
                            .connection("Upgrade")//值必须包含"Upgrade"。
                            .secWebSocketKey(getKey())//由一个随机生成的16字节的随机数通过base64编码得到的。每一个连接都必须随机的选择随机数。
                            .secWebSocketVersion(13) //值必须为13。
                            .secWebSocketProtocol("chat")//这个值包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。
                            .secWebSocketExtensions("permessage-deflate; client_max_window_bits")//这个值表示客户端期望使用的协议级别的扩展。
                    );
            request.write(channelWrapped.channel(), uuid);
            WsClientUpgradeHandler websocketClientUpgrade = new WsClientUpgradeHandler(channelWrapped, request);
            SelectionKey key = channelWrapped.key();
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            channelWrapped.key().attach(websocketClientUpgrade);
            LOGGER.info("websocket upgrade success {}", uuid);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getKey() {
        byte[] by = new byte[16];
        for (int i = 0; i < by.length; i++) {
            int i1 = r.nextInt(256);
            by[i] = (byte) i1;
        }
        return Base64.getEncoder().encodeToString(by);
    }

}
