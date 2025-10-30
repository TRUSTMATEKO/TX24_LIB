package kr.tx24.lib.lang;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
Visa Card: ^4[0-9]{12}(?:[0-9]{3})?$^5[1-5][0-9]{14}$ 
Amex Card: ^3[47][0-9]{13}$ 
Carte Blanche Card: ^389[0-9]{11}$ 
Diners Club Card: ^3(?:0[0-5]|[68][0-9])[0-9]{11}$ 
Discover Card: ^65[4-9][0-9]{13}|64[4-9][0-9]{13}|6011[0-9]{12}|(622(?:12[6-9]|1[3-9][0-9]|[2-8][0-9][0-9]|9[01][0-9]|92[0-5])[0-9]{10})$ 
JCB Card: ^(?:2131|1800|35\d{3})\d{11}$ 
Visa Master Card: ^(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14})$ 
Insta Payment Card: ^63[7-9][0-9]{13}$ 
Laser Card: ^(6304|6706|6709|6771)[0-9]{12,15}$ 
Maestro Card: ^(5018|5020|5038|6304|6759|6761|6763)[0-9]{8,15}$ 
Solo Card: ^(6334|6767)[0-9]{12}|(6334|6767)[0-9]{14}|(6334|6767)[0-9]{15}$ 
Switch Card: ^(4903|4905|4911|4936|6333|6759)[0-9]{12}|(4903|4905|4911|4936|6333|6759)[0-9]{14}|(4903|4905|4911|4936|6333|6759)[0-9]{15}|564182[0-9]{10}|564182[0-9]{12}|564182[0-9]{13}|633110[0-9]{10}|633110[0-9]{12}|633110[0-9]{13}$ 
Union Pay Card: ^(62[0-9]{14,17})$ 
KoreanLocalCard: ^9[0-9]{15}$ 
BCGlobal: ^(6541|6556)[0-9]{12}$ 

 * @author juseop
 *
 */
public class RegExUtils {

	private static final String EMAIL_PATTERN 	= 
			"^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
			+ "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
	
	private static final String IPADDRESS_PATTERN 	= 
			"^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
			"([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

	private static final String CARD_PATTERN 	= 
			"^(?:(?<visa>4[0-9]{12}(?:[0-9]{3})?)|" +
			"(?<mastercard>5[1-5][0-9]{14})|" +
			"(?<discover>6(?:011|5[0-9]{2})[0-9]{12})|" +
			"(?<amex>3[47][0-9]{13})|" +
			"(?<diners>3(?:0[0-5]|[68][0-9])?[0-9]{11})|" +
			"(?<korealocal>9[0-9]{15}|" +
			"(?<bcglobal>(6541|6556)[0-9]{12}|" +
			"(?<unionpay>62[0-9]{14,17}|" +
			"(?<jcb>(?:2131|1800|35[0-9]{3})[0-9]{11})))))$";
	
	private static final String ALPHA_PATTERN				= "^[a-zA-Z]+$";;
	private static final String ALPHA_NUMBER_PATTERN		= "^[a-zA-Z0-9]+$";
	private static final String TRX_ID_PATTERN				= "^[a-zA-Z_\\-0-9]+$";
	
	
	
	
	public static boolean isAlpha(String str) {
		if(CommonUtils.isEmpty(str)) {
			return false;
		}
		return str.matches(ALPHA_PATTERN);
	}
	
	public static boolean isNotAlpha(String str) {
		if(CommonUtils.isEmpty(str)) {
			return true;
		}
		return !str.matches(ALPHA_PATTERN);
	}
	
	
	public static boolean isAlphaNumber(String str) {
		if(CommonUtils.isEmpty(str)) {
			return false;
		}
		return str.matches(ALPHA_NUMBER_PATTERN);
	}
	
	public static boolean isNotAlphaNumber(String str) {
		if(CommonUtils.isEmpty(str)) {
			return true;
		}
		return !str.matches(ALPHA_NUMBER_PATTERN);
	}
	
	public static boolean isTrxId(String str) {
		if(CommonUtils.isEmpty(str)) {
			return false;
		}
		return str.matches(TRX_ID_PATTERN);
	}
	
