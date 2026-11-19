package kr.tx24.test.conf;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import kr.tx24.lib.conf.Configure;
import kr.tx24.lib.lang.SystemUtils;

/**
 * 비밀값(KMS 키/IV, REDIS 암호)이 콘솔(stdout/stderr)에 출력되지 않는지 검증한다.
 * 더미 값으로 만든 tx24.properties 를 classpath 맨 앞에 두고 자식 JVM 을 띄워 출력을 캡처한다.
 * 실행: java -cp classes:lib/* kr.tx24.test.conf.SecretPrintTest (실패 시 종료코드 1)
 */
public class SecretPrintTest {

    private static final String KMS_KEY   = "DUMMYKMSKEY0123456789abcdefghij";
    private static final String KMS_IV    = "DUMMYIV012345678";
    private static final String LOG_KEY   = "DUMMYLOGPWxyz9876";
    private static final String CACHE_KEY = "DUMMYCACHEPWxyz9876";
    private static final String[] SECRETS = {KMS_KEY, KMS_IV, LOG_KEY, CACHE_KEY};

    public static void main(String[] args) {
        if (args.length > 0 && "startup".equals(args[0])) {
            SystemUtils.init();
            System.exit(0);
        }
        try {
            run();
            System.out.println("PASS");
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("FAIL: " + t.getMessage());
            System.exit(1);
        }
    }

    private static void run() throws Exception {
        Path tmp = Files.createTempDirectory("secret-print-");
        try {
            Files.createDirectories(tmp.resolve("conf"));
            writeDummyProperties(tmp);

            assertNoSecret("configure(4)", child(tmp, "4\n", Configure.class.getName()));
            assertNoSecret("install(3)", child(tmp, "3\n" + KMS_KEY + "\n" + KMS_IV + "\n" + LOG_KEY + "\n" + CACHE_KEY + "\n",
                    Configure.class.getName()));
            String enc = encrypt(LOG_KEY);
            assertNoSecret("decrypt(2)", child(tmp, "2\n" + enc + "\n", Configure.class.getName()));
            assertNoSecret("encrypt(1)", child(tmp, "1\n" + CACHE_KEY + "\n", Configure.class.getName()));
            assertNoSecret("startup", child(tmp, "", SecretPrintTest.class.getName(), "startup"));
        } finally {
            try (var s = Files.walk(tmp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void writeDummyProperties(Path dir) throws Exception {
        Properties p = new Properties();
        p.setProperty("KMS_KEY", encrypt(KMS_KEY));
        p.setProperty("KMS_IV", encrypt(KMS_IV));
        p.setProperty("REDIS_LOG_KEY", encrypt(LOG_KEY));
        p.setProperty("REDIS_KEY", encrypt(CACHE_KEY));
        try (var out = Files.newOutputStream(dir.resolve("tx24.properties"))) {
            p.store(out, "dummy");
        }
    }

    private static String encrypt(String plain) throws Exception {
        Method m = Configure.class.getDeclaredMethod("encrypt", String.class);
        m.setAccessible(true);
        return (String) m.invoke(new Configure(), plain);
    }

    private static String child(Path dir, String stdin, String mainClass, String... mainArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-DREDIS=127.0.0.1:1");
        cmd.add("-cp");
        cmd.add(dir.toAbsolutePath() + File.pathSeparator + System.getProperty("java.class.path"));
        cmd.add(mainClass);
        for (String a : mainArgs) {
            cmd.add(a);
        }
        Process pr = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        try (var in = pr.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        byte[] out = pr.getInputStream().readAllBytes();
        if (!pr.waitFor(30, TimeUnit.SECONDS)) {
            pr.destroyForcibly();
            throw new IllegalStateException("child timeout: " + mainClass);
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    private static void assertNoSecret(String scenario, String output) {
        for (String secret : SECRETS) {
            if (output.contains(secret)) {
                throw new IllegalStateException(scenario + " printed a secret value (" + secret.length() + " chars)");
            }
        }
        System.out.println("ok " + scenario);
    }
}
