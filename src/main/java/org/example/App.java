package org.example;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hello world!
 */
public class App {
    private static class Attr {
        private static Attr attr = new Attr();
        Help.Status status;
        String uuid;
        String host;
        Integer port;

        private Attr self() {
            return this;
        }

        public Help.Status getStatus() {
            return status;
        }

        public Attr status(Help.Status status) {
            this.status = status;
            return self();
        }


        public Attr uuid(String uuid) {
            this.uuid = uuid;
            return self();
        }

        public String getUuid() {
            return uuid;
        }

        public Attr host(String host) {
            this.host = host;
            return self();
        }


        public Attr port(Integer port) {
            this.port = port;
            return self();
        }


        public Integer getPort() {
            return port;
        }

        public String getHost() {
            return host;
        }

    }

    private static Map<String, HttpURLConnection> map = new ConcurrentHashMap<String, HttpURLConnection>();

    public static void main(String[] args) throws IOException {
        extracted();
        //sendData("36.152.44.95", 80, "xx");
    }

    private static void extracted() throws IOException {
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(1080));
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        ByteBuffer buffer = ByteBuffer.allocate(2048);
        ByteBuffer writeBuffer = ByteBuffer.allocate(2048);
        ByteBuffer max = ByteBuffer.allocate(6000);
        while (true) {
            int n = selector.select();
            if (n == 0) {
                continue;
            }
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            try {
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        SocketChannel childChannel = serverChannel.accept();
                        childChannel.configureBlocking(false);
                        SelectionKey register = childChannel.register(selector, 0);
                        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
                        register.attach(new Attr().status(Help.Status.AUTH).uuid(uuid));
                        register.interestOps(SelectionKey.OP_READ);
                    } else if (key.isReadable()) {
                        SocketChannel childChannel = (SocketChannel) key.channel();
                        Attr attr = (Attr) key.attachment();
                        Help.Status status = attr.getStatus();
                        String VER;
                        String[] msg = null;
                        // TODO: 2023/5/23  处理粘包问题 
                        switch (status) {
                            case AUTH:
                                while (childChannel.read(buffer) > 0) {
                                    buffer.flip();
                                    msg = bytes2hexFirst(buffer);
                                }
                                if (null == msg || msg.length <= 0) {
                                    System.out.println("AUTH 数据包不够，关闭channel");
                                    childChannel.close();
                                }
                                try {
                                    VER = msg[0];
                                } catch (Exception e) {
                                    break;
                                }

                                if (!"05".equals(VER) || Integer.parseInt(VER) < 5) {
                                    System.out.println("版本号错误或版本过低，只能支持5");
                                }
                                if (msg.length < 3) {
                                    System.out.println("数据包不符合规定");
                                    childChannel.close();
                                }
                                String NMETHODS = msg[1];
                                //2~255
                                key.attach(attr.status(Help.Status.CONNECTION));
                                writeBuffer.put((byte) 5);
                                writeBuffer.put((byte) 0);
                                writeBuffer.flip();
                                childChannel.write(writeBuffer);
                                writeBuffer.clear();
                                break;
                            case CONNECTION:
                                while (childChannel.read(buffer) > 0) {
                                    buffer.flip();
                                    msg = bytes2hexFirst(buffer);
                                }
                                if (null == msg || msg.length <= 0) {
                                    System.out.println("AUTH 数据包不够，关闭channel");
                                    childChannel.close();
                                }
                                try {
                                    VER = msg[0];
                                } catch (Exception e) {
                                    break;
                                }
                                if (!"05".equals(VER) || Integer.parseInt(VER) < 5) {
                                    System.out.println("版本号错误或版本过低，只能支持5");
                                }
                                String CMD = msg[1];
                                if (!"01".equals(CMD)) {
                                    System.out.println("协议格式不对");
                                }
                                String RSV = msg[2];
                                String ATYP = msg[3];
                                String host = null;
                                Integer port = 0;
                                if ("01".equals(ATYP)) {//IPV4
                                    int h1 = Integer.parseInt(msg[4], 16);
                                    int h2 = Integer.parseInt(msg[5], 16);
                                    int h3 = Integer.parseInt(msg[6], 16);
                                    int h4 = Integer.parseInt(msg[7], 16);
                                    host = h1 + "." + h2 + "." + h3 + "." + h4;
                                    port = Integer.parseInt(msg[8] + msg[9], 16);
                                    System.out.println("IPV4 host: " + host + " port:" + port);
                                } else if ("03".equals(ATYP)) {//域名
                                    int hostnameSize = Integer.parseInt(msg[4], 16);
                                    String[] hostArr = Arrays.copyOfRange(msg, 4 + 1, hostnameSize + 4 + 1);
                                    host = hexToAscii(hostArr);
                                    //按照大端
                                    String[] portArr = Arrays.copyOfRange(msg, hostnameSize + 4 + 1, hostnameSize + 4 + 1 + 2);
                                    port = Integer.parseInt(portArr[0] + portArr[1], 16);
                                    System.out.println("域名访问 host: " + host + " port:" + port);

                                    if (host.contains("google")) {
                                        iterator.remove();
                                        continue;
                                    }
                                }
                                if ("04".equals(ATYP)) {//IPV6
                                    System.out.println("IPV6访问");
                                    continue;
                                }

                                writeBuffer.put((byte) 5);
                                writeBuffer.put((byte) 0);
                                writeBuffer.put((byte) 0);
                                writeBuffer.put((byte) Integer.parseInt(ATYP, 16));
                                //put host
                                writeBuffer.put(new byte[]{0, 0, 0, 0});
                                //put port
                                writeBuffer.put(new byte[]{0, 0});
                                writeBuffer.flip();
                                childChannel.write(writeBuffer);
                                writeBuffer.clear();
                                key.attach(attr.status(Help.Status.RECOVE).host(host).port(port));
                                break;
                            case RECOVE:
                                //获取服务端数据
                                if (!childChannel.isOpen()){
                                    System.out.println("channel 已经关闭");
                                    break;
                                }
                                int read = childChannel.read(max);

                                if (read < 0) {
                                    System.out.println("channel 关闭");
                                    childChannel.close();
                                } else {
                                    final String host1 = attr.getHost();
                                    final Integer port1 = attr.getPort();
                                    final SocketChannel channel = childChannel;
                                    final ByteBuffer buffer1 = buffer;
                                    //Thread thread = new Thread(new Runnable() {
                                    //    @Override
                                    //    public void run() {
                                            sendData(host1, port1, channel, max);
                                        //}
                                    //});
                                   /* thread.start();
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    if (thread.isAlive()){
                                        System.out.println("访问url严重超时 port："+port1+" host ：" +host1);
                                        childChannel.close();
                                        thread.interrupt();
                                    }*/
                                }
                                //getLen(buffer, childChannel, attr);
                                break;
                        }
                        buffer.clear();
                    }
                    iterator.remove();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private static void sendData(String host, Integer port, SocketChannel childChannel, ByteBuffer buffer) {
        try {
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(host, port));
            socketChannel.configureBlocking(false);
            while (!socketChannel.finishConnect()) {
            }
            Selector selector = Selector.open();
            socketChannel.register(selector, SelectionKey.OP_WRITE);
            long start = System.currentTimeMillis();
            c:
            while (true) {
                int n = selector.selectNow();
                /*long now = System.currentTimeMillis();
                if ((now - start) > 3000) {
                    break;
                }*/
                if (n == 0) {
                    continue;
                }
                if (n > 1) {
                    System.out.println("监听过多");
                }
                Set<SelectionKey> selectionKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectionKeys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey selectionKey = iterator.next();
                    SocketChannel channel = (SocketChannel) selectionKey.channel();
                    if (selectionKey.isWritable()) {
                        /*do {
                            buffer.flip();
                            channel.write(buffer);
                            buffer.flip();
                        } while (childChannel.read(buffer) > 0);*/
                        buffer.flip();
                        channel.write(buffer);
                        buffer.clear();
                        selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                    }
                    if (selectionKey.isReadable()) {
                        while (channel.read(buffer) > 0) {
                            buffer.flip();
                            childChannel.write(buffer);
                            buffer.flip();
                        }
                        buffer.clear();
                        iterator.remove();
                        break c;
                    }
                    iterator.remove();
                }
            }
            socketChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ByteBuffer buildString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
                .append("HTTP/1.1 200 OK").append("\r\n")
                .append("Cache-Control: no-cache").append("\r\n")
                .append("Content-Type: text/html;charset=utf-8").append("\r\n")
                .append("Connection: keep-alive").append("\r\n").append("\r\n")
                .append("<!DOCTYPE html><html></html>");
        return ByteBuffer.wrap(stringBuilder.toString().getBytes());
    }

