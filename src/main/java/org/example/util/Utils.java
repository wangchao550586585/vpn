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

}
