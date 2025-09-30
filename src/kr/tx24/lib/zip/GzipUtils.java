package kr.tx24.lib.zip;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;

public class GzipUtils {

    private static final Logger logger = LoggerFactory.getLogger(GzipUtils.class);
    private static final int BUFFER_SIZE = 1024;

    private GzipUtils() {
        // Utility class
    }

    // ---------------------------
    // Byte[] 압축 / 해제
    // ---------------------------
    public static byte[] compress(byte[] plain) throws IOException {
        if (plain == null || plain.length == 0) return null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(plain);
            gzip.finish();
            return baos.toByteArray();
        }
    }

    public static byte[] decompress(byte[] compressed) throws IOException {
        if (compressed == null || compressed.length == 0) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    // ---------------------------
    // String 압축 / 해제 (UTF-8)
    // ---------------------------
    public static String compressToBase64(String plain) throws IOException {
        if (plain == null || plain.isEmpty()) return null;
        byte[] compressed = compress(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(compressed);
    }

    public static String decompressFromBase64(String base64) throws IOException {
        if (base64 == null || base64.isEmpty()) return null;
        byte[] decoded = Base64.getDecoder().decode(base64);
        byte[] decompressed = decompress(decoded);
        return new String(decompressed, StandardCharsets.UTF_8);
    }

    // ---------------------------
    // 파일 압축 / 해제
    // ---------------------------
    public static boolean compressFile(String srcPath, String destPath) {
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(srcPath));
             BufferedOutputStream bos = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(destPath)))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return true;
        } catch (IOException e) {
            logger.warn(CommonUtils.getExceptionMessage(e));
            return false;
        }
    }

    public static boolean decompressFile(String zipPath, String destPath) {
        try (BufferedInputStream bis = new BufferedInputStream(new GZIPInputStream(new FileInputStream(zipPath)));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destPath))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            logger.debug("Decompressed file: {} -> {}", zipPath, destPath);
            return true;
        } catch (IOException e) {
            logger.warn(CommonUtils.getExceptionMessage(e));
            return false;
        }
    }
}
