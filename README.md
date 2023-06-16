# vpn

# 进展

1. 主从Reactor
2. 针对select和register死锁问题，通过异步任务解决。
3. 优化selectkey，采用数组替换set，性能提高2%
4. 支持自动伸缩缓冲区
5. 实现http协议
   1. get请求支持
      1. 获取图片支持
   2. post请求支持
      1. application/x-www-form-urlencoded支持
      2. multipart/form-data支持
      3. application/json支持
      4. 上传文件支持
6. 实现websocket协议
    1. 支持接收UTF-8的文本数据
    2. 支持二进制传输
    3. 支持响应close事件
    4. 支持响应ping事件
    5. 支持发送pong事件
    6. 支持发送8字节长度数据。2字节长度最高为65535.
7. 实现dns协议，基于udp协议。
8. tcp三次握手，4次挥手。
9. 实现udp(尚未开发)
10. 缓冲区对象池化（借鉴jemalloc，和netty池化内存思想)(尚未开发)

# socks5

## 浏览器支持

### chrome

首先开启socks5代理
<img width="1291" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/1195e170-2eff-4ff0-baf7-af6a0406ad11">
<img width="1355" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/dfcff39b-bcd5-4587-b29f-613ba1507693">
可以正常访问
<img width="985" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/02190f36-8e12-4bef-9248-cc21aeae1952">

### safari

首先开启socks5代理
<img width="805" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/0f597864-86a6-431d-8650-b374b84bbbb6">
可以正常访问
<img width="985" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/02190f36-8e12-4bef-9248-cc21aeae1952">

#### 调试

输入： curl --socks5 192.168.1.101:1080 https://www.baidu.com

<img width="957" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/ae0272f2-9eeb-45f1-a984-7e6d02941bfa">


对应端口为50207

<img width="938" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/f2aacc8c-27a2-429d-9a5b-e6dbc581e652">

## wireshark抓包

### 查看socks5协议

一次收发

<img width="962" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/10d4f29f-2306-4bd3-9232-5ce5d4f28094">



<img width="958" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/7a92f51b-4f23-4b21-a4c1-71f4de328fa7">


二次收发

<img width="954" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/01cbab8e-5eb6-454a-a02b-b730811b22d6">

<img width="947" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/a5cc0b0c-114f-4c34-b865-02bbf9641413">

至此协议升级完成，后面开始传输数据。

### 查看https协议

tlsv加密正常。

<img width="933" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/d22ec84b-23ca-4b4a-aaae-4d71ad2e5529">

### 查看http协议

输入命令行：curl --socks5 192.168.1.101:1080 http://www.baidu.com 

<img width="951" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/41322c3c-ee17-47ee-88ab-817ee433fe45">

## 压测 todo

# http1.1

## get请求支持

<img width="1348" alt="image-20230531203044309" src="https://github.com/wangchao550586585/vpn/assets/21312820/f37083bd-cc5c-435d-a2b2-6b5cd1fcfd25">


### 获取图片支持

<img width="1291" alt="image-20230531203236172" src="https://github.com/wangchao550586585/vpn/assets/21312820/fd10148d-d304-418c-bafb-16feb744bd87">

<img width="1183" alt="image-20230531203309520" src="https://github.com/wangchao550586585/vpn/assets/21312820/c4792a39-1b71-45ea-8f03-1080e7b8b28a">


## post请求支持

### application/json支持支持
<img width="1290" alt="image-20230531203414016" src="https://github.com/wangchao550586585/vpn/assets/21312820/ecc8242c-ae13-4c6e-bf4d-f52c3daeda1d">
<img width="1268" alt="image-20230531203715176" src="https://github.com/wangchao550586585/vpn/assets/21312820/937651ff-e7a4-4682-8c40-0d5356dfd2ca">
<img width="1222" alt="image-20230531203439676" src="https://github.com/wangchao550586585/vpn/assets/21312820/5fe6018a-a264-4294-a22d-4b3ed8ad3c70">

### application/x-www-form-urlencoded

<img width="905" alt="image-20230531203903127" src="https://github.com/wangchao550586585/vpn/assets/21312820/81ad9347-08ef-4ba0-b550-48b7db658950">


<img width="1258" alt="image-20230531203919260" src="https://github.com/wangchao550586585/vpn/assets/21312820/913b8fee-049a-498b-bfcc-07ba563b7dc2">


<img width="1258" alt="image-20230531203927419" src="https://github.com/wangchao550586585/vpn/assets/21312820/c0bf8f4e-da75-46e3-922f-7004382c5006">


