package kr.tx24.lib.cipher;

import java.nio.charset.Charset;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * AES/CBC/PKCS5Padding 기반 암호화/복호화 유틸리티
 */
public class AESCipher {

    private String iv;

    /**
     * 시스템에 지정된 DEFAULT KMS_IV 로 암호화 구동
     */
    public AESCipher() {
        this.iv = CommonUtils.hasValue(SystemUtils.KMS_IV) ? SystemUtils.KMS_IV : "iuytrewqkjhgfdsa";
    }

    public AESCipher(String iv) {
        this.iv = (iv != null && iv.length() > 0) ? iv : "iuytrewqkjhgfdsa";
    }

    /**
     * 시스템에 지정된 DEFAULT AES KEY 로 암호화 처리
     */
    public String encrypt(String plainTxt) {
        return encrypt(SystemUtils.KMS_KEY, plainTxt, Charset.defaultCharset().name());
    }

    public String encrypt(String key, String plainTxt) {
        return encrypt(key, plainTxt, Charset.defaultCharset().name());
    }

    /**
     * 시스템에 지정된 DEFAULT AES KEY 로 복호화 처리
     */
    public String decrypt(String encryptedTxt) {
        return decrypt(SystemUtils.KMS_KEY, encryptedTxt, Charset.defaultCharset().name());
    }

    public String decrypt(String key, String encryptedTxt) {
        return decrypt(key, encryptedTxt, Charset.defaultCharset().name());
    }

    /**
     * 지정 키와 Charset으로 암호화
     */
    public String encrypt(String key, String plainTxt, String charsetName) {
        if (!CommonUtils.hasValue(key) || !CommonUtils.hasValue(plainTxt)) return "";
        if (!CommonUtils.hasValue(charsetName)) charsetName = Charset.defaultCharset().name();

        String enTxt = "";
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(key), new IvParameterSpec(getIvBytes()));
            enTxt = Base64.getEncoder().encodeToString(cipher.doFinal(plainTxt.getBytes(charsetName)));
        } catch (Exception e) {
            System.err.println("[AESCipher.encrypt] Error: " + e.getMessage());
        }
        return enTxt;
    }

    /**
     * 지정 키와 Charset으로 복호화
     */
    public String decrypt(String key, String encryptedTxt, String charsetName) {
        if (!CommonUtils.hasValue(key) || !CommonUtils.hasValue(encryptedTxt)) return "";
        if (!CommonUtils.hasValue(charsetName)) charsetName = Charset.defaultCharset().name();

        String deTxt = "";
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(key), new IvParameterSpec(getIvBytes()));
            byte[] decoded = Base64.getDecoder().decode(encryptedTxt);
            deTxt = CommonUtils.toString(cipher.doFinal(decoded), charsetName);
        } catch (Exception e) {
            System.err.println("[AESCipher.decrypt] Error: " + e.getMessage());
        }
        return deTxt;
    }

    /**
     * AES 키를 32바이트로 패딩
     */
    private SecretKeySpec getSecretKey(String key) {
        byte[] keyBytes = CommonUtils.paddingSpaceToByte(key, 32);
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * IV를 16바이트로 반환
     */
    private byte[] getIvBytes() {
        return CommonUtils.paddingSpaceToByte(this.iv, 16);
    }
}
