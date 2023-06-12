package org.example.protocol.tcp.entity;

/**
 * 发送序列号空间
 * <p>
 *      1         2          3          4
 * ----------|----------|----------|----------
 *         SND.UNA    SND.NXT    SND.UNA
 *                               +SND.WND
 * <p>
 * 1 - 已经被确认的字节序列
 * 2 - 未确认的字节序列
 * 3 - 新数据发送可以使用的字节序列
 * 4 - 还没有分配的，未来会用的字节序列号
 */

/**
 * 接收序列号空间
 * <p>
 *      1          2          3
 * ----------|----------|----------
 *         RCV.NXT    RCV.NXT
 * +RCV.WND
 * <p>
 * 1 - 被确认的序列号
 * 2 - 打算接收的序列号
 * 3 - 还未被分配的序列号
 */
public class TCB {
    private int SourcePort;
    private int DestinationPort;
    private TcpStatus tcpStatus;
    //接收序列号空间
    // 发送但未确认的
    private int SND_UNA;
    // 下次发送的字节序列号
    private int SND_NXT;
    // 发送窗口
    private int SND_WND;
    // 紧急数据指针
    private int SND_UP;
    // 上次更新窗口使用的字节序列号
    private int SND_WL1;
    // 上次更新窗口使用的确认序列号
    private int SND_WL2;
    // 初始序列号
    private int ISS;
    //----------------------------------发送序列号空间
    // 下次希望接收的字节序列号
    private int RCV_NXT;
    // 接收窗口大小
    private int RCV_WND;
    // 紧急数据指针
    private int RCV_UP;
    // 连接建立时初始序列号
    private int IRS;

    private TCB self() {
        return this;
    }

    public int SourcePort() {
        return SourcePort;
    }

    public TCB SourcePort(int sourcePort) {
        SourcePort = sourcePort;
        return self();
    }

    public int DestinationPort() {
        return DestinationPort;
    }

    public TCB DestinationPort(int destinationPort) {
        DestinationPort = destinationPort;
        return self();
    }


    public TcpStatus TcpStatus() {
        return tcpStatus;
    }

    public TCB TcpStatus(TcpStatus tcpStatus) {
        this.tcpStatus = tcpStatus;
        return self();
    }

    public int SND_UNA() {
        return SND_UNA;
    }

    public TCB SND_UNA(int SND_UNA) {
        this.SND_UNA = SND_UNA;
        return self();
    }

    public int SND_NXT() {
        return SND_NXT;
    }

    public TCB SND_NXT(int SND_NXT) {
        this.SND_NXT = SND_NXT;
        return self();
    }

    public int SND_WND() {
        return SND_WND;
    }

    public TCB SND_WND(int SND_WND) {
        this.SND_WND = SND_WND;
        return self();
    }

    public int SND_UP() {
        return SND_UP;
    }

    public TCB SND_UP(int SND_UP) {
        this.SND_UP = SND_UP;
        return self();
    }

    public int SND_WL1() {
        return SND_WL1;
    }

    public TCB SND_WL1(int SND_WL1) {
        this.SND_WL1 = SND_WL1;
        return self();
    }

    public int SND_WL2() {
        return SND_WL2;
    }

    public TCB SND_WL2(int SND_WL2) {
        this.SND_WL2 = SND_WL2;
        return self();
    }

    public int ISS() {
        return ISS;
    }

    public TCB ISS(int ISS) {
        this.ISS = ISS;
        return self();
    }

    public int RCV_NXT() {
        return RCV_NXT;
    }

    public TCB RCV_NXT(int RCV_NXT) {
        this.RCV_NXT = RCV_NXT;
        return self();
    }

    public int RCV_WND() {
        return RCV_WND;
    }

    public TCB RCV_WND(int RCV_WND) {
        this.RCV_WND = RCV_WND;
        return self();
    }

    public int RCV_UP() {
        return RCV_UP;
    }

    public TCB RCV_UP(int RCV_UP) {
        this.RCV_UP = RCV_UP;
        return self();
    }

    public int IRS() {
        return IRS;
    }

    public TCB IRS(int IRS) {
        this.IRS = IRS;
        return self();
    }

    @Override
    public String toString() {
        return "TCB{\n" +
                "\nSourcePort=" + SourcePort +
                "\n, DestinationPort=" + DestinationPort +
                "\n, tcpStatus=" + tcpStatus +
                "\n, SND_UNA=" + SND_UNA +
                "\n, SND_NXT=" + SND_NXT +
                "\n, SND_WND=" + SND_WND +
                "\n, SND_UP=" + SND_UP +
                "\n, SND_WL1=" + SND_WL1 +
                "\n, SND_WL2=" + SND_WL2 +
                "\n, ISS=" + ISS +
                "\n, RCV_NXT=" + RCV_NXT +
                "\n, RCV_WND=" + RCV_WND +
                "\n, RCV_UP=" + RCV_UP +
                "\n, IRS=" + IRS +
                '}';
    }
}