### multipart/form-data支持(难点)

这里做个简单的表单然后提交，提交后输入的key-value显示，txt文件显示，图片则上传到服务器，并且显示。
<img width="1051" alt="image-20230531204012612" src="https://github.com/wangchao550586585/vpn/assets/21312820/c78495b5-bb09-491e-ac56-d80bc4c2534e">


效果如下：

<img width="630" alt="image-20230531204114114" src="https://github.com/wangchao550586585/vpn/assets/21312820/1f29c78a-0f43-43b3-937c-33f5a4eb6470">


<img width="649" alt="image-20230531204121856" src="https://github.com/wangchao550586585/vpn/assets/21312820/373199d5-8f77-4cd3-912a-8854e4e8dd6d">


<img width="1258" alt="image-20230531204215248" src="https://github.com/wangchao550586585/vpn/assets/21312820/3b337a16-e0ee-48aa-aa51-d132d2b0df7c">


解析数据如下：

<img width="662" alt="image-20230531204334652" src="https://github.com/wangchao550586585/vpn/assets/21312820/c531eef4-fe47-4ce4-a0e2-38145b22e0d2">


这里封装了一个CompositeByteBuf类，该类主要是为了解决粘包的问题。

上传的文件目录地址

<img width="423" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/e6d513a7-6e62-42b4-b1ba-599265104ac7">


### 上传文件支持


# websocket

## 协议升级过程

<img width="1414" alt="image-20230602133947084" src="https://github.com/wangchao550586585/vpn/assets/21312820/4c935308-6c1b-4a4b-b982-0b5da4ca97e3">

这里可以看到

- 响应码是101
- connection=Upgrade
- upgrade=websocket
- secWebSocketExtensions表示客户端期望使用的协议级别的扩展
- secWebSocketProtocol表示客户端希望使用的用逗号分隔的根据权重排序的子协议。
- secWebSocketKey返回解码之后的值，解码规则为sha+base64

```java
Response.builder()//构建状态行
        .httpVersion(request.getStartLine().getHttpVersion())
        //状态码101表示同意升级
        .httpStatus(HttpStatus.UPGRADE)
        .date()//构建响应头
        //不包含"Upgrade"的值（该值不区分大小写），那么客户端必须关闭连接。
        .connection(connection)
        //不是"websocket，那么客户端必须关闭连接。
        .upgrade(upgrade)
        //表示客户端期望使用的协议级别的扩展
        .secWebSocketExtensions(secWebSocketExtensions)
        //包含了一个或者多个客户端希望使用的用逗号分隔的根据权重排序的子协议。
        .secWebSocketProtocol(secWebSocketProtocol)
        //这里需要解码，解码规则为sha+base64
        .secWebSocketAccept(getKey(secWebSocketKey))
        .contentLanguage("zh-CN")
        .write(channelWrapped.channel(), channelWrapped.uuid());
```

## 发送数据过程

发送数据不需要掩码，因为掩码作用主要是为了防止客户端攻击服务端。而服务端则没这个问题，所以明文就行。

协议如下：

<img width="535" alt="image-20230602134623081" src="https://github.com/wangchao550586585/vpn/assets/21312820/091fa9c0-6f49-4372-8cfd-ec6baeb3b230">

<img width="1440" alt="image-20230602134707790" src="https://github.com/wangchao550586585/vpn/assets/21312820/4527c4e5-9bea-481e-b182-f3bf3e61cc26">

按照如上格式解析协议数据就行了。

<img width="1433" alt="image-20230602134833043" src="https://github.com/wangchao550586585/vpn/assets/21312820/8f56c0ed-1c18-42e0-b28f-48798c0a3ace">

响应也同理。

<img width="1433" alt="image-20230602134833043" src="https://github.com/wangchao550586585/vpn/assets/21312820/711ab081-aec9-40e6-826a-96dba1912c21">

## 关闭过程

<img width="1424" alt="image-20230602134951987" src="https://github.com/wangchao550586585/vpn/assets/21312820/3e8244be-4a47-4d47-bd59-bc3c1d5ef161">
通过js触发的close事件是没有响应体的。客户端触发的close事件服务端是需要回应的。我这里回应后并封装了code表示关闭状态。

<img width="1250" alt="image-20230602135212169" src="https://github.com/wangchao550586585/vpn/assets/21312820/8310f0f8-d6d1-4427-a7a4-1d54ee130ef2">

常用的关闭状态码

