package org.example.util;

import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

public class Utils {
    public static String[] bytes2hexFirst(ByteBuffer buffer) {
        String[] bytes = new String[buffer.remaining()];
        final String HEX = "0123456789abcdef";
        for (int i = 0; buffer.hasRemaining(); i++) {
            StringBuilder sb = new StringBuilder(2);
            byte b = buffer.get();
            // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt((b >> 4) & 0x0f));
            // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
            sb.append(HEX.charAt(b & 0x0f));
            bytes[i] = sb.toString();
        }
        return bytes;
    }
    public static String hexToAscii(String[] host) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < host.length; i++) {
            String str = host[i];
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static void nPrint(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            String s = byteToAscii(bytes);
            System.out.println(fix + " " + s);
        }
    }
    public static String byteToAscii(byte[] host) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < host.length; i++) {
            output.append((char) host[i]);
        }
        return output.toString();
    }
    public static void nPrintByte(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            int size = allocate.remaining();
            String[] str = new String[size];
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.limit());
            final String HEX = "0123456789abcdef";
            for (int i = 0; i < bytes.length; i++) {
                StringBuilder sb = new StringBuilder(2);
                // 取出这个字节的高4位，然后与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
                sb.append(HEX.charAt((bytes[i] >> 4) & 0x0f));
                // 取出这个字节的低位，与0x0f与运算，得到一个0-15之间的数据，通过HEX.charAt(0-15)即为16进制数
                sb.append(HEX.charAt(bytes[i] & 0x0f));
                str[i] = sb.toString();
            }
            System.out.println(fix + " " + Arrays.toString(str));
        }
    }

    /**
     * 先将byte转成16进制数字
     * 然后将16进制数字转成10进制数字
     * 接着将10进制数字转成Ascii
     * @param b
     * @return
     */
    public static String byte2Ascii(byte b) {
        return String.valueOf((char)byteToInt(b));
    }
    public static String byte2Ascii2(byte b) {
        return String.valueOf((char)byteToInt2(b));
    }
    public static int byteToInt2(byte b) {
        return b & 0xFF;
    }

    /**
     * 可以优化
     * @param b
     * @return
     */
    // TODO: 2023/5/31
    public static int byteToInt(byte b) {
        return Integer.parseInt(byteToHex(b), 16);
    }

    public static String byteToHex(byte b) {
        String hex = Integer.toHexString(b & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }

    // TODO: 2023/5/31 可以优化
    /**
     * 1.Ascii -> 10进制
     * 2.10进制 -> 16进制
     * 3.16进制 -> byte
     *
     * @param str
     * @return
     */
    public static byte[] string2Byte(String str) {
        byte[] b = new byte[str.length()];
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            b[i] = Utils.charToByte(c);
        }
        return b;
    }
    public static String asciiToHex(char c) {
        String hex = Integer.toHexString(c);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        return hex;
    }
    public static byte charToByte(char c) {
        return (byte)Integer.parseInt(asciiToHex(c), 16);
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

}
