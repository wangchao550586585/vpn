package org.example.protocol.websocket.entity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.util.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class Frame {
    protected final Logger LOGGER = LogManager.getLogger(this.getClass());
    byte fin;
    byte[] rsv;
    byte[] opcode;
    byte mask;
    byte[] payloadLen;
    byte[] payloadLenExtended;
    byte[] maskingKey;
    byte[] payloadData;
    private int length;

    public static Frame builder() {
        return new Frame();
    }

    public byte[] build() {
        int off = 0;
        byte[] bytes = new byte[length];
        bytes[off++] = fin;
        off = copy(off, bytes, rsv);
        off = copy(off, bytes, opcode);
        bytes[off++] = mask;
        off = copy(off, bytes, payloadLen);
        if (Objects.nonNull(payloadLenExtended)) {
            off = copy(off, bytes, payloadLenExtended);
        }
        if (Objects.nonNull(maskingKey)) {
            off = copy(off, bytes, maskingKey);
        }
        if (Objects.nonNull(payloadData)) {
            off = copy(off, bytes, payloadData);
        }
        String frameString = Utils.printBinary(bytes);
        LOGGER.info("frame {} {} ", frameString, "");
        return Utils.binary2Bytes(bytes);
    }

    private int copy(int off, byte[] bytes, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            bytes[off++] = target[i];
        }
        return off;
    }

    private Frame self() {
        return this;
    }

    public Frame fin(byte fin) {
        this.fin = fin;
        length += 1;
        return self();
    }

    public Frame rsv(byte[] rsv) {
        this.rsv = rsv;
        length += rsv.length;
        return self();
    }

    public Frame opcode(byte[] opcode) {
        this.opcode = opcode;
        length += opcode.length;
        return self();
    }

    public Frame mask(byte mask) {
        this.mask = mask;
        length += 1;
        return self();
    }

    public Frame payloadLen(byte[] payloadLen) {
        this.payloadLen = payloadLen;
        length += payloadLen.length;
        return self();
    }

    public Frame payloadLenExtended(byte[] payloadLenExtended) {
        if (Objects.nonNull(payloadLenExtended)) {
            this.payloadLenExtended = payloadLenExtended;
            length += payloadLenExtended.length;
        }
        return self();
    }

    public Frame maskingKey(byte[] maskingKey) {
        if (Objects.nonNull(maskingKey)) {
            this.maskingKey = maskingKey;
            length += maskingKey.length;
        }
        return self();
    }

    public Frame payloadData(byte[] payloadData) {
        if (Objects.nonNull(payloadData)) {
            this.payloadData = payloadData;
            length += payloadData.length;
        }
        return self();
    }

    public void write(SocketChannel channel, String uuid) throws IOException {
        byte[] response = build();
        ByteBuffer byteBuffer = ByteBuffer.wrap(response);
        channel.write(byteBuffer);
        LOGGER.info("response {} {} ", response, uuid);
    }

}
