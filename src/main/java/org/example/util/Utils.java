package org.example.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

public class Utils {
    protected static final Logger LOGGER = LogManager.getLogger(Utils.class);

    /**
     * 将二进制数据转成字符串打印
     *
     * @param allocate
     * @param fix
     */
    public static void printString(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            try {
                String s = new String(bytes, "utf-8");
                LOGGER.info(" {} {} ", fix, s);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 将allocate转成十六进制打印
     *
     * @param allocate
     * @param fix
     */
    public static void printHex(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            StringBuilder stringBuilder = new StringBuilder();
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            for (int i = 0; i < bytes.length; i++) {
                stringBuilder.append(byteToHex(bytes[i])).append(" ");
            }
            LOGGER.info(" {} {} ", fix, stringBuilder.toString());
        }
    }

    public static String byteToHex(byte b) {
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    /**
     * 二进制转十进制
     *
     * @param b
     * @return
     */
    public static int byteToIntV2(byte b) {
        return b & 0xFF;
    }

    public static int javaVersion() {
        String key = "java.specification.version";
        String version = "1.6";
        String value;
        if (System.getSecurityManager() == null) {
            value = System.getProperty(key);
        } else {
            value = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty(key));

        }
        version = value == null ? version : value;
        return majorVersion(version);
    }

    public static int majorVersion(String version) {
        final String[] components = version.split("\\.");
        int[] javaVersion = new int[components.length];
        for (int i = 0; i < javaVersion.length; i++) {
            javaVersion[i] = Integer.parseInt(components[i]);
        }
        if (javaVersion[0] == 1) {
            assert javaVersion[1] >= 6;
            return javaVersion[1];
        }
        return javaVersion[0];
    }

    public static ClassLoader getSystemClassLoader() {
        if (System.getSecurityManager() == null) {
            return ClassLoader.getSystemClassLoader();
        } else {
            return AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () -> ClassLoader.getSystemClassLoader());
        }
    }

    /**
     * ByteBuffer转成01显示
     *
     * @param byteBuffer
     * @return
     */
    public static byte[] bytes2Binary(ByteBuffer byteBuffer) {
        byte[] result = null;
        if (byteBuffer.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
            result = bytes2Binary(bytes);
        }
        return result;
    }

    /**
     * 字节转成01显示
     *
     * @param bytes
     * @return
     */
    public static byte[] bytes2Binary(byte[] bytes) {
        byte[] result = new byte[bytes.length * 8];
        for (int i = 0; i < bytes.length; i++) {
            byte[] dest = bytes2Binary(bytes[i]);
            System.arraycopy(dest, 0, result, i * 8, 8);
        }
        return result;
    }

    /**
     * 获取单个字节0101二进制显示。
     *
     * @param aByte
     * @return
     */
    public static byte[] bytes2Binary(byte aByte) {
        return new byte[]{(byte) ((aByte >> 7) & 0x1)
                , (byte) ((aByte >> 6) & 0x1)
                , (byte) ((aByte >> 5) & 0x1)
                , (byte) ((aByte >> 4) & 0x1)
                , (byte) ((aByte >> 3) & 0x1)
                , (byte) ((aByte >> 2) & 0x1)
                , (byte) ((aByte >> 1) & 0x1)
                , (byte) (aByte & 0x1)
        };
    }
    /**
     * 01的bit数组转成字节数组
     *
     * @param payloadData
     * @return
     */
    public static byte[] binary2Bytes(byte[] payloadData) {
        byte[] result = new byte[payloadData.length / 8];
        int r = 0;
        byte[] bytes1 = new byte[8];
        for (int i = 0; i < payloadData.length; i++) {
            int i1 = i % 8;
            if (i != 0 && i1 == 0) {
                result[r++] = (byte) Utils.binary2Int(bytes1);
            }
            bytes1[i1] = payloadData[i];
        }
        result[r] = (byte) Utils.binary2Int(bytes1);
        return result;
    }

    /**
     * 01转成单个int
     * 按照大端表示法
     * @param bytes
     * @return
     */
    public static int binary2Int(byte[] bytes) {
        int result = 0;
        int off = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            result += (bytes[i] << off);
            off++;
        }
        return result;
    }

    /**
     * 打印01组成的数组
     * 格式如下：
     * 11100101 00111111 00100000 01110011
     *
     * @param frame
     */
    public static String printBinary(byte[] frame) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frame.length; i++) {
            if (i != 0 && (i % 8) == 0) {
                sb.append(" ");
            }
            if (i != 0 && (i % 64) == 0) {
                sb.append("\r\n");
            }
            sb.append(frame[i]);
        }
        return sb.toString();
    }


    /**
     * 反掩码并编译成字符串
     * @param payloadData 01组成数组
     * @param maskingKey 01组成数组
     * @return
     */
    public static String unmask(byte[] payloadData, byte[] maskingKey) {
        byte[] result = binary2Bytes(payloadData);
        byte[] mask = binary2Bytes(maskingKey);
        return new String(mask(result, mask));
    }

    /**
     * 反掩码并编译成字节数组
     * @param payloadData 01组成数组
     * @param maskingKey 01组成数组
     * @return
     */
    public static byte[] unmaskBytes(byte[] payloadData, byte[] maskingKey) {
        byte[] result = binary2Bytes(payloadData);
        byte[] mask = binary2Bytes(maskingKey);
        return mask(result, mask);
    }

    /**
     * 进行掩码
     * original-octet-i：为原始数据的第i字节。
     * transformed-octet-i：为转换后的数据的第i字节。
     * j：为i mod 4的结果。
     * masking-key-octet-j：为mask key第j字节。
     * 算法描述为：
     * original-octet-i与 masking-key-octet-j异或后，得到 transformed-octet-i。
     * j = i MOD 4
     * transformed-octet-i = original-octet-i XOR masking-key-octet-j
     *
     * @param payloadData 字节
     * @param maskingKey  字节
     * @return
     */
    public static byte[] mask(byte[] payloadData, byte[] maskingKey) {
        for (int i = 0; i < payloadData.length; i++) {
            payloadData[i] = (byte) (payloadData[i] ^ maskingKey[i % 4]);
        }
        return payloadData;
    }

    public static byte[] buildStatusCode(int code) {
        byte[] sendPayloadData;
        int i = code / 256;
        int i1 = code % 256;
        byte[] bytes = Utils.bytes2Binary((byte) i);
        byte[] bytes2 = Utils.bytes2Binary((byte) i1);
        sendPayloadData = new byte[bytes.length + bytes2.length];
        System.arraycopy(bytes, 0, sendPayloadData, 0, bytes.length);
        System.arraycopy(bytes2, 0, sendPayloadData, bytes.length, bytes2.length);
        return sendPayloadData;
    }
}