<img width="886" alt="image-20230602135341921" src="https://github.com/wangchao550586585/vpn/assets/21312820/ecfdab30-b03e-4aef-8259-2ca2b423491a">
### 浏览器关闭网页触发关闭

可以看到浏览器发送了code。code对应的数字为1001.
<img width="1270" alt="image-20230602140002199" src="https://github.com/wangchao550586585/vpn/assets/21312820/3e31febe-c21a-486b-a503-762100b0ba50">
<img width="1283" alt="image-20230602140054456" src="https://github.com/wangchao550586585/vpn/assets/21312820/489af21e-026d-4a4c-b826-c95fd3ab0fd4">

# dns
## 结构

```
   +---------------------+
    |        Header       | 报文头
    +---------------------+
    |       Question      | 要查询的问题
    +---------------------+
    |        Answer       | 服务器的应答
    +---------------------+
    |      Authority      | 权威的应答
    +---------------------+
    |      Additional     | 附加信息
    +---------------------+
```

请求对应图中如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/9251426c6f7b4f9bbaf0747f655a1b82.png)


服务器应答如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/8312d44caffe4af7a1441cc89fb9e715.png)


## 1.1 Header的格式 

```
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
```

- ID 请求客户端设置的16位标示，服务器给出应答的时候会带相同的标示字段回来，这样请求客户端就可以区分不同的请求应答了。
- QR    1个比特位用来区分是请求（0）还是应答（1）。
- OPCODE 4个比特位用来设置查询的种类，应答的时候会带相同值，可用的值如下：
  - 0        标准查询 (QUERY)
  - 1        反向查询 (IQUERY)
  - 2        服务器状态查询 (STATUS)
  - 3-15      保留值，暂时未使用
-  AA    授权应答(Authoritative Answer) - 这个比特位在应答的时候才有意义，指出给出应答的服务器是查询域名的授权解析服务器。注意因为别名的存在，应答可能存在多个主域名，这个AA位对应请求名，或者应答中的第一个主域名。
- TC    截断(TrunCation) - 表示是否被截断。值为 1 时，表示响应已超过 512 字节并已被截断，只返回前 512 个字节。
- RD    期望递归(Recursion Desired) - 这个比特位被请求设置，应答的时候使用的相同的值返回。如果设置了RD，就建议域名服务器进行递归解析，递归查询的支持是可选的。如果该位为 0，且被请求的名称服务器没有一个授权回答，它将返回一个能解答该查询的其他名称服务器列表。这种方式被称为迭代查询。
- RA    支持递归(Recursion Available) - 这个比特位在应答中设置或取消，用来代表服务器是否支持递归查询。 
- Z    保留值，暂时未使用。在所有的请求和应答报文中必须置为0。
- answer authenticated 回答认证，服务端设置。
- AD 表示认证数据。
- RCODE  应答码(Response code) - 这4个比特位在应答报文中设置，代表的含义如下：
  - 0        没有错误。
  - 1        报文格式错误(Format error) - 服务器不能理解请求的报文。
  - 2        服务器失败(Server failure) - 因为服务器的原因导致没办法处理这个请求。
  - 3        名字错误(Name Error) - 只有对授权域名解析服务器有意义，指出解析的域名不存在。
  - 4        没有实现(Not Implemented) - 域名服务器不支持查询类型。
  - 5        拒绝(Refused) - 服务器由于设置的策略拒绝给出应答。比如，服务器不希望对某些请求者给出应答，或者服务器不希望进行某些操作（比如区域传送zone transfer）。
  - 6-15      保留值，暂时未使用。
- QDCOUNT 无符号16位整数表示报文请求段中的问题记录数。
- ANCOUNT 无符号16位整数表示报文回答段中的回答记录数。
- NSCOUNT 无符号16位整数表示报文授权段中的授权记录数。
- ARCOUNT 无符号16位整数表示报文附加段中的附加记录数。

### 请求的格式

