package org.example.protocol.dns.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

import static org.example.util.Utils.copy;

public class DnsFrame {
    //事务id，请求报文和响应报文一样，用于区分应答报文对应那个响应
    private byte[] txId;
    /**
     * 0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                      ID                       |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                    QDCOUNT                    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                    ANCOUNT                    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                    NSCOUNT                    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                    ARCOUNT                    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     */
    //flag
    //查询请求/响应的标志信息。查询请求时，值为 0；响应时，值为 1。
    private byte response;
    //操作码。其中，0 表示标准查询；1 表示反向查询；2 表示服务器状态请求。
    private byte[] opcode;
    //授权应答(Authoritative Answer) - 这个比特位在应答的时候才有意义，指出给出应答的服务器是查询域名的授权解析服务器。
    private byte authoritativeAnswer;//请求方不需要写
    //表示是否被截断。值为 1 时，表示响应已超过 512 字节并已被截断，只返回前 512 个字节。
    private byte truncated;
    //期望递归。该字段能在一个查询中设置，并在响应中返回。该标志告诉名称服务器必须处理这个查询，这种方式被称为一个递归查询。
    //如果该位为 0，且被请求的名称服务器没有一个授权回答，它将返回一个能解答该查询的其他名称服务器列表。这种方式被称为迭代查询。
    private byte recursionDesired;
    //RA       支持递归(Recursion Available) - 这个比特位在应答中设置或取消，用来代表服务器是否支持递归查询。
    private byte recursionAvailable;//请求方不需要写
    private byte[] z;
    /**
     * 应答码(Response code) - 这4个比特位在应答报文中设置，代表的含义如下：
     * ≈ 0    没有错误。
     * ≈ 1    报文格式错误(Format error) - 服务器不能理解请求的报文。
     * ≈ 2    服务器失败(Server failure) - 因为服务器的原因导致没办法处理这个请求。
     * ≈ 3    名字错误(Name Error) - 只有对授权域名解析服务器有意义，指出解析的域名不存在。
     * ≈ 4    没有实现(Not Implemented) - 域名服务器不支持查询类型。
     * ≈ 5    拒绝(Refused) - 服务器由于设置的策略拒绝给出应答。比如，服务器不希望对某些请求者给出应答，或者服务器不希望进行某些操作（比如区域传送zone transfer）。
     * ≈ 6-15 保留值，暂时未使用。
     */
    private byte[] rcode;//请求方不需要写
    //flag end
    //DNS 查询请求的数目。
    private byte[] questions;
    //DNS 响应的数目。
    private byte[] answer;
    //权威名称服务器的数目。
    private byte[] authority;
    //额外的记录数目（权威名称服务器对应 IP 地址的数目）。
    private byte[] additional;
    //queries
    /**
     * 0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                                               |
     * /                     QNAME                     /
     * /                                               /
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                     QTYPE                     |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     * |                     QCLASS                    |
     * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     */
    //一般为要查询的域名，有时也会是 IP 地址，用于反向查询。
    private byte[] name;
    //DNS 查询请求的资源类型。通常查询类型为 A 类型，表示由域名获取对应的 IP4 地址。（更多类型如 AAAA，CANME，SOA，PTR，NS 等）
    private byte[] type;
    //地址类型，通常为互联网地址，值为 1。
    private byte[] _class;
    //“该报文为标准查询(Opcode=0)请求(response=1)报文，向本地域名服务器( IP 报文中目的地址为本地域名服务器地址，在上面准备工作中已经知道了)请求查询，
    // 发起请求内容为 ‘获取www.biying.com (Name=www.baidu.com)所对应的IP4地址(Type=A)’，期待本地域名服务器递归查询(Recursion Desired=1)请求”

    //answers
    List<Answer> answers = new ArrayList<Answer>();

    public static class Answer {
        private byte[] name;
        private byte[] type;
        private byte[] _class;
        private byte[] ttl;
        private byte[] data;
        private byte[] address;//cname

        public Answer name(byte[] name) {
            this.name = name;
            return self();
        }

        public Answer type(byte[] type) {
            this.type = type;
            return self();
        }

        public Answer _class(byte[] _class) {
            this._class = _class;
            return self();
        }

        public Answer ttl(byte[] ttl) {
            this.ttl = ttl;
            return self();
        }

        public Answer data(byte[] data) {
            this.data = data;
            return self();
        }

        public Answer address(byte[] address) {
            this.address = address;
            return self();
        }

        public byte[] name() {
            return name;
        }

        public byte[] type() {
            return type;
        }

        public byte[] _class() {
            return _class;
        }

        public byte[] ttl() {
            return ttl;
        }

        public byte[] data() {
            return data;
        }

        public byte[] address() {
            return address;
        }

        private Answer self() {
            return this;
        }

        public static Answer builder() {
            return new Answer();
        }

    }

    /**
     * 协议原生数据
     */
    private byte[] originFrame;

