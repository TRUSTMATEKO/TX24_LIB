package kr.tx24.lib.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kr.tx24.lib.lang.CommonUtils;

/**
 * 시스템에서 사용하는 기본 AES256 용 IV/KEY 셋팅
 * 및 REDIS LOG 용 및 REDIS 일반 용 암호 설정
 * KMS_KEY	= "asdfghjklqwertyuiopzsyslink0907!";
 * KMS_IV	= "iuytrewqkjhgfdsa";
 */
public class Configure {

    private static final String IV = "z2e4x6e89x1y3n5m";
    private static final String KEY = "1234z6g890feterw";
    private static final String NAME = "tx24.properties";
    private static Path path = null;

    private Scanner scanner = null;

    public Configure() {
        path = Paths.get(getRootDirectory(), "classes", Configure.NAME);
    }

    public void execute() {
        try { Thread.sleep(1000); } catch (Exception e) {}
        System.out.println("\n\n\n------------------");
        System.out.println("시스템 실행을 위한 기본 정보를 설정할 수 있습니다.");
        System.out.println("실행할 명령을 숫자로 입력하여 주시기 바랍니다.");
        System.out.println(" 1. 암호화 ");
        System.out.println(" 2. 복호화");
        System.out.println(" 3. 기본 설정");
        System.out.println(" 4. 기본 설정 확인");
        System.out.println(" 기타. 종료");

        scanner = new Scanner(System.in);
        int cmd = Integer.parseInt(scanner.nextLine());

        switch (cmd) {
            case 1: encrypt(); break;
            case 2: decrypt(); break;
            case 3: install(); break;
            case 4: configure(); break;
            default: System.out.println("\n실행할 명령이 일치하지 않습니다."); break;
        }
        scanner.close();
        System.out.println("\n시스템을 종료합니다.");
        System.exit(1);
    }

    public void install() {
        Properties props = new Properties();

        System.out.println("\n기본 암호키(32)를 설정하여 주시기 바랍니다.  : ");
        String kmsKey = scanner.nextLine().trim();
        if (CommonUtils.isNotEmpty(kmsKey)) {
            kmsKey = KEY;
        }
        kmsKey = String.format("%-32s", kmsKey);
        System.out.println("\n=> [" + kmsKey + "]");
        props.setProperty("KMS_KEY", encrypt(kmsKey));

        System.out.println("\n기본 암호 Vector 를 설정하여 주시기 바랍니다.  : ");
        String kmsIv = scanner.nextLine().trim();
        if (CommonUtils.isNotEmpty(kmsIv)) {
            kmsIv = IV;
        }
        kmsIv = String.format("%-16s", kmsIv);
        System.out.println("\n=> [" + kmsIv + "]");
        props.setProperty("KMS_IV", encrypt(kmsIv));

        System.out.println("\nREDIS (로그용) 암호를 설정하시기 바랍니다.  : ");
        String redisLogKey = scanner.nextLine().trim();
        if (CommonUtils.isNotEmpty(redisLogKey)) {
            redisLogKey = "";
        }
        System.out.println("\n=> [" + redisLogKey + "]");
        props.setProperty("REDIS_LOG_KEY", encrypt(redisLogKey));

        System.out.println("\nREDIS (캐쉬용) 암호를 설정하시기 바랍니다.  : ");
        String redisCacheKey = scanner.nextLine().trim();
        if (CommonUtils.isNotEmpty(redisCacheKey)) {
            redisCacheKey = "";
        }
        System.out.println("\n=> [" + redisCacheKey + "]");
        props.setProperty("REDIS_KEY", encrypt(redisCacheKey));

        try {
            String properties = "";
            if (Files.exists(path.getParent())) {
                props.store(new FileOutputStream(path.toFile()), "For TX24 library");
                properties = new String(Files.readAllBytes(path));
                System.out.println("\nconfig save to " + path.toString());
            } else {
                path = Paths.get(".", NAME);
                props.store(new FileOutputStream(path.toFile()), "For TX24 library");
                properties = new String(Files.readAllBytes(path));
                System.out.println("\nconfig save to " + path.toString());
            }
            System.out.println("\nconfig = \n[" + properties + "]");
            System.err.println("\n\n'" + NAME + "' 을 tx24_lib_x.jar 에 추가하여 주시기 바랍니다.");

            // 설치 후 System Property 반영
            load();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void configure() {
        Map<String, String> map = load();
        for (String k : map.keySet()) {
            if (map.get(k) == null) {
                System.out.println(k + " = ");
            } else {
                System.out.println(k + " = " + map.get(k));
            }
        }
    }

    public Map<String, String> load() {
        Map<String, String> map = new HashMap<>();
        Properties props = new Properties();
        InputStream input = null;

        try {
            input = Configure.class.getClassLoader().getResourceAsStream(NAME);
            if (input == null && Files.exists(path)) {
                input = new FileInputStream(path.toFile());
            }

            if (input != null) {
                props.load(input);
                for (Object o : props.keySet()) {
                    String k = (String) o;
                    String v = decrypt(props.getProperty(k, ""));
                    map.put(k, v);

                    // System Property 반영
                    if (System.getProperty(k) == null) {
                        System.setProperty(k, v);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        // 기본값 보장
        if (System.getProperty("KMS_KEY") == null) System.setProperty("KMS_KEY", KEY);
        if (System.getProperty("KMS_IV") == null) System.setProperty("KMS_IV", IV);
        if (System.getProperty("REDIS_KEY") == null) System.setProperty("REDIS_KEY", "");
        if (System.getProperty("REDIS_LOG_KEY") == null) System.setProperty("REDIS_LOG_KEY", "");

        return map;
    }

    public void encrypt() {
        System.out.println("\n평문 문자열을 입력하세요 : ");
        String plain = scanner.nextLine();
        System.out.println("\nPlain   : [" + plain.trim() + "]");
        System.out.println("\nEncrypt : [ENC:" + encrypt(plain.trim()) + "]");
    }

    public void decrypt() {
        System.out.println("\n암호화 문자열을 입력하세요 : ");
        String encrypt = scanner.nextLine();
        System.out.println("\nEncrypt : [" + encrypt.trim() + "]");
        System.out.println("\nPlain   : [" + decrypt(encrypt.trim()) + "]");
    }

    private String getRootDirectory() {
        return System.getProperty("user.dir", "")
                .replaceAll(File.separator + "bin", "")
                .replaceAll(File.separator + "conf", "");
    }

    private String encrypt(String plainTxt) {
        String enTxt = "";
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, getSecretKey(), new IvParameterSpec(IV.getBytes()));
            enTxt = Base64.getEncoder().encodeToString(c.doFinal(plainTxt.getBytes()));
        } catch (Exception e) {}
        return enTxt;
    }

    public String decrypt(String encryptedTxt) {
        String deTxt = "";
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, getSecretKey(), new IvParameterSpec(IV.getBytes()));
            byte[] db = Base64.getDecoder().decode(encryptedTxt);
            deTxt = new String(c.doFinal(db));
        } catch (Exception e) {}
        return deTxt;
    }

    private SecretKeySpec getSecretKey() {
        return new SecretKeySpec(KEY.getBytes(), "AES");
    }

    public static void main(String[] args) {
        new Configure().execute();
    }

}
