package org.example.protocol.websocket;

import org.example.entity.ChannelWrapped;
import org.example.protocol.http.HttpHandler;
import org.example.protocol.http.HttpStatus;
import org.example.protocol.http.entity.Request;
import org.example.protocol.http.entity.Response;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * 替代HTTP轮询的方法来满足Web页面和远端服务器的双向数据通信。
 * ws-URI = "ws:" "//" host [ ":" port ] path [ "?" query ]
 * wss-URI = "wss:" "//" host [ ":" port ] path [ "?" query ]
 *
 * https://github.com/HJava/myBlog/blob/master/WebSocket%20%E7%B3%BB%E5%88%97/WebSocket%E7%B3%BB%E5%88%97%E4%B9%8B%E4%BA%8C%E8%BF%9B%E5%88%B6%E6%95%B0%E6%8D%AE%E8%AE%BE%E8%AE%A1%E4%B8%8E%E4%BC%A0%E8%BE%93.md
 */
public class WebsocketHandler extends HttpHandler {
    private static final byte[] MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes();

    public WebsocketHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
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
     *
     * 客户端必须通过以下的规则验证服务端的请求：
     * 1. 如果客户端收到的服务端返回状态码不是101，客户端需要处理每个HTTP请求的响应。特别的是，客户端需要在收到401状态码的时候可能需要进行验证；服务端可能会通过3xx的状态码来将客户端进行重定向（但是客户端不要求遵守这些）等。否则，遵循下面的步骤。
     * 2. 如果客户端收到的响应缺少一个`Upgrade`header字段或者`Upgrade`header字段包含一个不是"websocket"的值（该值不区分大小写），那么客户端必须关闭连接。
     * 3. 如果客户端收到的响应缺少一个`Connection`header字段或者`Connection`header字段不包含"Upgrade"的值（该值不区分大小写），那么客户端必须关闭连接。
     * 4. 如果客户端收到的`Sec-WebSocket-Accept`header字段或者`Sec-WebSocket-Accept`header字段不等于通过`Sec-WebSocket-Key`字段的值（作为一个字符串，而不是base64解码后）和"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"串联起来，忽略所有前后空格进行base64 SHA-1编码的值，那么客户端必须关闭连接。
     * 5. 如果客户端收到的响应包含一个`Sec-WebSocket-Extensions`header字段，并且这个字段使用的extension值在客户端的握手请求里面不存在（即服务端使用了一个客户端请求中不存在的值），那么客户端必须关闭连接。（解析这个header字段来确定使用哪个扩展在9.1节中有讨论。）
     * 6. 如果客户端收到的响应包含一个`Sec-WebSocket-Protocol`header字段，并且这个字段包含了一个没有在客户端握手中出现的子协议（即服务端使用了一个客户端请求中子协议字段不存在的值），那么客户端必须关闭连接。
     *
     * @param request
     * @throws IOException
     */
    @Override
    protected void doGet(Request request) throws Exception {
        String uuid = channelWrapped.uuid();
        String httpVersion = request.getStartLine().getHttpVersion();
        float v = Float.parseFloat(httpVersion.split("/")[1]);
        if (v < 1.1) {
            //不支持
        }
        String host = request.getRequestHeaders().getHost();
        if (Objects.isNull(host)) {
            //不支持
        }
        //表示建立连接的脚本属于哪一个源
        String origin = request.getRequestHeaders().getOrigin();
        if (Objects.isNull(origin)) {
            //缺少origin字段，WebSocket服务器需要回复HTTP 403 状态码（禁止访问）
        }
        String upgrade = request.getRequestHeaders().getUpgrade();
        if (Objects.equals("websocket", upgrade)) {
            //不支持
        }
        String connection = request.getRequestHeaders().getConnection();
        if (Objects.equals("Upgrade", connection)) {
            //不支持
        }
        String secWebSocketKey = request.getRequestHeaders().getSecWebSocketKey();
        if (Objects.isNull(secWebSocketKey)) {
            //不支持
        }

        Integer secWebSocketVersion = request.getRequestHeaders().getSecWebSocketVersion();
        if (secWebSocketVersion != 13) {
            //不支持
        }

        String secWebSocketExtensions = request.getRequestHeaders().getSecWebSocketExtensions();
        String secWebSocketProtocol = request.getRequestHeaders().getSecWebSocketProtocol();
        Response.builder()//构建状态行
                .httpVersion(request.getStartLine().getHttpVersion())
                //状态码101表示同意升级
                .httpStatus(HttpStatus.UPGRADE)
                .date()//构建响应头
                //不包含"Upgrade"的值（该值不区分大小写），那么客户端必须关闭连接。
                .connection(connection)
                //不是"websocket，那么客户端必须关闭连接。
                .upgrade(upgrade)
                .secWebSocketExtensions(secWebSocketExtensions)
                .secWebSocketProtocol(secWebSocketProtocol)
                .secWebSocketAccept(getKey(secWebSocketKey))
                .contentLanguage("zh-CN")
                .write(channelWrapped.channel(), channelWrapped.uuid());
        Receive deliverHandler = new Receive(channelWrapped,request);
        channelWrapped.key().attach(deliverHandler);

        LOGGER.info("websocket upgrade success {}",uuid);
    }

    /**
     * 请求必须包含一个名为`Sec-WebSocket-Key`的header字段。这个header字段的值必须是由一个随机生成的16字节的随机数通过base64
     * （见[RFC4648的第四章][3]）编码得到的。每一个连接都必须随机的选择随机数。
     * 注意：例如，如果随机选择的值的字节顺序为0x01 0x02 0x03 0x04 0x05 0x06 0x07 0x08 0x09 0x0a 0x0b 0x0c 0x0d 0x0e 0x0f 0x10，
     * 那么header字段的值就应该是"AQIDBAUGBwgJCgsMDQ4PEC=="。
     * <p>
     * 如果客户端收到的`Sec-WebSocket-Accept`header字段或者`Sec-WebSocket-Accept`header字段不等于通过`Sec-WebSocket-Key`字段的值
     * （作为一个字符串，而不是base64解码后）和"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"串联起来，
     * 忽略所有前后空格进行SHA-1编码然后base64值，那么客户端必须关闭连接。
     *
     * @param s
     * @return
     * @throws NoSuchAlgorithmException
     */
    private static String getKey(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        md.update(s.getBytes());
        md.update(MAGIC);
        return Base64.getEncoder().encodeToString(md.digest());
    }

    /**
     * 不支持
     *
     * @param request
     * @throws IOException
     */
    @Override
    protected void doPost(Request request) throws IOException {
        //dont accept
        super.doPost(request);
    }

    /**
     * 不支持
     *
     * @param request
     */
    @Override
    protected void otherMethod(Request request) {
        //dont accept
        super.otherMethod(request);
    }
}