    public static String[] bytes2hexFirst(ByteBuffer buffer) {
        String[] bytes = new String[buffer.remaining()];
        final String HEX = "0123456789abcdef";
        for (int i = 0; buffer.hasRemaining(); i++) {
            StringBuilder sb = new StringBuilder(2);
            byte b = buffer.get();
            // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt((b >> 4) & 0x0f));
            // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt(b & 0x0f));
            bytes[i] = sb.toString();
        }
        return bytes;
    }

    public static String hexToAscii(String[] host) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < host.length; i++) {
            String str = host[i];
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static String byteToAscii(byte[] host) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < host.length; i++) {
            output.append((char) host[i]);
        }
        return output.toString();
    }


    private static boolean getLen(ByteBuffer buffer, SocketChannel childChannel, Attr attr) throws IOException {
        sendData(attr.getHost(), attr.getPort(), attr.getUuid());
        HttpURLConnection connection = map.get(attr.getUuid());
        OutputStream outputStream = map.get(attr.getUuid()).getOutputStream();
        boolean flag = false;
        while (childChannel.read(buffer) > 0) {
            flag = true;
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            for (int i = 0; buffer.hasRemaining(); i++) {
                byte b = buffer.get();
                bytes[i] = b;
            }
            if (bytes[0] == 5) {
                break;
            }

            buffer.flip();
            outputStream.write(bytes);
            outputStream.flush();

            String str = byteToAscii(bytes);
            System.out.println("请求数据 " + str);
        }
        //调用 getResponseCode()方法，表示该请求已经结束了，若要再写入数据，需要发起一个新的请求
        if (flag) {
  /*          byte[] bytes = new byte[1024];
            InputStream inputStream = connection.getInputStream();
            while ((len = inputStream.read(bytes)) > 0) {
                ByteBuffer allocate = ByteBuffer.allocate(len);
                allocate.put(bytes, 0, len);
                childChannel.write(allocate);
            }
            inputStream.close();*/

            StringBuilder response = put(connection);
            try {
                BufferedReader inputStream = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), "utf-8"));
                String inputLine;
                while ((inputLine = inputStream.readLine()) != null) {
                    response.append(inputLine);
                }
                System.out.println(response);
                inputStream.close();

                byte[] bytes1 = response.toString().getBytes();
                ByteBuffer wrap = ByteBuffer.wrap(bytes1);
                childChannel.write(wrap);
            } catch (Exception exception) {
            }

        }
        outputStream.close();
        connection.disconnect();
        return flag;
    }

    private static StringBuilder put(HttpURLConnection connection) {
        StringBuilder builder = new StringBuilder();
        Map<String, List<String>> map2 = connection.getHeaderFields();
        for (Map.Entry<String, List<String>> entry : map2.entrySet()) {
            if (entry.getKey() == null) {
                builder = new StringBuilder(entry.getValue().get(0)).append("\r\n").append(builder);
                continue;
            }

            builder.append(entry.getKey())
                    .append(": ");
            List headerValues = entry.getValue();
            Iterator it = headerValues.iterator();
            if (it.hasNext()) {
                builder.append(it.next());
                while (it.hasNext()) {
                    builder.append(", ")
                            .append(it.next());
                }
            }
            builder.append("\r\n");
        }
        builder.append("\r\n");
        return builder;
    }

    private static void sendData(String host, Integer port, String uuid) {
        String url = host + ":" + port;
        if (port == 80) {
            url = "http://" + url;
        } else if (port == 443) {
            url = "https://" + url;
        }
        System.out.println("sendData " + url);
        URL obj = null;
        try {
            obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            //默认值我GET
            //con.setRequestMethod("GET");
            //添加请求头
            //con.setRequestProperty("User-Agent", "XX");
            //设置连接超时时间
            con.setReadTimeout(3000);
            con.setDoOutput(true);
            con.setDoInput(true);
            map.put(uuid, con);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
