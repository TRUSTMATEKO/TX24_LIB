package kr.tx24.lib.kms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * 
 */
public class KMSUtils {
	private static Logger logger 						= LoggerFactory.getLogger(KMSUtils.class);
	private static final String TRANSFORMATION 			= "AES/CBC/PKCS5Padding";
	private static SecretKeySpec SECRET_KEY 			= null;
	private static IvParameterSpec IV_PARAMETER 		= null;
	private static final String SUFFIX					= "ENC";
	private static final byte[] SUFFIXB					= SUFFIX.getBytes(StandardCharsets.UTF_8);						
	
	static {
		SystemUtils.init();
		SECRET_KEY 		= new SecretKeySpec(SystemUtils.KMS_KEY.getBytes(), "AES");
		IV_PARAMETER 	= new IvParameterSpec(SystemUtils.KMS_IV.getBytes());
	}


	public static List<String> encrypt(Collection<String> plains){
		 return plains.stream().map(KMSUtils::encrypt).collect(Collectors.toList());
	}
	
	
	public static List<String> decrypt(Collection<String> ciphers){
		return ciphers.stream().map(KMSUtils::decrypt).collect(Collectors.toList());
	}
	
	
	
	
	
	/**
	 * AES256 암호화 한 후 BASE64 로 인코딩하여 회신함.
	 * @param plain
	 * @return
	 */
	public static String encrypt(String plain) {
		if (CommonUtils.isEmpty(plain)) return "";

        try {
            byte[] encrypted = encrypt(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            logger.warn("Encrypt error: {}", CommonUtils.getExceptionMessage(e));
            return plain;
        }
	}
	
	/**
	 * BASE64 로 디코딩한후 AES256으로 복호화하여 회신함.
	 * @param encrypted
	 * @return
	 */
	public static String decrypt(String cipher) {
		if (CommonUtils.isEmpty(cipher)) return "";

        try {
            byte[] decoded = Base64.getDecoder().decode(cipher);
            return new String(decrypt(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Decrypt error: {}", CommonUtils.getExceptionMessage(e));
            return cipher;
        }
	}
	
	/**
	 * byte[] 암호화 데이터를 AES256 으로 복호화 하여 회신함.
	 * SUFFIX 3 BYTE 가 ENC 인 데이터만 처리함. 
	 * @param plain
	 * @return
	 */
	public static byte[] encrypt(byte[] plain) {
		if (plain == null) return null;

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, IV_PARAMETER);
            byte[] encryptedData = cipher.doFinal(plain);

            byte[] result = new byte[encryptedData.length + SUFFIXB.length];
            System.arraycopy(encryptedData, 0, result, 0, encryptedData.length);
            System.arraycopy(SUFFIXB, 0, result, encryptedData.length, SUFFIXB.length);

            return result;
        } catch (Exception e) {
            logger.warn("Encrypt error: {}", CommonUtils.getExceptionMessage(e));
            return plain;
        }
	}
	
	/**
	 * byte[] 데이터를 AES256 으로 암호화 하여 전달됨.
	 * SUFFIX 3 BYTE 에 자동으로 ENC 가 추가됨. 
	 * @param encrypted
	 * @return
	 */
	public static byte[] decrypt(byte[] encrypted) {
        if (encrypted == null || encrypted.length < SUFFIXB.length) return encrypted;

        String suffix = new String(encrypted, encrypted.length - SUFFIXB.length, SUFFIXB.length, StandardCharsets.UTF_8);
        if (!SUFFIX.equals(suffix)) return encrypted;

        try {
            byte[] data = new byte[encrypted.length - SUFFIXB.length];
            System.arraycopy(encrypted, 0, data, 0, data.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, IV_PARAMETER);
            return cipher.doFinal(data);
        } catch (Exception e) {
            logger.warn("Decrypt error: {}", CommonUtils.getExceptionMessage(e));
            return encrypted;
        }
    }
	
	
	/**
	 * 파일 암호화 , 파일을 암호화 하고 원본 파일에 대해서 삭제할지 옵션을 제공한다.
	 * 암호화된 파일은 Path 로 회신한다.
	 * 암호화가 정상으로 진행되면 동일한 Path 에 FilName.ext.enc 로 파일이 생성된다.
	 * 파일에 대한 암호화 또는 복호화만 진행할 경우는 encrypt(byte[]) 를 사용한다.
	 * @param path
	 * @param remove
	 * @return
	 * @throws Exception
	 */
	public static Path encryptFile(Path path,boolean remove) throws Exception {
		Path encryptedPath = Paths.get(path.toString() + ".enc");
        Files.write(encryptedPath, encrypt(Files.readAllBytes(path)));
        if (remove) Files.delete(path);
        return encryptedPath;
	}
	
	/**
	 * 파일 복호화 , 파일을 복호화 하고 원본 파일에 대해서 삭제할지 옵션을 제공한다.
	 * 복호화딘 파일은 Path 로 회신한다.
	 * 복호화가 정상으로 진행되면 동일한 Path 에 FileName 이 생성된다.
	 * 파일에 대한 암호화 또는 복호화만 진행할 경우는 encrypt(byte[]) 를 사용한다.
	 * @param path
	 * @param remove
	 * @return
	 * @throws Exception
	 */
	public static Path decryptFile(Path path,boolean remove) throws Exception {
		String originalName = path.getFileName().toString().replaceFirst("\\.enc$", "");
        Path decryptedPath = path.getParent().resolve(originalName);
        Files.write(decryptedPath, decrypt(Files.readAllBytes(path)));
        if (remove) Files.delete(path);
        return decryptedPath;
	}
	
	

	

}
