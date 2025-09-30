package kr.tx24.lib.crypt;

import java.security.Key;
import java.security.MessageDigest;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * @author Administrator
 * CBC(Cipher Block Chaining), ECB(Electronic Code Block) 지원
 * OFB(Output FeedBack), CTR (CounTeR)  미지원
 */
public class CCrypt {


	/*
	 * 암호문으로 KEY 생성 
	 */
	public static Key generateKey(String algorithm,byte[] keyData) throws Exception {
		if("DES".equals(algorithm)){
			KeySpec keySpec = new DESKeySpec(keyData);
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
			SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
			return secretKey;
		}else if("DESede".equals(algorithm) || "TripleDES".equals(algorithm)) {
			KeySpec keySpec = new DESedeKeySpec(keyData);
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(algorithm);
			SecretKey secretKey = secretKeyFactory.generateSecret(keySpec);
			return secretKey;
		}else{
			return new SecretKeySpec(keyData, algorithm);
		}
	}


	
	
	public static String encryptBase64(Key secretKeySpec,String transforamtion,byte[] iv,byte[] data) throws Exception {
		return java.util.Base64.getEncoder().encodeToString(encrypt(secretKeySpec,transforamtion, iv, data)); 
	}
	
	public static byte[] encrypt(Key secretKeySpec,String transforamtion,byte[] iv,byte[] data) throws Exception {
		
		
		Cipher cipher = Cipher.getInstance(transforamtion) ;
		if(transforamtion.indexOf("CBC") > -1 || transforamtion.indexOf("CFB") > -1){
			IvParameterSpec ivSpec = new IvParameterSpec(iv) ;
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec) ;
		}else if(transforamtion.indexOf("ECB") > -1){
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec) ;
		}else{
			
		}
		return cipher.doFinal(data) ;
	}
	
	
	
	
	public static String decryptBase64(Key secretKeySpec,String transforamtion,byte[] iv,String data) throws Exception {
		return new String(decrypt(secretKeySpec,transforamtion, iv,java.util.Base64.getDecoder().decode(data))); 
	}
	
	
	public static byte[] decrypt(Key secretKeySpec,String transforamtion,byte[] iv,byte[] data) throws Exception {
	
		Cipher cipher = Cipher.getInstance(transforamtion) ;
		if(transforamtion.indexOf("CBC") > -1 || transforamtion.indexOf("CFB") > -1){
			IvParameterSpec ivSpec = new IvParameterSpec(iv) ;
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec) ;
		}else if(transforamtion.indexOf("ECB") > -1){
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec) ;
		}else{
			
		}
		return cipher.doFinal(data) ;
	}
	
	
	public static String getHashToBase64(String plain,String algorithm) throws Exception{
		
		byte[] hashBytes = getHash(plain, algorithm);
		return java.util.Base64.getEncoder().encodeToString(hashBytes);
	}
	
	
	public static String getHashToHex(String plain,String algorithm) throws Exception{
		
		byte[] hashBytes = getHash(plain, algorithm);
		StringBuilder hexString = new StringBuilder();
    	for (int i=0;i<hashBytes.length;i++) {
    	  hexString.append(Integer.toHexString(0xFF & hashBytes[i]));
    	}
    	return hexString.toString();
	}
	
	/** 
	 * algorithm SHA-1, MD5, SHA-256
	 * @param plain
	 * @param algorithm
	 * @return
	 */
	public static byte[] getHash(String plain,String algorithm) throws Exception{
		MessageDigest md = MessageDigest.getInstance(algorithm);
		return md.digest();
	}
	

}
