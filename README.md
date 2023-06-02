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
    2. 支持响应close事件
    3. 支持响应ping事件
    4. 支持发送pong事件
    5. 支持发送0~65535位以内数据。
7. 实现dns协议，基于udp协议。(开发中)
8. 实现tcp，udp(尚未开发)
9. 缓冲区对象池化（借鉴jemalloc，和netty池化内存思想)(尚未开发)

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
