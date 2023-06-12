package org.example.protocol.tcp.entity;

public enum TcpStatus {
    //代表接收来自任何其他TCP组件的连接请求
    LISTEN("LISTEN"),
    //发送完SYN标志位之后，等待对方发送ACK标志
    SYN_SENT("SYN_SENT"),
    //等待接收之前发送的syn的ack。
    SYN_RECEIVED("SYN_RECEIVED"),
    //连接已经建立，用户进程可以收发数据。
    ESTABLISHED("ESTABLISHED"),
    //等待之前发送fin的ack或者连接对端的fin标志。
    FIN_WAIT_1("FIN_WAIT_1"),
    //等待对端的fin标识数据。
    FIN_WAIT_2("FIN_WAIT_2"),
    //等待本地用户进程发送关闭指令（一般是半关闭，比如java socket的shutdownOutput）。
    CLOSE_WAIT("CLOSE_WAIT"),
    //等待之前接收发送的fin的ack。
    LAST_ACK("LAST_ACK"),
    //对端收到本地发送回去fin ack的最长时间。
    TIME_WAIT("TIME_WAIT"),
    //标识连接已经拆除。
    CLOSED("CLOSED");
    String status;

    TcpStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "TcpStatus{" +
                "status='" + status + '\'' +
                '}';
    }
}
