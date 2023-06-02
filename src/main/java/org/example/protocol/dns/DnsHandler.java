package org.example.protocol.dns;

import org.example.protocol.dns.entity.DnsFrame;
import org.example.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.example.util.Utils.copy;

public class DnsHandler {
    private static DatagramChannel channel;

    static {
        try {
            channel = DatagramChannel.open();
            channel.connect(new InetSocketAddress("192.168.1.1", 53));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws Exception {
        new DnsHandler().exec("baidu.com");
    }

    /**
     * @throws Exception
     */
    protected void exec(String host) throws Exception {
        //域名被编码为一些labels序列，每个labels包含一个字节表示后续字符串长度，以及这个字符串，以0长度和空字符串来表示域名结束。
        String[] split = host.split("\\.");
        int length = 0;
        for (int i = 0; i < split.length; i++) {
            //存储字节
            length++;
            length += split[i].length();
        }
        byte[] name = new byte[length + 1];
        int off = 0;
        for (int i = 0; i < split.length; i++) {
            byte[] bytes = split[i].getBytes();
            name[off++] = (byte) split[i].length();
            copy(off, name, bytes);
            off += split[i].length();
        }
        //最后一位需要置0
        name[name.length - 1] = 0x00;
        byte[] bytes1 = Utils.bytes2Binary(name);
        //改成9字节
        //二进制，16位0
        byte[] bytes = Utils.int2BinaryA2Byte(0);
        ByteBuffer write = DnsFrame.builder()
                .setTxId(Utils.int2BinaryA2Byte(58))
                .setResponse((byte) 0x00)
                .setOpcode(new byte[]{0x00, 0x00, 0x00, 0x00})
                .setAuthoritativeAnswer((byte) 0x00)//不需要提供
                .setTruncated((byte) 0x00)
                .setRecursionDesired((byte) 0x01)
                .setRecursionAvailable((byte) 0x00)//不需要提供
                .setZ(new byte[]{0x00, 0x00, 0x00})//保留，必须设置0
                .setRcode(new byte[]{0x00, 0x00, 0x00, 0x00})//应答报文中设置
                .setQuestions(Utils.int2BinaryA2Byte(1))//2字节
                .setAnswer(bytes)//2字节,回答资源记录数
                .setAuthority(bytes)//2字节,权威名称服务器计数
                .setAdditional(bytes)//2字节,附加资源记录数
                .setName(bytes1)
                .setType(Utils.int2BinaryA2Byte(1))
                .set_class(Utils.int2BinaryA2Byte(1))
                .write(channel, "x");

    }
}


