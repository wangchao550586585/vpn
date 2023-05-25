# vpn

## safari浏览器支持

首先开启socks5代理
<img width="805" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/0f597864-86a6-431d-8650-b374b84bbbb6">

可以正常访问

<img width="985" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/02190f36-8e12-4bef-9248-cc21aeae1952">

## 调试

输入： curl --socks5 192.168.1.101:1080 https://www.baidu.com

<img width="957" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/ae0272f2-9eeb-45f1-a984-7e6d02941bfa">


对应端口为50207

<img width="938" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/f2aacc8c-27a2-429d-9a5b-e6dbc581e652">

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

## 问题修复

1. 增加对粘包的处理
2. 主从Reactor
3. 针对select和register死锁问题，通过异步任务解决。
4. 减少频繁wakeup操作
      可以看的到简单访问下，少了58次访问。
<img width="951" alt="image" src="https://github.com/wangchao550586585/vpn/assets/21312820/e4b3cbb6-de88-4f8f-9465-01b46fa6d0c6">
