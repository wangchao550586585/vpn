package org.example;

import java.nio.ByteBuffer;
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

    private static void nPrint(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), allocate.remaining());
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
    private static void nPrintByte(ByteBuffer allocate, String fix) {
        if (allocate.remaining() > 0) {
            int size = allocate.remaining();
            String[] str = new String[size];
            byte[] bytes = Arrays.copyOfRange(allocate.array(), allocate.position(), size);
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
}