	public static boolean isNotTrxId(String str) {
		if(CommonUtils.isEmpty(str)) {
			return true;
		}
		return !str.matches(TRX_ID_PATTERN);
	}

	public static boolean isRegexMatches(String input, String pattern){
		if(input == null){
			input = "";
		}
		return Pattern.compile(pattern).matcher(input).matches();
	}

	public static boolean isNotRegexMatches(String input, String pattern){
		return !isRegexMatches(input, pattern);
	}

	public static boolean isRegexFind(String input, String pattern){
		if(input == null){
			input = "";
		}
		return Pattern.compile(pattern).matcher(input).find();
	}
	
	public static boolean isNotRegexFind(String input, String pattern){
		return !isRegexFind(input, pattern);
	}
	
	/**
	 * 이메일 양식 확인
	 * @param email
	 * @return
	 */
	public static boolean matchEmail(String email){
		
		Pattern pattern = Pattern.compile(EMAIL_PATTERN);
		Matcher matcher = pattern.matcher(email);
		return matcher.matches();
	}
	
	public static boolean isEmailFormat(String email) {
		return matchEmail(email);
	}
	
	public static boolean isNotEmailFormat(String email) {
		return !matchEmail(email);
	}
	
	/**
	 * 아이피 주소 양식 확인
	 * @param ipAddress
	 * @return
	 */
	public static boolean matchIpAddress(String ipAddress){
		Pattern pattern = Pattern.compile(IPADDRESS_PATTERN);
		Matcher matcher = pattern.matcher(ipAddress);
		return matcher.matches();
	}
	
	/**
	 * 입력값이 숫자값으로만 이루어져 있는지 확인
	 * @param num
	 * @return
	 */
	public static boolean isDigitNumber(String num){
		return num.matches("^\\d+$");
	}
	
	public static boolean isNotDigitNumber(String num) {
		return !isDigitNumber(num);
	}
	
	/**
	 * 숫자값과 (.)의 조합인지 확인
	 * @param num
	 * @return
	 */
	public static boolean isNumber(String num){
		return num.matches("^[-+]?\\d+(\\.\\d+)?$");
	}
	
	
	public static boolean isNotNumber(String num) {
		return !isNumber(num);
	}
	
	
	
	/**
	 * Decimal Type .xx 조합 확인
	 * @param num
	 * @return
	 */
	public static boolean isNumberWith2Decimals(String num){
		return num.matches("^\\d+\\.\\d{2}$");
	}
	
	/**
	 * Decimal Type .xxx 조합 확인
	 * @param num
	 * @return
	 */
	public static boolean isNumberWith3Decimals(String num){
		return num.matches("^\\d+\\.\\d{3}$");
	}
	

	/**
	 * 카드번호 정규 표현식 확인
	 * @param card 카드번호
	 * @return
	 */
	public static boolean cardMatch(String card){
		card = card.replaceAll("-", "");
		Pattern pattern = Pattern.compile(CARD_PATTERN);
		Matcher matcher = pattern.matcher(card);
		return matcher.matches();
	}
	
	/**
	 * 카드번호, 브랜드 일치 여부 확인
	 * @param card
	 * @param brands
	 * @return
	 */
	public static boolean cardMatchBrand(String card,String[] brands){
		boolean isMatch = false;
		card = card.replaceAll("-", "");
		Pattern pattern = Pattern.compile(CARD_PATTERN);
		Matcher matcher = pattern.matcher(card);
		
		if(matcher.matches()){
			for(String brand:brands){
				String cc = CommonUtils.isBlank(matcher.group(brand),"");
				if(cc.equals(card)){
					isMatch = true;
					break;
				}
			}		
		}
		
		return isMatch;
	}

	/**
	 * 룬 알고리즘을 통한 카드번호 유효성 확인
	 * @param card
	 * @return
	 */
	public static boolean cardLuhn(String card){
		int sum = 0;
        boolean alternate = false;
        card = card.replaceAll("-", "");
        for (int i = card.length() - 1; i >= 0; i--){
            int n = Integer.parseInt(card.substring(i, i + 1));
            if (alternate){
                n *= 2;
                if (n > 9){
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
	}
}