    private int length;
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());

    public static DnsFrame builder() {
        return new DnsFrame();
    }

    public byte[] getTxId() {
        return txId;
    }

    public DnsFrame txid(byte[] txId) {
        this.txId = txId;
        length += txId.length;
        return self();
    }

    public byte getResponse() {
        return response;
    }

    public DnsFrame response(byte response) {
        this.response = response;
        length++;
        return self();
    }

    public byte[] getOpcode() {
        return opcode;
    }

    public DnsFrame opcode(byte[] opcode) {
        this.opcode = opcode;
        length += opcode.length;
        return self();
    }

    public byte getAuthoritativeAnswer() {
        return authoritativeAnswer;
    }

    public DnsFrame authoritativeAnswer(byte authoritativeAnswer) {
        this.authoritativeAnswer = authoritativeAnswer;
        length++;
        return self();
    }

    public byte getTruncated() {
        return truncated;
    }

    public DnsFrame truncated(byte truncated) {
        this.truncated = truncated;
        length++;
        return self();
    }

    public byte getRecursionDesired() {
        return recursionDesired;
    }

    public DnsFrame recursionDesired(byte recursionDesired) {
        this.recursionDesired = recursionDesired;
        length++;
        return self();
    }

    public byte getRecursionAvailable() {
        return recursionAvailable;
    }

    public DnsFrame recursionAvailable(byte recursionAvailable) {
        this.recursionAvailable = recursionAvailable;
        length++;
        return self();
    }

    public byte[] getZ() {
        return z;
    }

    public DnsFrame z(byte[] z) {
        this.z = z;
        length += z.length;
        return self();
    }

    public byte[] getRcode() {
        return rcode;
    }

    public DnsFrame rcode(byte[] rcode) {
        this.rcode = rcode;
        length += rcode.length;
        return self();
    }

    public byte[] getQuestions() {
        return questions;
    }

    public DnsFrame questions(byte[] questions) {
        this.questions = questions;
        length += questions.length;
        return self();
    }

    public byte[] getAnswer() {
        return answer;
    }

    public DnsFrame answer(byte[] answer) {
        this.answer = answer;
        length += answer.length;
        return self();
    }

    public byte[] getAuthority() {
        return authority;
    }

    public DnsFrame authority(byte[] authority) {
        this.authority = authority;
        length += authority.length;
        return self();
    }

    public byte[] getAdditional() {
        return additional;
    }

    public DnsFrame additional(byte[] additional) {
        this.additional = additional;
        length += additional.length;
        return self();
    }

    public byte[] name() {
        return name;
    }

    public DnsFrame name(byte[] name) {
        this.name = name;
        length += name.length;
        return self();
    }

    public byte[] getType() {
        return type;
    }

    public DnsFrame type(byte[] type) {
        this.type = type;
        length += type.length;
        return self();
    }

    public byte[] get_class() {
        return _class;
    }

    public DnsFrame _class(byte[] _class) {
        this._class = _class;
        length += _class.length;
        return self();
    }

    public List<Answer> answers() {
        return answers;
    }

    public DnsFrame answers(List<Answer> answers) {
        this.answers = answers;
        return self();
    }

    public byte[] originFrame() {
        return originFrame;
    }

    public DnsFrame originFrame(byte[] originFrame) {
        this.originFrame = originFrame;
        return self();
    }

    private DnsFrame self() {
        return this;
    }

    /**
     * 事务id2字节         flag2字节
     * 00111011 10001010 00000001 00000000 00000000 00000001 00000000 00000000
     * 00000000 00000000 00000000 00000000 00000101 01100010 01100001 01101001
     * 01100100 01110101 00000011 01100011 01101111 01101101 00000000 00000000
     *
     * @param channel
     * @param uuid
     * @return
     * @throws IOException
     */
    public ByteBuffer write(DatagramChannel channel, String uuid) throws IOException {
        //发送数据
        byte[] frame = build();
        String frameBinary = Utils.buildBinaryReadable(Utils.bytes2Binary(frame));
        LOGGER.info(" request frame {} {} ", frameBinary, uuid);
        channel.write(ByteBuffer.wrap(frame));

        //接收数据
        ByteBuffer rec = ByteBuffer.allocate(1024);
        int bytesRead = channel.read(rec);
        rec.flip();
        frameBinary = Utils.buildBinaryReadable(Utils.bytes2Binary(frame));
        LOGGER.info(" response frame {} {} ", frameBinary, uuid);
        return rec;
    }

    private byte[] build() {
        int off = 0;
        byte[] bytes = new byte[length];
        off = copy(off, bytes, txId);
        bytes[off++] = response;
        off = copy(off, bytes, opcode);
        bytes[off++] = authoritativeAnswer;
        bytes[off++] = truncated;
        bytes[off++] = recursionDesired;
        bytes[off++] = recursionAvailable;
        off = copy(off, bytes, z);
        off = copy(off, bytes, rcode);
        off = copy(off, bytes, questions);
        off = copy(off, bytes, answer);
        off = copy(off, bytes, authority);
        off = copy(off, bytes, additional);
        off = copy(off, bytes, name);
        off = copy(off, bytes, type);
        off = copy(off, bytes, _class);
        return Utils.binary2Bytes(bytes);
    }

}
