package org.example.protocol.dns.client;

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

/**
 * https://www.cnblogs.com/mggahui/p/13899888.html
 * https://blog.csdn.net/chen1415886044/article/details/121054801
 * https://juejin.cn/post/6844903582441963527
 * https://blog.csdn.net/qq_20677327/article/details/106994017
 * https://developer.aliyun.com/article/441173
 */
public class DnsHandler {
    protected static final Logger LOGGER = LogManager.getLogger(DnsHandler.class);
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
        List<String> cname = new ArrayList<>();
        new DnsHandler().exec("www.biying.com");
    }

    /**
     * @throws Exception
     */
    protected void exec(String cname) throws Exception {
        //域名被编码为一些labels序列，每个labels包含一个字节表示后续字符串长度，以及这个字符串，以0长度和空字符串来表示域名结束。
        byte[] name = getNameFrame(cname);
        byte[] type = Utils.int2BinaryA2Byte(1);
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
                //构建query
                .name(name)
                .type(type)
                ._class(Utils.int2BinaryA2Byte(1))
                //end query
                .write(channel, "x");
        DnsFrame dnsFrame = parse(write);
        if (dnsFrame == null) {
            return;
        }
        List<DnsFrame.Answer> answers = dnsFrame.answers();
        List<Address> addressList = new ArrayList<Address>();
        for (int i = 0; i < answers.size(); i++) {
            DnsFrame.Answer answer = answers.get(i);
            String address = parseAddress(answer.address(), answer.type(), dnsFrame.originFrame());
            if (answer.name()[0] != 0x01 && answer.name()[0] != answer.name()[1]) {
                LOGGER.warn("报文解析错误");
                continue;
            }
            String tempName = parseName(answer.name(), dnsFrame.originFrame());
            LOGGER.info("addressList  {} {}", address, tempName);
            addressList.add(Address.builder().address(address).name(tempName));

        }
        String queryName = parseName(dnsFrame.name(), dnsFrame.originFrame());
        LOGGER.info("queryName {} answers {} ", queryName, addressList);
    }

    private static byte[] getNameFrame(String host) {
        String[] split = host.split("\\.");
        //构建协议name的字节总长度
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
            //添加长度
            name[off++] = (byte) split[i].length();
            //bytes数据拷贝到协议name字节数组中
            off = copy(off, name, bytes);
        }
        //最后一位需要置0
        name[name.length - 1] = 0x00;
        byte[] bytes1 = Utils.bytes2Binary(name);
        return bytes1;
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
        int nameOffset = off / 8;
        byte[] queryName = Utils.int2BinaryA2Byte(nameOffset);
        //将len的第6~7位置1
        queryName[0] |= 1;
        queryName[1] |= 1;

        while (true) {
            byte[] lengthBinary = Arrays.copyOfRange(frame, off, off + 8);
            off += 8;
            int length = Utils.binary2Int(lengthBinary);
            if (length == 0) {
                break;
            }
            off += (length * 8);
        }

        byte[] queryType = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);
        byte[] queryClass = Arrays.copyOfRange(frame, off, off + 2 * 8);
        off += (2 * 8);

        //answers部分
        List<DnsFrame.Answer> answers = new ArrayList<>();
        while (off < frame.length) {
            //name offset
            // 为了减小报文，域名系统使用一种压缩方法来消除报文中域名的重复。
            //使用这种方法，后面重复出现的域名或者labels被替换为指向之前出现位置的指针。
            //前两个比特位都为1。因为lablels限制为不多于63个字节，所以label的前两位一定为0，这样就可以让指针与label进行区分。(10 和 01 组合保留，以便日后使用) 。
            //偏移值(OFFSET)表示从报文开始的字节指针。偏移量为0表示ID字段的第一个字节。
            byte[] answerName = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            byte[] answerType = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            byte[] answerClass = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            byte[] answerTtl = Arrays.copyOfRange(frame, off, off + 4 * 8);
            off += (4 * 8);
            byte[] answerDataLength = Arrays.copyOfRange(frame, off, off + 2 * 8);
            off += (2 * 8);
            int dataLength = Utils.binary2Int(answerDataLength);
            byte[] answersAddress = Arrays.copyOfRange(frame, off, off + dataLength * 8);
            off += (dataLength * 8);

            answers.add(DnsFrame.Answer.builder()
                    .name(answerName)
                    .type(answerType)
                    ._class(answerClass)
                    .ttl(answerTtl)
                    .data(answerDataLength)
                    .address(answersAddress));
        }
        /**
         * 如果解析出来的应答元素低于Answers，则说明报文有问题
         */
        if (answers.size() != Utils.binary2Int(answer)) {
            LOGGER.warn("报文解析错误");
            return null;
        }
        return DnsFrame.builder().txid(txId)
                .questions(questions)
                .answer(answer)
                .authority(authority)
                .additional(additional)
                .name(queryName)
                .type(queryType)
                ._class(queryClass)
                .answers(answers)
                .originFrame(frame);
    }

    private static String parseAddress(byte[] answersAddress, byte[] typeBinary, byte[] originFrame) {
        String addr = null;
        byte[] bytes = Utils.binary2Bytes(answersAddress);
        int type = Utils.binary2Int(typeBinary);
        StringBuilder sb = new StringBuilder();
        switch (type) {
            case 5://CNAME，主机别名
                int off = 0;
                while (off < bytes.length) {
                    int len = Utils.byteToIntV2(bytes[off]);
                    off++;
                    if (len == 0) {
                        break;
                    }
                    byte b = (byte) ((len >> 7) & 0x1);
                    byte b1 = (byte) ((len >> 6) & 0x1);
                    if (b == 1 && b1 == 1) {
                        //说明执行了压缩
                        //读取另外一个字节数
                        int offset = Utils.byteToIntV2(bytes[off]);
                        off++;
                        //将len的第6~7位置0
                        len &= ~(1 << 7);
                        len &= ~(1 << 6);
                        int index = len * 255 + offset;
                        sb.append(getCompressionString(originFrame, index * 8));
                        continue;
                    }
                    byte[] bytes1 = Arrays.copyOfRange(bytes, off, off + len);
                    off += len;
                    sb.append(new String(bytes1)).append(".");
                }
                addr = sb.toString().substring(0, sb.length() - 1);
                break;
            case 1://A，ip地址记录
                for (int i = 0; i < bytes.length; i++) {
                    sb.append(Utils.byteToIntV2(bytes[i])).append(".");
                }
                addr = sb.toString().substring(0, sb.length() - 1);
                break;
        }
        return addr;
    }

    /**
     * 获取
     *
     * @param name        位移。最高位第1-2位均为1，后续14位为下段长度。
     * @param originFrame
     * @return
     */
    private static String parseName(byte[] name, byte[] originFrame) {
        byte[] bytes1 = new byte[name.length - 2];
        System.arraycopy(name, 2, bytes1, 0, bytes1.length);
        int offset = Utils.binary2Int(bytes1);
        String compressionString = getCompressionString(originFrame, offset * 8);
        return compressionString.substring(0, compressionString.length() - 1);
    }

    /**
     * 获取压缩字符串
     *
     * @param originFrame 原数据，01010表示
     * @param off         binary 的位移，不是字节位移。
     * @return
     */
    private static String getCompressionString(byte[] originFrame, int off) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            byte[] lengthBinary = Arrays.copyOfRange(originFrame, off, off + 8);
            off += 8;
            int length = Utils.binary2Int(lengthBinary);
            if (length == 0) {
                break;
            }
            if (lengthBinary[0] == 0x01 && lengthBinary[0] == lengthBinary[1]) {
                byte[] lengthBinary2 = Arrays.copyOfRange(originFrame, off, off + 8);
                off += 8;
                byte[] merge = Utils.merge(lengthBinary, lengthBinary2);
                String s = parseName(merge, originFrame);
                sb.append(s).append(".");
                break;
            }
            byte[] data = Arrays.copyOfRange(originFrame, off, off + length * 8);
            off += (length * 8);
            sb.append(new String(Utils.binary2Bytes(data))).append(".");
        }
        return sb.toString();
    }

    public static class Address {
        String name;
        String address;

        public String name() {
            return name;
        }

        public Address name(String name) {
            this.name = name;
            return self();
        }

        public String address() {
            return address;
        }

        public Address address(String address) {
            this.address = address;
            return self();
        }

        private Address self() {
            return this;
        }

        public static Address builder() {
            return new Address();
        }

        @Override
        public String toString() {
            return "Address{" +
                    "name='" + name + '\'' +
                    ", address='" + address + '\'' +
                    '}';
        }
    }
}


