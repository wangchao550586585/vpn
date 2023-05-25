# vpn

## safari浏览器支持

首先开启socks5代理

![image-20230525152419243](/Users/a1/Library/Application Support/typora-user-images/image-20230525152419243.png)

可以正常访问

![image-20230525152536116](/Users/a1/Library/Application Support/typora-user-images/image-20230525152536116.png)

## 调试

输入： curl --socks5 192.168.1.101:1080 https://www.baidu.com

![image-20230525152727716](/Users/a1/Library/Application Support/typora-user-images/image-20230525152727716.png)

对应端口为50207

![image-20230525152748498](/Users/a1/Library/Application Support/typora-user-images/image-20230525152748498.png)

### 查看socks5协议

一次收发

![image-20230525152827809](/Users/a1/Library/Application Support/typora-user-images/image-20230525152827809.png)



![image-20230525152901761](/Users/a1/Library/Application Support/typora-user-images/image-20230525152901761.png)



二次收发

![image-20230525152929442](/Users/a1/Library/Application Support/typora-user-images/image-20230525152929442.png)

![image-20230525152941145](/Users/a1/Library/Application Support/typora-user-images/image-20230525152941145.png)

至此协议升级完成，后面开始传输数据。

### 查看https协议

tlsv加密正常。

![image-20230525153046900](/Users/a1/Library/Application Support/typora-user-images/image-20230525153046900.png)

### 查看http协议

输入命令行：curl --socks5 192.168.1.101:1080 http://www.baidu.com 

![image-20230525153207412](/Users/a1/Library/Application Support/typora-user-images/image-20230525153207412.png)

## 问题修复

1. 增加对粘包的处理
2. 主从Reactor
3. 针对select和register死锁问题，通过异步任务解决。
4. 减少频繁wakeup操作

可以看的到简单访问下，少了58次访问。

![image-20230525152254762](/Users/a1/Library/Application Support/typora-user-images/image-20230525152254762.png)
