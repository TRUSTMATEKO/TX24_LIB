package kr.tx24.lib.lang;


public class BitmapUtils {

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
		    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
		            + Character.digit(s.charAt(i+1), 16));
		}
		return data;
	}
	
	public static String byteArrayToBinaryString(byte[] b){
		StringBuilder sb=new StringBuilder();
		for(int i=0; i<b.length; ++i){
		    sb.append(byteToBinaryString(b[i]));
		}
		return sb.toString();
	}
	
	public static String byteToBinaryString(byte n) {
		StringBuilder sb = new StringBuilder("00000000");
		for (int bit = 0; bit < 8; bit++) {
			if (((n >> bit) & 1) > 0) {
			    sb.setCharAt(7 - bit, '1');
			    }
		}
		return sb.toString();
	} 
	
	/**
	 * 바이너리 스트링을 바이트배열로 변환
	 * 
	 * @param s
	 * @return
	 */
	public static byte[] binaryStringToByteArray(String s) {
		int count = s.length() / 8;
		byte[] b = new byte[count];
		for (int i = 1; i < count; ++i) {
		    String t = s.substring((i - 1) * 8, i * 8);
		    b[i - 1] = binaryStringToByte(t);
		}
		return b;
	}
 
	/**
	 * 바이너리 스트링을 바이트로 변환
	 * 
	 * @param s
	 * @return
	 */
	public static byte binaryStringToByte(String s) {
		byte ret = 0, total = 0;
		for (int i = 0; i < 8; ++i) {
		    ret = (s.charAt(7 - i) == '1') ? (byte) (1 << i) : 0;
		    total = (byte) (ret | total);
		}
		return total;
	}
	
	public static String byteArrayToHex(byte[] bytes){
		StringBuilder sb = new StringBuilder();
		for(final byte b: bytes){
			sb.append(String.format("%02x", b&0xff).toUpperCase());
		}
		return sb.toString();
			
	}
	
	
	public static String hexToString(String s){
		return BitmapUtils.byteArrayToBinaryString(BitmapUtils.hexStringToByteArray(s));
	}

	
	
	public static void main(String[] args){
		System.out.println("확인["+BitmapUtils.hexToString("BE18048128628210")+"]");
		System.out.println("승인 P["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("1011111000011000000001001000000100101000011000101000001100010000"))+"]");	//승인,취소
		System.out.println("승인 P["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("1011111000011000000001001000000100101000011000101000001100010000"))+"]");	//승인,취소
		System.out.println("승인 P["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("1011111000011000000001001000000100101000011000101000001100000000"))+"]");	//승인,취소
		System.out.println("승인 S["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("0000000000000000000000000000000000000000000000000000001000000100"))+"]");	//승인,취소
		System.out.println("개시 P["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("1000001000100000000000000000000000000010000000000000000000000000"))+"]");	//개시
		System.out.println("개시 S["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("0000010000000000000000000000000000000000000000000000000000000000"))+"]");	//개시
		System.out.println("망취 P["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("0011111000011000000000000000000100101010010000001000001100000000"))+"]");	//망취소
		System.out.println("망취 S["+BitmapUtils.byteArrayToHex(BitmapUtils.binaryStringToByteArray("0000000000000000000000000000000000000000000000000000001000000000"))+"]");	//망취소
		System.out.println("개시 P["+BitmapUtils.hexToString("8220000002000000")+"]");	//개시응답
		System.out.println("개시 S["+BitmapUtils.hexToString("0400000000000000")+"]");	//개시응답
		System.out.println("Xxxx확인["+BitmapUtils.hexToString("B238040128608A00")+"]");
		System.out.println("Xxxx확인["+BitmapUtils.hexToString("0000600000000000")+"]");
		System.out.println("Xxxx확인["+BitmapUtils.hexToString("B238040128609A00")+"]");
		
		
		
		
		
		System.out.println("["+BitmapUtils.hexToString("3E1800012A408300")+"]");
		
		
		String bitmap = "0000000000000000000000000000000000000000000000000000000000000000";
		StringBuilder sb = new StringBuilder(bitmap);
		
		System.out.println(sb.replace(0, 1, "1"));
		System.out.println(sb.replace(2, 3, "1"));
	}
}

