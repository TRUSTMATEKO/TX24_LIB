package kr.tx24.lib.zip;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    private static final int COMPRESSION_LEVEL = 8;
    private static final int BUFFER_SIZE = 1024 * 10;

    private ZipUtils() {
        // Utility class
    }

    // ---------------------------
    // 디렉토리/파일 전체 압축
    // ---------------------------
    public static void zip(String sourcePath, String output) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("압축 대상이 존재하지 않습니다: " + sourcePath);
        }
        if (!output.endsWith(".zip")) {
            throw new IllegalArgumentException("출력 파일명은 .zip 확장자를 가져야 합니다.");
        }

        // baseDir: 상대 경로 계산 기준
        File baseDir = sourceFile.isDirectory() ? sourceFile.getParentFile() : sourceFile.getParentFile();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))) {
            zos.setLevel(COMPRESSION_LEVEL);
            zipRecursive(sourceFile, baseDir, zos);
            zos.finish();
        }
    }

    private static void zipRecursive(File file, File baseDir, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    zipRecursive(child, baseDir, zos);
                }
            }
        } else {
            String entryName = baseDir.toPath().relativize(file.toPath()).toString().replace("\\", "/");
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(file.lastModified());
            zos.putNextEntry(entry);

            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = bis.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    // ---------------------------
    // 단일 파일 압축
    // ---------------------------
    public static void zipSingle(String sourceFile, String entryName, String output) throws IOException {
        File file = new File(sourceFile);
        if (!file.exists()) {
            throw new IllegalArgumentException("압축 대상이 존재하지 않습니다: " + sourceFile);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)));
             BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {

            zos.setLevel(COMPRESSION_LEVEL);
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(file.lastModified());
            zos.putNextEntry(entry);

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
            zos.finish();
        }
    }

    // ---------------------------
    // 압축 해제
    // ---------------------------
    public static void unzip(File zipFile, File targetDir, boolean fileNameToLowerCase) throws IOException {
        if (!zipFile.exists()) {
            throw new IllegalArgumentException("Zip 파일이 존재하지 않습니다: " + zipFile);
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = fileNameToLowerCase ? entry.getName().toLowerCase() : entry.getName();
                File targetFile = new File(targetDir, fileName);

                if (entry.isDirectory()) {
                    Files.createDirectories(targetFile.toPath());
                } else {
                    Files.createDirectories(targetFile.getParentFile().toPath());
                    unzipEntry(zis, targetFile);
                }

                zis.closeEntry();
            }
        }
    }

    private static void unzipEntry(ZipInputStream zis, File targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = zis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
    }
}
