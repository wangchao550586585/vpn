package org.example.protocol.dns;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.protocol.dns.entity.DnsFrame;
import org.example.util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.example.util.Utils.copy;

public class DnsHandler {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
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
            off = copy(off, name, bytes);
        }
        //最后一位需要置0
        name[name.length - 1] = 0x00;
        byte[] bytes1 = Utils.bytes2Binary(name);
        //改成9字节
        //二进制，16位0
        byte[] bytes = Utils.int2BinaryA2Byte(0);
        ByteBuffer write = DnsFrame.builder()
                .txid(Utils.int2BinaryA2Byte(58))
                .response((byte) 0x00)
                .opcode(new byte[]{0x00, 0x00, 0x00, 0x00})
                .authoritativeAnswer((byte) 0x00)//不需要提供
                .truncated((byte) 0x00)
                .recursionDesired((byte) 0x01)
                .recursionAvailable((byte) 0x00)//不需要提供
                .z(new byte[]{0x00, 0x00, 0x00})//保留，必须设置0
                .rcode(new byte[]{0x00, 0x00, 0x00, 0x00})//应答报文中设置
                .questions(Utils.int2BinaryA2Byte(1))//2字节
                .answer(bytes)//2字节,回答资源记录数
                .authority(bytes)//2字节,权威名称服务器计数
                .additional(bytes)//2字节,附加资源记录数
                .name(bytes1)
                .type(Utils.int2BinaryA2Byte(1))
                ._class(Utils.int2BinaryA2Byte(1))
                .write(channel, "x");
        DnsFrame dnsFrame = parse(write);
    }

    private DnsFrame parse(ByteBuffer byteBuffer) {
        byte[] frame = Utils.bytes2Binary(byteBuffer);
        int off = 0;
        byte[] txId = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] flags = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] questions = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] answer = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] authority = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] additional = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);

        //queries
        StringBuilder name = new StringBuilder();
        for (int i = 0; ; i++) {
            byte[] lengthBinary = Arrays.copyOfRange(frame, off, off + 8);
            off += 8;
            int length = Utils.binary2Int(lengthBinary);
            if (length == 0) {
                break;
            }
            byte[] data = Arrays.copyOfRange(frame, off, off + length * 8);
            off += (length * 8);
            name.append(new String(Utils.binary2Bytes(data))).append(".");
        }

        LOGGER.info("queries name {} ", name.toString().substring(0,name.length()-1));
        byte[] type = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] _class = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);

        //question部分
        List<String> addres = new ArrayList<>();
        while (off < frame.length) {
            //name offset
            // 为了减小报文，域名系统使用一种压缩方法来消除报文中域名的重复。
            //使用这种方法，后面重复出现的域名或者labels被替换为指向之前出现位置的指针。
            //前两个比特位都为1。因为lablels限制为不多于63个字节，所以label的前两位一定为0，这样就可以让指针与label进行区分。(10 和 01 组合保留，以便日后使用) 。
            //偏移值(OFFSET)表示从报文开始的字节指针。偏移量为0表示ID字段的第一个字节。
            byte[] answersName = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            if (answersName[0] != 0x01 && answersName[0] != answersName[1]) {
                LOGGER.info("报文解析错误");
            }
            byte[] bytes1 = new byte[answersName.length - 2];
            System.arraycopy(answersName, 2, bytes1, 0, bytes1.length);
            int offset = Utils.binary2Int(bytes1);
            LOGGER.info("offset {} ",offset);

            byte[] answersType = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            byte[] answersClass = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            byte[] ttl = Arrays.copyOfRange(frame, off, off + 4 * 8);
            off += (4 * 8);
            byte[] dataLengthBinary = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            int dataLength = Utils.binary2Int(dataLengthBinary);
            byte[] address = Arrays.copyOfRange(frame, off, off + dataLength * 8);
            off += (dataLength * 8);

            StringBuilder sb = new StringBuilder();
            byte[] bytes = Utils.binary2Bytes(address);
            for (int i = 0; i < bytes.length; i++) {
                sb.append(Utils.byteToIntV2(bytes[i])).append(".");
            }

            addres.add(sb.toString().substring(0,name.length()-1));
        }
        LOGGER.info("addres  {} ", addres);
        return null;
    }
}


