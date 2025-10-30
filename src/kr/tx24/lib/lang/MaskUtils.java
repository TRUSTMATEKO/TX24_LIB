package kr.tx24.lib.lang;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class MaskUtils {
	private static final Logger logger 				= LoggerFactory.getLogger( MaskUtils.class );
	private static final char DEFAULT_REPLACE		= '*';
	private static final String DEFAULT_REPLACE_STR	= "****************************";
	private static final String REGEX_EMAIL_MASKING	= "(?<=.{%s}).(?=.*@)";
	
	
	private static final Pattern MASK_PHONE			=  Pattern.compile("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})$");
	private static final Pattern MASK_ACCOUNT		=  Pattern.compile("(^[0-9]+)$");
	private static final Pattern MASK_ADDR_OLD		=  Pattern.compile("(([가-힣]+(\\d{1,5}|\\d{1,5}(,|.)\\d{1,5}|)+(읍|면|동|가|리))(^구|)((\\d{1,5}(~|-)\\d{1,5}|\\d{1,5})(가|리|)|))([ ](산(\\d{1,5}(~|-)\\d{1,5}|\\d{1,5}))|)|");
	private static final Pattern MASK_ADDR_NEW		=  Pattern.compile("(([가-힣]|(\\d{1,5}(~|-)\\d{1,5})|\\d{1,5})+(로|길))");
	
	
	private static final Pattern MASK_NAME_KO		=  Pattern.compile("(?<=.{1})(?<masking>.*)(?=.$)");
	private static final Pattern MASK_NAME_EN		=  Pattern.compile("(?<=.{1})(?<masking>.*)(?=\\s)");
	
	private static final String MASK_DRIVER			= "([가-힣]{2})(\\d{1})(\\d{1})-(\\d{4})(\\d{2})-(\\d{1})(\\d{1})";
	
	/**
	 * 전화번호 마스킹 
	 * 하이픈이 있는 경우는 maskPhoneNoWithHyphen 와 하이픈 여부에 따라 연결됨.
	 * @param phone
	 * @return
	 */
	public static String maskPhoneNo(String phone){
		if(CommonUtils.isBlank(phone)) {
			return "";
		}
		phone = phone.replaceAll(" ", "");
		
		int len = phone.length();
		
		if(len < 9){
			return phone;
		}
		
		if(phone.indexOf("-") > -1) {
			return maskPhoneNoWithHyphen(phone);
		}
		
		if(len == 9){
			return phone.substring(0,2)+DEFAULT_REPLACE_STR.substring(0,3)+phone.substring(5);
		}else if(len == 10){
			return phone.substring(0,3)+DEFAULT_REPLACE_STR.substring(0,3)+phone.substring(6);
		}else if(len == 11){
			return phone.substring(0,3)+DEFAULT_REPLACE_STR.substring(0,4)+phone.substring(7);
		}else{
			return phone.substring(0,3)+DEFAULT_REPLACE_STR.substring(0,4)+phone.substring(7);
		}
	}
	
	/**
	 * 하이픈이 포함된 전화번호 마스킹 
	 * 하이픈이 없어도 사용해도 됨. maskPhoneNo 와 하이픈 여부에 따라 연결됨.
	 * @param phone
	 * @return
	 */
	public static String maskPhoneNoWithHyphen(String phone) {
		if(CommonUtils.isBlank(phone)) {
			return "";
		}
		
		phone = phone.replaceAll(" ", "");
		
		if(phone.length() < 9){
			return phone;
		}
		
		if(phone.indexOf("-") < 0) {
			return maskPhoneNo(phone);
		}
		try {
			Matcher matcher = MASK_PHONE.matcher(phone);
			if(matcher.find()) {
				String target = matcher.group(2);
				int length = target.length();
				char[] c = new char[length];
				Arrays.fill(c, '*');
				
				return phone.replace(target, String.valueOf(c));
			}
			return phone;
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",phone,CommonUtils.getExceptionMessage(e,5));
		}
		
		return phone;
	}
	
	/**
	 * Email Masking : REGEX  를 통한 마스킹 
	 * @param email
	 * @param start
	 * @return
	 */
	public static String maskEmail(String email,int start) {
		if(CommonUtils.isBlank(email)) {
			return "";
		}
		
		if(email.indexOf("@") <= 3 ) {
			return email;
		}else {
			try {
				return email.replaceAll(
				String.format(REGEX_EMAIL_MASKING,Integer.toString(start)),
				Character.toString(DEFAULT_REPLACE));
			}catch(Exception e) {
				logger.warn("mask error : {}, {}",email,CommonUtils.getExceptionMessage(e,5));
			}
			return email;
		}
	}
	
	/**
	 * 계좌번호 마스킹 
	 * @param accountNo
	 * @return
	 * @throws Exception
	 */
	public static String maskAccount(String accountNo) {
		if(CommonUtils.isBlank(accountNo)) {
			return "";
		}
		try {
			accountNo = accountNo.replaceAll("-", "").replaceAll(" ", "");
			// 계좌번호는 숫자만 파악하므로
			Matcher matcher = MASK_ACCOUNT.matcher(accountNo);
			if(matcher.find()) {
				int length = accountNo.length();
				if(length > 5) {
					char[] c = new char[5];
					Arrays.fill(c, DEFAULT_REPLACE);
					
					return accountNo.replace(accountNo, accountNo.substring(0, length-5) + String.valueOf(c));
				}
			}
			return accountNo;
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",accountNo,CommonUtils.getExceptionMessage(e,5));
		}
		return accountNo;
	}
	
	/**
	 * 생년월일 마스킹 뒷 일자만.
	 * @param dob 
	 * @return 
	 * @throws Exception
	 */
	public static String maskDob(String dob) {
		if(CommonUtils.isBlank(dob)) {
			return "";
		}
		try {
		
			return dob.replaceAll("(.{2}$)", DEFAULT_REPLACE_STR.substring(0,2));
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",dob,CommonUtils.getExceptionMessage(e,5));
		}
		return dob;
	}
	
	
	/**
	 * 주민번호 마스킹 뒷 6자리만 Mask 한다.
	 * @param ssn
	 * @return
	 */
	public static String maskSSN(String ssn) {
		if(CommonUtils.isBlank(ssn)) {
			return "";
		}
		try {
			return ssn.replaceAll("(.{6}$)", DEFAULT_REPLACE_STR.substring(0,6));
			
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",ssn,CommonUtils.getExceptionMessage(e,5));
		}
		return ssn;
	}
	
	/**
	 * 여권에 대한 뒷 4자리 마스킹 
	 * @param passport
	 * @return
	 */
	public static String maskPassport(String passport) {
		if(CommonUtils.isBlank(passport)) {
			return "";
		}
		try {
			return passport.replaceAll("(.{4}$)", DEFAULT_REPLACE_STR.substring(0,4));
			
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",passport,CommonUtils.getExceptionMessage(e,5));
		}
		return passport;
	}
	
	
	/**
	 * 운전면허 마스킹 서울12-123456-78 → 서울1*-1234**-*8
	 * @param passport
	 * @return
	 */
	public static String maskDriverLicense(String passport) {
		if(CommonUtils.isBlank(passport)) {
			return "";
		}
		try {
			return passport.replaceAll(MASK_DRIVER, "$1$2*-$4**-*$7");
			
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",passport,CommonUtils.getExceptionMessage(e,5));
		}
		return passport;
	}
	
	
	
	
	/**
	 * 주소 마스킹 신(구)주소, 도로명 주소
	 * @param address
	 * @return
	 * @throws Exception
	 */
	public static String maskAddress(String address) {
		if(CommonUtils.isBlank(address)) {
			return "";
		}
		
		try {
			Matcher matcher = MASK_ADDR_OLD.matcher(address);
			Matcher newMatcher = MASK_ADDR_NEW.matcher(address);
			if(matcher.find()) {
				return address.replaceAll("[0-9]", "*");
			} else if(newMatcher.find()) {
				return address.replaceAll("[0-9]", "*");
			}
			return address;
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",address,CommonUtils.getExceptionMessage(e,5));
		}
		return address;
	}
	
	
	public static String maskCardNumber(String cardNo) {
		if(CommonUtils.isBlank(cardNo)) {
			return "";
		}
		
		if(cardNo.length() < 9) {
			return cardNo;
		}
		
		//14자리 이하는 없겠지만 들어오면 끝 2자리만 마스킹 한다.
		if(cardNo.length() < 14) {
			return cardNo.replaceAll("(.{2}$)", DEFAULT_REPLACE_STR.substring(0,2));
		}
		
		try {
			String replace = DEFAULT_REPLACE_STR.substring(0,cardNo.length() -8 -3 );	//BIN 8 , 끝 3자리 			
			return cardNo.substring(0,8) + replace + cardNo.substring(cardNo.length()-3);
			
			
			
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",cardNo,CommonUtils.getExceptionMessage(e,5));
		}
		return cardNo;
	}
	
	
	
	public static String maskName(String name) {
		if(CommonUtils.isBlank(name)) {
			return "";
		}
		
		try {
		
			if (name.length() < 3) {
	             return name.substring(0, name.length() - 1) + Character.toString(DEFAULT_REPLACE);
	        }
	
	        if (name.charAt(0) >= 65 && name.charAt(0) <= 122) {
	             Matcher m = MASK_NAME_EN.matcher(name);
	
	             if (m.find()) {
	                 return name.replaceFirst(
	                	MASK_NAME_EN.toString(), 
	                	DEFAULT_REPLACE_STR.substring(0,m.group().length())
	                 );
	             }
	         }            
	        return name.replaceFirst(
	        		MASK_NAME_KO.toString(),
	        		DEFAULT_REPLACE_STR.substring(0,name.length() - 2)
	        	);
		}catch(Exception e) {
			logger.warn("mask error : {}, {}",name,CommonUtils.getExceptionMessage(e,5));
		}
		return name;
	}
	
	
	/**
	 * 
	  * 문자열 마스킹 
	 * @param src		원본 
	 * @param startIdx	시작위치 
	 * @return
	 */
	public static String masking(String src, int startIdx) {
        return masking(src, DEFAULT_REPLACE, null, startIdx, src.length());
    }
	
	/**
	 * 
	  * 문자열 마스킹 
	 * @param src		원본 
	 * @param startIdx	시작위치 
	 * @param length	길이
	 * @return
	 */
	public static String masking(String src, int startIdx, int length) {
        return masking(src, DEFAULT_REPLACE, null, startIdx, length);
    }
	
	/*
	 * 문자열 마스킹 
	 * @param src		원본 
	 * @param replace	대체문자
	 * @param startIdx	시작위치 
	 * @return
	 */
	public static String masking(String src, char replace, int startIdx) {
        return masking(src, replace, null, startIdx, src.length());
    }
	
	
	/**
	 * 문자열 마스킹 
	 * @param src		원본 
	 * @param replace	대체문자
	 * @param startIdx	시작위치
	 * @param length	길이
	 * @return
	 */
	public static String masking(String src, char replace, int startIdx, int length) {
		return masking(src, replace, null, startIdx, length);
	}
	
	/**
	 * 문자열 마스킹 YOUNGSEOK LEE 소스 참고
	 * @param src		원본 
	 * @param replace	대체문자
	 * @param exclude	제외문자
	 * @param startIdx	시작위치
	 * @param length	길이
	 * @return
	 */
	public static String masking(String src, char replace, char[] exclude, int startIdx, int length) {
		if(CommonUtils.isBlank(src)) {
			return "";
		}
		StringBuilder sb = new StringBuilder(src);
		// 종료 인덱스
		int endIdx = startIdx + length;
		if (sb.length() < endIdx)
			endIdx = sb.length();

		// 치환
		for (int i = startIdx; i < endIdx; i++) {
			boolean isExclude = false;
			// 제외 문자처리
			if (exclude != null && 0 < exclude.length) {
				char currentChar = sb.charAt(i);

				for (char excludeChar : exclude) {
					if (currentChar == excludeChar)
						isExclude = true;
				}
			}

			if (!isExclude)
				sb.setCharAt(i, replace);
			// sb.replace(i, i + 1, replace);
		}

		return sb.toString();
	}
	
	
	
	


}
