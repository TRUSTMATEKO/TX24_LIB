package kr.tx24.lib.cipher;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Scanner;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;

/**
 * 
 */
public class RSAUtils {

	private static Logger logger = LoggerFactory.getLogger(RSAUtils.class );
	private BigInteger modules 			= null; 
	private BigInteger exponent 		= null; 
	private BigInteger d 				= null;

	public RSAUtils(){
		try{
			modules 	= new RSA().modulus; 
			exponent 	= new RSA().exponent; 
			d 			= new RSA().d;
		}catch(Exception e){
			logger.info("RSA : {}",CommonUtils.getExceptionMessage(e));
		}
	}
	
	public String decrypt(String input){
		if(input.trim().equals("")){
			return "";
		}
		String decryptText = "";
		byte[] encryptedFileBytes = Base64.getDecoder().decode(input.getBytes());
		try{
			
			KeyFactory factory = KeyFactory.getInstance("RSA"); 
			Cipher cipher = Cipher.getInstance("RSA");
			RSAPrivateKeySpec privSpec = new RSAPrivateKeySpec(modules, d); 
			PrivateKey privKey = factory.generatePrivate(privSpec); 
			cipher.init(Cipher.DECRYPT_MODE, privKey); 

			
			//RSA need 128 bytes for output
            int encryptedFileBytesChunkLength = 128;
            int numberOfEncryptedChunks = encryptedFileBytes.length / encryptedFileBytesChunkLength;

            //The limit per chunk is 117 bytes for RSA
            int decryptedFileBytesChunkLength = 100;
            int decryptedFileBytesLength = numberOfEncryptedChunks * encryptedFileBytesChunkLength;
            //It looks like we must create the decrypted file as long as the encrypted since RSA need 128 for output

            //Create the decoded byte array
            byte[] decryptedFileBytes = new byte[decryptedFileBytesLength];

            //Counters
            int decryptedByteIndex = 0;
            int encryptedByteIndex = 0;
            
            for(int i = 0; i < numberOfEncryptedChunks; i++){
            	if( i < numberOfEncryptedChunks -1 ){
            		decryptedByteIndex = decryptedByteIndex + cipher.doFinal(encryptedFileBytes, encryptedByteIndex, encryptedFileBytesChunkLength, decryptedFileBytes, decryptedByteIndex);
            		encryptedByteIndex = encryptedByteIndex + encryptedFileBytesChunkLength;
                }else{
                	decryptedByteIndex = decryptedByteIndex + cipher.doFinal(encryptedFileBytes, encryptedByteIndex, encryptedFileBytes.length - encryptedByteIndex, decryptedFileBytes, decryptedByteIndex);
                }
            }
            
            decryptText = new String(decryptedFileBytes,"UTF8").trim();
            
		}catch(Exception e){
			logger.info("RSA decrypt: {}",CommonUtils.getExceptionMessage(e));
		}
		return decryptText;
	}
	
	public String encrypt(String input){
		if(input.trim().equals("")){
			return "";
		}
		byte[] enText = null;
		try{
			KeyFactory factory = KeyFactory.getInstance("RSA"); 
			Cipher cipher = Cipher.getInstance("RSA"); 
			RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(modules, exponent);
			PublicKey pubKey = factory.generatePublic(pubSpec);
			cipher.init(Cipher.ENCRYPT_MODE, pubKey); 
			byte[] pText = input.getBytes("UTF8");
			
			//The limit per chunk is 117 bytes for RSA
            int decryptedFileBytesChunkLength = 100;
            int numberenOfDecryptedChunks = (pText.length-1) / decryptedFileBytesChunkLength + 1;

            //RSA need 128 bytes for output
            int encryptedFileBytesChunkLength = 128;
            int encryptedFileBytesLength = numberenOfDecryptedChunks * encryptedFileBytesChunkLength;

            //Create the encoded byte array
            byte[] encryptedFileBytes = new byte[ encryptedFileBytesLength ];

            //Counters
            int decryptedByteIndex = 0;
            int encryptedByteIndex = 0;

            for(int i = 0; i < numberenOfDecryptedChunks; i++){
            	if(i < numberenOfDecryptedChunks - 1){
            		encryptedByteIndex = encryptedByteIndex + cipher.doFinal(pText, decryptedByteIndex, decryptedFileBytesChunkLength, encryptedFileBytes, encryptedByteIndex);
            		decryptedByteIndex = decryptedByteIndex + decryptedFileBytesChunkLength;
            	}else{
            		cipher.doFinal(pText, decryptedByteIndex, pText.length - decryptedByteIndex, encryptedFileBytes, encryptedByteIndex);
            	}
           }

			enText = Base64.getEncoder().encode(encryptedFileBytes);
		}catch(Exception e){
			logger.info("RSA encrypt: {}",CommonUtils.getExceptionMessage(e));
		}
		return new String(enText);
	}
	
	public String getSystemIn(){
		Scanner scanner = new Scanner(System.in);
		String input = scanner.nextLine();
		return input.trim();
	}
	
	
	
	


}
