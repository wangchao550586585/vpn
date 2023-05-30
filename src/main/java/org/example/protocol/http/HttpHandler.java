package org.example.protocol.http;

import org.example.CompositeByteBuf;
import org.example.entity.ChannelWrapped;
import org.example.protocol.AbstractHandler;

import java.io.IOException;
import java.util.*;

/**
 * curl  http://127.0.0.1/index.jsp?name=%E5%BC%A0%E4%B8%89&value=10
 * POST请求
 * curl -H "Content-Type: application/json" -X POST -d '{"id": "001", "name":"张三", "phone":"13099999999"}'  http://127.0.0.1/index.jsp?name=%E5%BC%A0%E4%B8%89&value=10
 *
 * 请求行 request-line，开始于一个方法标识 method，
 * 紧接着一个空白 SP，
 * 然后是请求目标 request-target，
 * 另一个空白 SP，
 * 之后是协议版本 HTTP-version，
 * 最后是回车换行符 CRLF。
 * 服务器在等待接收和解析parse一个请求行 request-line 的时候，应当 忽略至少一个空行
 * 例子如下：GET /index.jsp?name=%E5%BC%A0%E4%B8%89&value=10 HTTP/1.1
 *
 * status-line = HTTP-version SP status-code SP reason-phrase CRLF
 * 协议版本 HTTP-version，一个空白 SP，状态码 status-code，另一个空白 SP，一个可能为空的文本短语 reason-phrase 来描述该状态码，最后是回车换行符 CRLF。
 * reason-phrase 早期互联网交互，可省略。
 * 例子如下：HTTP/1.1 200 OK
 *
 * 每一个头字段header field都由一个字段名field name
 * 及随后的一个分号（":"）、
 * 可选的前置空白、
 * 一个字段值field value、
 * 一个可选的结尾空白组成。
 * 例子如下：Connection: keep-alive
 */
public class HttpHandler extends AbstractHandler {
    public HttpHandler(ChannelWrapped channelWrapped) {
        super(channelWrapped);
    }

    @Override
    protected void exec() throws IOException {
        Request request = getRequest();
        LOGGER.info("IPV4 host:{} remoteAddress:{} {}", request.getRequestHeaders().getHost(), channelWrapped.channel().getRemoteAddress(), channelWrapped.uuid());
        //接收端接收到一个不合法的请求行request-line时，应当 响应一个 400 (Bad Request) 错误或者 301 (Move Permanently) 重定向，
        //服务器接收到超出其长度要求的请求方法request method时 应当 响应一个 501 (Not Implemented) 状态码。服务器接收到一个 URI 其长度超出服务器所期望的最大长度时，必须 响应一个 414 (URI Too Long) 状态码
        String method = request.getStartLine().getMethod();
        if (("GET").equals(method)) {
            //解析域名后面的字符串。
            doGet(request);
        } else if (method.equals("POST")) {
            doPost(request);
        }
    }

    private Request getRequest() {
        String uuid = channelWrapped.uuid();
        CompositeByteBuf cumulation = channelWrapped.cumulation();
        cumulation.print();
        //1:读取start line
        StartLine startLine = StartLine.parse(cumulation.readLine());
        LOGGER.info("startLine {} {} ", startLine, uuid);

        //2:读取请求头
        //通常会在一个 POST 请求中带有一个 Content-Length 头字段
        RequestHeaders requestLine = RequestHeaders.parse(cumulation);
        LOGGER.info("headerFields {} {}", requestLine, uuid);

        //3:获取请求体
        //在一个请求中是否会出现消息体，以消息头中是否带有 Content-Length 或者 Transfer-Encoding 头字段作为信号。
        // 请求消息的分帧是独立于请求方法request method的语义之外的，即使请求方法并没有任何用于一个消息体的相关定义。
        //如果发送端所生成的消息包含有一个有效载荷，那么发送端 应当 在该消息里生成一个 Content-Type 头字段
        String payload = null;
        Integer contentLength = requestLine.getContentLength();
        String contentType = requestLine.getContentType();
        if (Objects.isNull(contentLength) || Objects.isNull(contentType)) {
        } else {
            //3:读取请求体
            payload = cumulation.read(contentLength);
            LOGGER.info("payloads {} {}", payload, uuid);
        }

        //4:获取路径上的参数
        //GET /index.jsp?name=%E5%BC%A0%E4%B8%89&value=10 HTTP/1.1
        Map<String, String> paramsMap = null;
        String requestTarget = startLine.getRequestTarget();
        int pre = requestTarget.indexOf("?");
        if (pre > 0) {
            paramsMap = new HashMap<>();
            String params = requestTarget.substring(pre + 1, requestTarget.length());
            String[] split = params.split("&");
            for (int i = 0; i < split.length; i++) {
                String[] keyVal = split[i].split("=");
                paramsMap.put(keyVal[0], keyVal[1]);
            }
            LOGGER.info("params {} {}", paramsMap, uuid);
        }

        return new Request(startLine, requestLine, payload, paramsMap);
    }

    private void doPost(Request request) throws IOException {
        if (request.getRequestHeaders().getContentType().contains("application/json")) {
            //解析json
            String requestBody = request.getRequestBody();
            Response.builder()//构建状态行
                    .httpVersion(request.getStartLine().getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .date()  //构建响应头
                    .connection(request.getRequestHeaders().getConnection())
                    .contentLanguage("zh-CN")
                    .contentType(request.getRequestHeaders().getContentType())
                    .contentLength(requestBody.getBytes().length)
                    .payload(requestBody)//构建响应体
                    .write(channelWrapped.channel(), channelWrapped.uuid());
        }
    }

    private void doGet(Request request) throws IOException {
        //获取响应体
        String payload = buildPayload();
        Response.builder()//构建状态行
                .httpVersion(request.getStartLine().getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .date()//构建响应头
                .connection(request.getRequestHeaders().getConnection())
                .contentLanguage("zh-CN")
                .contentType("text/html;charset=utf-8")
                .contentLength(payload.getBytes().length)
                .payload(payload)//构建响应体
                .write(channelWrapped.channel(), channelWrapped.uuid());
    }

    private String buildPayload() {
        StringBuilder payload = new StringBuilder("");
        payload.append("<!DOCTYPE html>");
        payload.append("<html>");
        payload.append("<head>");
        payload.append("<body>");
        payload.append("hello");
        payload.append("</body>");
        payload.append("</head>");
        payload.append("</html>");
        return payload.toString();
    }

}
