package kr.tx24.lib.cipher;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 
 */
public class RSA implements RSAPublicKey
{
	public final String rsaPublicKeyAsXMLString = "<RSAKeyValue><Modulus>6zHnTwUkIMw4gaJy1jyDYWS3QWkv1c2kZIJBX9XVF/qCYqr8YeCNJ7s7Aw+CMlYsj6nBnZ0HbmZi+LZDL4pXIpgieYLx2eEfoNNcIHHN85b0k9LMSN0e/bt88plyJNZAbbC6lHGb89o5yC8SbtXOyaX0JPzcpbhllgxkBsll6sU=</Modulus><Exponent>AQAB</Exponent><P>/48o8HbfCyLQJhA+dUzL9xrEGlMN7XpfKSsU3ul2NjPNNKN2sbtyQIR6V7JNKcHPf0XEl02/2HNjb+swKhIpEw==</P><Q>65nAf6nfjlawL2PJzJFhVfpItMhLKz0GeGGsecHWmA+3SKMNVPmHAlZ/m25s1teMmDeG0gdGyY2eVjrH4nyvx==</Q><DP>PLD2/AR++9oMrL1218yhM9H7eHT4/cjuCIzerAGtvTmYJkQb0CHABGpx05nYkW4hpQwgn/4q7XCZ0N591b/yWQ==</DP><DQ>D8n2BJg1yyw0Enj1hSgZBdIIHVVu9C7ayCaDkIVVBEvJVKzYr+EpExcI5jm4QY2gdnMCLgEe1VMQUWcPDjac8Q==</DQ><InverseQ>a05H07cKYrROvpNP7Zj0DhJScVdD3lB315gpgo3v96mu5JgaOE9PPTbxCr8pvC7u95oRgudr+cr7Z3dusXXcUQ==</InverseQ><D>5Ac6ME7XvETD9Eu/8x4wctjnI7AMBMqgPCJdp8D2HY1vpKIKmArSUFci29DJdmw+P/tiEKISdmMmSeTMNud1meSbAah8vGo3EwF/aRPw8NrmsUxO2+gcWEetrNMU9paoCqEsQk6jKwpfjKxpp92fM10Qtdpnf0KCuY9w03EbQ/E=</D></RSAKeyValue>";
	public final BigInteger modulus;
	public final BigInteger exponent;
	public final BigInteger d;
	public RSA() throws Exception{
		String modulusAsBase64String = null;
		String exponentAsBase64String = null;
		String dAsBase64String = null;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document document = db.parse(new ByteArrayInputStream(rsaPublicKeyAsXMLString.getBytes("UTF-8"))); 
		Element root = document.getDocumentElement();
		// Go down the children looking for our interest nodes
		NodeList nodeList = root.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++){
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE){
				Element element = (Element)node;
				String nodeName = element.getNodeName();
				if (nodeName.equals("Modulus")){
					modulusAsBase64String = element.getTextContent();
				}else if (nodeName.equals("Exponent")){
					exponentAsBase64String = element.getTextContent();
				}else if (nodeName.equals("D")){
					dAsBase64String = element.getTextContent();
				}
			}
		}
		assert modulusAsBase64String != null;
		assert exponentAsBase64String != null;
		assert dAsBase64String != null;
		final byte[] modulusAsBytes = Base64.getDecoder().decode(modulusAsBase64String.getBytes());
		final byte[] exponentAsBytes = Base64.getDecoder().decode(exponentAsBase64String.getBytes());
		final byte[] dAsBytes = Base64.getDecoder().decode(dAsBase64String.getBytes());
		modulus = new BigInteger(1, modulusAsBytes);
		exponent = new BigInteger(1, exponentAsBytes);
		d = new BigInteger(1, dAsBytes);
	}
	
	public String getAlgorithm()
	{
		return "RSA";
	}
	
	public String getFormat()
	{
		return "MS"; // I don't know what the correct format specification should be!
	}
	
	public byte[] getEncoded()
	{
		try
		{
			return rsaPublicKeyAsXMLString.getBytes("UTF-8");
		}catch (Exception e){
			return null;
		}
	}
	public BigInteger getPublicExponent()
	{
		return exponent;
	}
	
	public BigInteger getModulus()
	{
		return modulus;
	}
	
	public String codificar(String args){
		byte[] encryptedData = null;
		try{
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, this);
			byte[] dateToEncrypt = args.getBytes();
			encryptedData = cipher.doFinal(dateToEncrypt);
			System.out.println("RSA: " + toHexString(encryptedData).toUpperCase());
			cipher = Cipher.getInstance("RSA/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, this);
			dateToEncrypt = args.getBytes();
			encryptedData = cipher.doFinal(dateToEncrypt);
			System.out.println("RSA/ECB/NoPadding: " + toHexString(encryptedData).toUpperCase());
			cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			cipher.init(Cipher.ENCRYPT_MODE, this);
			dateToEncrypt = args.getBytes();
			encryptedData = cipher.doFinal(dateToEncrypt);
			System.out.println("RSA/ECB/PKCS1Padding: " + toHexString(encryptedData).toUpperCase());
		}catch (Exception e){
			e.printStackTrace();
		}
		//String a = new String(encryptedData.toString());
		//return Arrays.toString(encryptedData);
		//return toBase64String(encryptedData, Base64Chars);
		return toHexString(encryptedData).toUpperCase();
	}
	
	private static final char[] HexChars = {'0', '1', '2', '3', '4', '5', '6', '7','8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	
	public static final String toHexString(byte[] bytes) {
		StringBuffer sb = new StringBuffer();
		int i;
		for (i=0; i < bytes.length; i++) {
			sb.append(HexChars[(bytes.length >> 4) & 0xf]);
			sb.append(HexChars[bytes.length & 0xf]);
		}
		return new String(sb);
	}
	
	private static final char[] Base64Chars = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
		'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
		'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
		'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', '0', '1', '2', '3',
		'4', '5', '6', '7', '8', '9', '+', '/',
		'='
	};
	
	public static final String toBase64String(byte[] bytes, char[] chars) {
		StringBuffer sb = new StringBuffer();
		int len = bytes.length, i=0, ival;
		while (len >= 3) {
			ival = ((int)bytes[i++] + 256) & 0xff;
			ival <<= 8;
			ival += ((int)bytes[i++] + 256) & 0xff;
			ival <<= 8;
			ival += ((int)bytes[i++] + 256) & 0xff;
			len -= 3;
			sb.append(chars[(ival >> 18) & 63]);
			sb.append(chars[(ival >> 12) & 63]);
			sb.append(chars[(ival >> 6) & 63]);
			sb.append(chars[ival & 63]);
		}
		switch (len) {
			case 0:// No pads needed.
				break;
			case 1: // Two more output bytes and two pads.
				ival = ((int)bytes[i++] + 256) & 0xff;
				ival <<= 16;
				sb.append(chars[(ival >> 18) & 63]);
				sb.append(chars[(ival >> 12) & 63]);
				sb.append(chars[64]);
				sb.append(chars[64]);
				break;
			case 2:// Three more output bytes and one pad.
				ival = ((int)bytes[i++] + 256) & 0xff;
				ival <<= 8;
				ival += ((int)bytes.length + 256) & 0xff;
				ival <<= 8;
				sb.append(chars[(ival >> 18) & 63]);
				sb.append(chars[(ival >> 12) & 63]);
				sb.append(chars[(ival >> 6) & 63]);
				sb.append(chars[64]);
				break;
		}
	return new String(sb);
	}

}