![在这里插入图片描述](https://img-blog.csdnimg.cn/d51305896a334596928e0a35abdb89ef.png)


- 事务id（Transaction ID）：0x003a
- Flags：DNS 报文中的标志字段。
  - QR：0。表示是一个请求
  - OPCODE：0000。表示是一个标准查询。
  - AA：0。应答的时候才会设置。
  - TC：0。表示没有被截断。
  - RD：1。表示期望递归解析。
  - RA：0。应答的事后设置。表示是否支持递归。
  - Z：0。保留，均设置0。
  - answer authenticated：0。回答认证，服务端设置。
  - AD：0。表示没有认证数据。
  - RCODE：0000。应答报文中设置。
- Questions：1。表示请求的question有一个
- Answers RRs：DNS 响应的数目，服务端设置。

### 应答的格式

![在这里插入图片描述](https://img-blog.csdnimg.cn/0c1b8d5652df4fad854563b331916199.png)


- 事务id：0x003a。同上一样，说明是同一个事务响应请求。

- Flags：DNS 报文中的标志字段。

  - QR：1。表示是一个应答。
  - OPCODE：0000。表示是一个标准查询。
  - AA：0。应答的服务器不是查询域名的授权解析服务器。
  - TC：0。表示没有被截断。
  - RD：1。表示期望递归解析。
  - RA：1。表示服务器支持递归解析。
  - Z：0。保留，均设置0。
  - answer authenticated：0。回答认证，服务端设置。
  - AD：0。表示没有认证数据。

  - RCODE：0000。表示没有错误。

- Questions：1。表示请求的question有一个
- Answers RRs：4。表明服务端响应了4个回答。

## 1.2 Question的格式 

在大多数查询中，Question段包含着问题(question)，比如，指定问什么。这个段包含QDCOUNT(usually 1)个问题，每个问题为下面的格式：

```
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
```

 字段含义如下：

- QNAME：要查询的域名，有时也会是 IP 地址，用于反向查询。域名被编码为一些labels序列，每个labels包含一个字节表示后续字符串长度，以及这个字符串，以0长度和空字符串来表示域名结束。注意这个字段可能为奇数字节，不需要进行边界填充对齐。
- QTYPE：DNS 查询请求的资源类型。通常查询类型为 A 类型，表示由域名获取对应的 IP4 地址。2个字节表示查询类型，取值可以为任何可用的类型值，以及通配码来表示所有的资源记录。
- QCLASS：地址类型，通常为互联网地址，值为 1。2个字节表示查询的协议类，比如，IN代表Internet。

### 常见QTYPE

| 代码                                                         | 号码  | 定义的 RFC                                                   | 描述                                                         | 功能                                                         |
| ------------------------------------------------------------ | ----- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| A                                                            | 1     | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | IP 地址记录                                                  | 传回一个 32 比特的 [IPv4](https://zh.wikipedia.org/wiki/IPv4) 地址，最常用于映射[主机名称](https://zh.wikipedia.org/wiki/主機名稱)到 [IP地址](https://zh.wikipedia.org/wiki/IP地址)，但也用于[DNSBL](https://zh.wikipedia.org/w/index.php?title=DNSBL&action=edit&redlink=1)（[RFC 1101](https://tools.ietf.org/html/rfc1101)）等。 |
| AAAA                                                         | 28    | [RFC 3596](https://tools.ietf.org/html/rfc3596)              | [IPv6](https://zh.wikipedia.org/wiki/IPv6) IP 地址记录       | 传回一个 128 比特的 IPv6 地址，最常用于映射主机名称到 IP 地址。 |
| AFSDB                                                        | 18    | [RFC 1183](https://tools.ietf.org/html/rfc1183)              | [AFS文件系统](https://zh.wikipedia.org/w/index.php?title=AFS檔案系統&action=edit&redlink=1) | （Andrew File System）数据库核心的位置，于域名以外的 AFS 客户端常用来联系 AFS 核心。这个记录的子类型是被过时的的 [DCE/DFS](https://zh.wikipedia.org/wiki/DCE/DFS)（DCE Distributed File System）所使用。 |
| APL                                                          | 42    | [RFC 3123](https://tools.ietf.org/html/rfc3123)              | 地址前缀列表                                                 | 指定地址列表的范围，例如：CIDR 格式为各个类型的地址（试验性）。 |
| CAA                                                          | 257   | [RFC 6844](https://tools.ietf.org/html/rfc6844)              | 权威认证授权                                                 | DNS认证机构授权，限制主机/域的可接受的CA                     |
| CDNSKEY                                                      | 60    | [RFC 7344](https://tools.ietf.org/html/rfc7344)              | 子关键记录                                                   | 关键记录记录的子版本，用于转移到父级                         |
| CDS                                                          | 59    | [RFC 7344](https://tools.ietf.org/html/rfc7344)              | 子委托签发者                                                 | 委托签发者记录的子版本，用于转移到父级                       |
| CERT                                                         | 37    | [RFC 4398](https://tools.ietf.org/html/rfc4398)              | 证书记录                                                     | 存储 [PKIX](https://zh.wikipedia.org/wiki/PKIX)、[SPKI](https://zh.wikipedia.org/w/index.php?title=SPKI&action=edit&redlink=1)、[PGP](https://zh.wikipedia.org/wiki/Pretty_Good_Privacy)等。 |
| [CNAME](https://zh.wikipedia.org/w/index.php?title=CNAME_記錄&action=edit&redlink=1) | 5     | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 规范名称记录                                                 | 一个主机名字的别名：[域名系统](https://zh.wikipedia.org/wiki/DNS)将会继续尝试查找新的名字。 |
| DHCID                                                        | 49    | [RFC 4701](https://tools.ietf.org/html/rfc4701)              | [DHCP](https://zh.wikipedia.org/wiki/DHCP)（动态主机设置协议）识别码 | 用于将 FQDN 选项结合至 [DHCP](https://zh.wikipedia.org/wiki/DHCP)。 |
| DLV                                                          | 32769 | [RFC 4431](https://tools.ietf.org/html/rfc4431)              | [DNSSEC](https://zh.wikipedia.org/wiki/DNSSEC)（域名系统安全扩展）来源验证记录 | 为不在DNS委托者内发布DNSSEC的信任锚点，与 DS 记录使用相同的格式，[RFC 5074](https://tools.ietf.org/html/rfc5074) 介绍了如何使用这些记录。 |
| [DNAME](https://zh.wikipedia.org/w/index.php?title=DNAME_記錄&action=edit&redlink=1) | 39    | [RFC 2672](https://tools.ietf.org/html/rfc2672)              | 代表名称                                                     | DNAME 会为名称和其子名称产生别名，与 CNAME 不同，在其标签别名不会重复。但与 CNAME 记录相同的是，DNS将会继续尝试查找新的名字。 |
| DNSKEY                                                       | 48    | [RFC 4034](https://tools.ietf.org/html/rfc4034)              | DNS 关键记录                                                 | 于DNSSEC内使用的关键记录，与 KEY 使用相同格式。              |
| DS                                                           | 43    | [RFC 4034](https://tools.ietf.org/html/rfc4034)              | 委托签发者                                                   | 此记录用于鉴定DNSSEC已授权区域的签名密钥。                   |
| HIP                                                          | 55    | [RFC 5205](https://tools.ietf.org/html/rfc5205)              | 主机鉴定协议                                                 | 将端点标识符及IP 地址定位的分开的方法。                      |
| IPSECKEY                                                     | 45    | [RFC 4025](https://tools.ietf.org/html/rfc4025)              | IPSEC 密钥                                                   | 与 [IPSEC](https://zh.wikipedia.org/wiki/IPSEC) 同时使用的密钥记录。 |
| KEY                                                          | 25    | [RFC 2535](https://tools.ietf.org/html/rfc2535)[[1\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-1)[RFC 2930](https://tools.ietf.org/html/rfc2930)[[2\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-rfc3445_sec1_def-2) | 关键记录                                                     | 只用于 SIG(0)（[RFC 2931](https://tools.ietf.org/html/rfc2931)）及 TKEY（[RFC 2930](https://tools.ietf.org/html/rfc2930)）。[[3\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-3)[RFC 3455](https://tools.ietf.org/html/rfc3455) 否定其作为应用程序键及限制DNSSEC的使用。[[4\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-rfc3445_sec1_subtype-4)[RFC 3755](https://tools.ietf.org/html/rfc3755) 指定了 DNSKEY 作为DNSSEC的代替。[[5\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-rfc3755_sec3-5) |
| [LOC记录](https://zh.wikipedia.org/w/index.php?title=LOC記錄&action=edit&redlink=1)（LOC record） | 29    | [RFC 1876](https://tools.ietf.org/html/rfc1876)              | 位置记录                                                     | 将一个域名指定地理位置。                                     |
| [MX记录](https://zh.wikipedia.org/wiki/MX记录)（MX record）  | 15    | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 电邮交互记录                                                 | 引导域名到该域名的[邮件传输代理](https://zh.wikipedia.org/w/index.php?title=郵件傳輸代理&action=edit&redlink=1)（MTA, Message Transfer Agents）列表。 |
| [NAPTR记录](https://zh.wikipedia.org/w/index.php?title=NAPTR記錄&action=edit&redlink=1)（NAPTR record） | 35    | [RFC 3403](https://tools.ietf.org/html/rfc3403)              | 命名管理指针                                                 | 允许基于正则表达式的域名重写使其能够作为 [URI](https://zh.wikipedia.org/wiki/URI)、进一步域名查找等。 |
| NS                                                           | 2     | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 名称服务器记录                                               | 委托[DNS区域](https://zh.wikipedia.org/w/index.php?title=DNS區域&action=edit&redlink=1)（DNS zone）使用已提供的权威域名服务器。 |
| NSEC                                                         | 47    | [RFC 4034](https://tools.ietf.org/html/rfc4034)              | 下一代安全记录                                               | DNSSEC 的一部分 — 用来验证一个未存在的服务器，使用与 NXT（已过时）记录的格式。 |
| NSEC3                                                        | 50    | [RFC 5155](https://tools.ietf.org/html/rfc5155)              | NSEC 记录第三版                                              | 用作允许未经允许的区域行走以证明名称不存在性的 DNSSEC 扩展。 |
| NSEC3PARAM                                                   | 51    | [RFC 5155](https://tools.ietf.org/html/rfc5155)              | NSEC3 参数                                                   | 与 NSEC3 同时使用的参数记录。                                |
| OPENPGPKEY                                                   | 61    | [RFC 7929](https://tools.ietf.org/html/rfc7929)              | OpenPGP公钥记录                                              | 基于DNS的域名实体认证方法，用于使用OPENPGPKEY DNS资源记录在特定电子邮件地址的DNS中发布和定位OpenPGP公钥。 |
| PTR                                                          | 12    | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 指针记录                                                     | 引导至一个[规范名称](https://zh.wikipedia.org/w/index.php?title=規範名稱&action=edit&redlink=1)（Canonical Name）。与 CNAME 记录不同，DNS“不会”进行进程，只会传回名称。最常用来运行[反向 DNS 查找](https://zh.wikipedia.org/w/index.php?title=反向_DNS_查找&action=edit&redlink=1)，其他用途包括引作 [DNS-SD](https://zh.wikipedia.org/w/index.php?title=DNS-SD&action=edit&redlink=1)。 |
| RRSIG                                                        | 46    | [RFC 4034](https://tools.ietf.org/html/rfc4034)              | DNSSEC 证书                                                  | DNSSEC 安全记录集证书，与 SIG 记录使用相同的格式。           |
| RP                                                           | 17    | [RFC 1183](https://tools.ietf.org/html/rfc1183)              | 负责人                                                       | 有关域名负责人的信息，电邮地址的 **@** 通常写为 **a**。      |
| SIG                                                          | 24    | [RFC 2535](https://tools.ietf.org/html/rfc2535)              | 证书                                                         | SIG(0)（[RFC 2931](https://tools.ietf.org/html/rfc2931)）及 TKEY（[RFC 2930](https://tools.ietf.org/html/rfc2930)）使用的证书。[[5\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-rfc3755_sec3-5)[RFC 3755](https://tools.ietf.org/html/rfc3755) designated RRSIG as the replacement for SIG for use within DNSSEC.[[5\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-rfc3755_sec3-5) |
| SOA                                                          | 6     | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 权威记录的起始                                               | 指定有关DNS区域的权威性信息，包含主要名称服务器、域名管理员的电邮地址、域名的流水式编号、和几个有关刷新区域的定时器。 |
| [SPF](https://zh.wikipedia.org/wiki/Sender_Policy_Framework) | 99    | [RFC 4408](https://tools.ietf.org/html/rfc4408)              | SPF 记录                                                     | 作为 SPF 协议的一部分，优先作为先前在 TXT 存储 SPF 数据的临时做法，使用与先前在 TXT 存储的格式。 |
| [SRV记录](https://zh.wikipedia.org/w/index.php?title=SRV記錄&action=edit&redlink=1)（SRV record） | 33    | [RFC 2782](https://tools.ietf.org/html/rfc2782)              | 服务定位器                                                   | 广义为服务定位记录，被新式协议使用而避免产生特定协议的记录，例如：MX 记录。 |
| SSHFP                                                        | 44    | [RFC 4255](https://tools.ietf.org/html/rfc4255)              | SSH 公共密钥指纹                                             | DNS 系统用来发布 [SSH](https://zh.wikipedia.org/wiki/SSH) 公共密钥指纹的资源记录，以用作辅助验证服务器的真实性。 |
| TA                                                           | 32768 | 无                                                           | DNSSEC 信任当局                                              | DNSSEC 一部分无签订 DNS 根目录的部署提案，，使用与 DS 记录相同的格式[[6\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-6)[[7\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-7)。 |
| [TKEY记录](https://zh.wikipedia.org/w/index.php?title=TKEY記錄&action=edit&redlink=1)（TKEY record） | 249   | [RFC 2930](https://tools.ietf.org/html/rfc2930)              | 秘密密钥记录                                                 | 为[TSIG](https://zh.wikipedia.org/w/index.php?title=TSIG&action=edit&redlink=1)提供密钥材料的其中一类方法，that is 在公共密钥下加密的 accompanying KEY RR。[[8\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-8) |
| TSIG                                                         | 250   | [RFC 2845](https://tools.ietf.org/html/rfc2845)              | 交易证书                                                     | 用以认证动态更新（Dynamic DNS）是来自合法的客户端，或与 DNSSEC 一样是验证回应是否来自合法的递归名称服务器。[[9\]](https://zh.wikipedia.org/wiki/域名伺服器記錄類型列表#cite_note-9) |
| TXT                                                          | 16    | [RFC 1035](https://tools.ietf.org/html/rfc1035)              | 文本记录                                                     | 最初是为任意可读的文本 DNS 记录。自1990年起，些记录更经常地带有机读数据，以 [RFC 1464](https://tools.ietf.org/html/rfc1464) 指定：[机会性加密](https://zh.wikipedia.org/wiki/机会性加密)（opportunistic encryption）、[Sender Policy Framework](https://zh.wikipedia.org/wiki/Sender_Policy_Framework)（虽然这个临时使用的 TXT 记录在 SPF 记录推出后不被推荐）、DomainKeys、DNS-SD等。 |
| URI                                                          | 256   | [RFC 7553](https://tools.ietf.org/html/rfc7553)              | 统一资源标识符                                               | 可用于发布从主机名到URI的映射。                              |

### 请求格式

![在这里插入图片描述](https://img-blog.csdnimg.cn/1264e2c39f674ba0b638d57832ef47aa.png)


应为上述协议头的questions个数为1，说明由一个question。

- name：www.biying.com。查询的域名为这个。

  - 注意看这里LabelCount=3，表示分了三段。这里每段我通过反编译发现都是通过.进行分割。拆分三段分别为
  - www，biying，com。
  - ![在这里插入图片描述](https://img-blog.csdnimg.cn/2f6903f5a619438ba2c6b84fd67e949c.png)


  - 每一个label由长度和数据组成。这里看第一段第一个字节为3，表示有3长度字节。反编译后为www。

  - ![在这里插入图片描述](https://img-blog.csdnimg.cn/673c44a262944935991dce3af01b60d5.png)


  - 同理一直到第三段，都是这样。然后三段过后紧随其后的是8bit的0，表示读取结束。

  - 代码如下：

    - ```java
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
      ```

- type：A。说明由域名获取ip地址。

- class：默认1。表示为互联网地址。

## 1.3 Answer

应答，授权，附加段都共用相同的格式：多个资源记录，资源记录的个数由报文头段中对应的几个数值确定，每个资源记录格式如下：

```
                                    1 1 1 1 1 1
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                                               |
    /                                               /
    /                      NAME                     /
    |                                               |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      TYPE                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                     CLASS                     |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                      TTL                      |
    |                                               |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    |                   RDLENGTH                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--|
    /                     RDATA                     /
    /                                               /
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

各字段含义如下：

- NAME  可变长字段，指该资源记录匹配的域名。它实际上就是查询报文问题部分查询名称的副本，但由于在域名重复出现的地方DNS使用压缩，这个字段就是到查询报文问题部分中的相应域名的指针偏移。

- TYPE   2个字节表示资源记录的类型，指出RDATA数据的含义

- CLASS  2个字节表示RDATA的类

- TTL   4字节无符号整数表示资源记录可以缓存的时间。0代表只能被传输，但是不能被缓存。

- RDLENGTH     2个字节无符号整数表示RDATA的长度

- RDATA  不定长字符串来表示记录，格式跟TYPE和CLASS有关。比如，TYPE是A，CLASS 是 IN，那么RDATA就是一个4个字节的ARPA网络地址。这里也会采用压缩。

  - 资源数据格式种类包含如下：

  - 数字：八位位组表示数，例如，IPv4地址是4个八位组整数，而IPv6地址是一个16个八位组整数。

  - 域名：可用标签序列来表示。每一个标签前面有1个字节长度字段，它定义标签中的字段数。长度字段的两个高位永远是0，标

    签的长度不能超过63字节。

  - 偏移指针：域名可以用偏移指针来替换。偏移指针是2字节字段，它的两个高位置为1

  - 字符串：用1字节的长度字段后面跟着长度字段数。长度字段并不像域名长度字段那样受限。字符串可以多达256个字符。
    
    

### 1.3.1 报文压缩

为了减小报文，域名系统使用一种压缩方法来消除报文中域名的重复。使用这种方法，后面重复出现的域名或者labels被替换为指向之前出现位置的指针。指针占用2个字节，格式如下：

```
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
    | 1 1|                OFFSET                    |
    +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```

前两个比特位都为1。因为lablels限制为不多于63个字节，所以label的前两位一定为0，这样就可以让指针与label进行区分。(10 和 01 组合保留，以便日后使用) 。偏移值(OFFSET)表示从报文开始的字节指针。偏移量为0表示ID字段的第一个字节。
    压缩方法让报文中的域名成为：
    \- 以0结尾的labels序列
    \- 一个指针
    \- 指针结尾的labels序列
    指针只能在域名不是特殊格式的时候使用，否则域名服务器或解析器需要知道资源记录的格式。目前还没有这种情况，但是以后可能会出现。
    如果报文中的域名需要计算长度，并且使用了压缩算法，那么应该使用压缩后的长度，而不是压缩前的长度。
    程序可以自由选择是否使用指针，虽然这回降低报文的容量，而且很容易产生截断。不过所有的程序都应该能够理解收到的报文中包含的指针。

### 1.3.2 应答的格式

![在这里插入图片描述](https://img-blog.csdnimg.cn/b069f6b01e8b4cdc956946abd1550045.png)


- name：www.biying.com。查询的域名为这个。
- type：5。表示CNAME，一个主机名字的别名。

- class：默认1。表示为互联网地址。
- ttl：3590。表示缓存59分钟，50秒。
- RDLENGTH：39。表示data占39字节。
- RDATA：主机别名。

可以看到name对应的是2字节。那为什么能够显示域名了？

答案：这是根据该2字节算出一个数字。该数字表示对于整个报文体的偏移量。

高端第1~2字节为1，因为lablels限制为不多于63个字节，所以label的前两位一定为0，这样就可以让指针与label进行区分。

![在这里插入图片描述](https://img-blog.csdnimg.cn/3e5cb296c1a64f87b9c245bc637d4d7e.png)


后面的14位对应12，表示报文体第12位开始。

![在这里插入图片描述](https://img-blog.csdnimg.cn/33fe1e4a12694507bdaf26f6bd45c571.png)


第12位是03，按照label的规则逐个解析就行了。解析到最后一行一定空行或者8字节0。

![在这里插入图片描述](https://img-blog.csdnimg.cn/47846ae2cf124088be64accf54b4932b.png)


解析为同上一样，说明解析成功。

![在这里插入图片描述](https://img-blog.csdnimg.cn/410ca896589f423daa7b0d32af0bbd42.png)


cname解析也同上，这里框1表示读取后续5位。框二同理。框三有点不同，但是可以看到第7~8位为1。说明与后续1字节组成偏移量。这里偏移量对应的是com。当读取偏移量后，也是根据读取字节为0结尾。

![在这里插入图片描述](https://img-blog.csdnimg.cn/52786d8bfd454273aa319cb7d95c3133.png)


解析结果如下：

![在这里插入图片描述](https://img-blog.csdnimg.cn/21e265105f704c30904e8fdf4ea61765.png)


可参考如下代码解析。
![在这里插入图片描述](https://img-blog.csdnimg.cn/022d421d393b4ef58e9dd760b05a5dea.png)


![在这里插入图片描述](https://img-blog.csdnimg.cn/388b9b38a160496cbccf2b9f747ceabf.png)

# dns





# 特性

1. 增加对粘包的处理
2. 主从Reactor
3. 针对select和register死锁问题，通过异步任务解决。
4. 减少频繁wakeup操作
   1. 可以看的到简单访问下，少了58次访问。
      <img width="951" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/e4b3cbb6-de88-4f8f-9465-01b46fa6d0c6">
5. 针对socks5协议的升级过程抽出3个方法。
6. 集成log4j，并添加彩色打印。
7. 优化selectkey，采用数组替换set，性能提高2%
8. 解决谷歌浏览器无法访问问题
9. 针对select和register死锁问题，通过异步任务解决。
10. 支持自动伸缩缓冲区
        1. <img width="896" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/08a2281a-e935-4377-b5c9-8d332b48ddcd">
11. 支持http请求
          1. <img width="903" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/4437b700-6260-4b65-a7c6-3cb3196121ec">
          2. <img width="893" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/48fcfc69-2d7d-4bbb-a18b-09bf80230c40">
11. 支持dns协议。
