/**
 * SYSLINK LIBRARY 
 * @author : juseop , 2023. 11. 9.
 */
package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 시스템에서 주로 사용되는 
 * D,T,W,2W,M 구분에 의한 날짜 계산
 * 공급가액,부가세 등에 대한 다양한 계산 방식을 처리한다.
 * 시스템의 기본 계산 방식은 RoundingMode.HALF_EVEN(오사오입,Bankers Round) 방식을 사용한다. 
 */
public class CalcUtils {
	private static Logger logger = LoggerFactory.getLogger(CalcUtils.class);
	/**
	 * 
	 */
	public CalcUtils() {
		// TODO Auto-generated constructor stub
	}
	
	
	/**
	 * type : D+3,M+15 에 대한 날짜 계산 
	 * @param type
	 * @return
	 */
	public static String day(String type) {
		return day(type, DateUtils.getCurrentDay());
	}
	
	
	/**
	 * type : D+3,M+15,W+,2W+ 에 대한 날짜 계산 , 지정일자 기준  W+1 은 차주 월요일을 의미하며 2W+1 은 차차주 월요일을 의미한다.
	 * @param type
	 * @return
	 */
	public static String day(String type,String specifyDay) {
		
		if(type.startsWith("D")) {
			int daysToAdd = CommonUtils.parseInt(type.replaceAll("D[+]", ""));
			return DateUtils.getDay(specifyDay).plusDays(daysToAdd)
					.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}else if(type.startsWith("M")) {
			int monthsToAdd = CommonUtils.parseInt(type.replaceAll("M[+]", ""));
			return DateUtils.getDay(specifyDay).plusMonths(1)
				.format(DateTimeFormatter.ofPattern("yyyyMM"))+CommonUtils.paddingZero(monthsToAdd, 2);
		}else if(type.startsWith("W")) {
			int week = CommonUtils.parseInt(type.replaceAll("W[+]", ""));
			return DateUtils.getDay(specifyDay).plusWeeks(1).with(WeekFields.of(Locale.KOREA).dayOfWeek(),week+1)
				.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}else if(type.startsWith("2W")) {
			int week = CommonUtils.parseInt(type.replaceAll("2W[+]", ""));
			return DateUtils.getDay(specifyDay).plusWeeks(2).with(WeekFields.of(Locale.KOREA).dayOfWeek(),week+1)
				.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		}else {
			logger.warn("format error getDay , D,M,W,2W 으로 시작되지 않았음. : {}",type);
			return "";
		}
	}
	
	/**
	 * type : T+5 -> T+분개념 으로 일자시간분 계산하기 , 모든 계산은 현재 시간 기준임.
	 * yyyyMMddHHmm00 로 리턴된다.
	 * @param type
	 * @return
	 */
	public static String minutes(String type) {
		
		if(type.startsWith("T")) {
			long minutesToAdd = CommonUtils.parseInt(type.replaceAll("T[+]", ""));
			return DateUtils.getDate().plusMinutes(minutesToAdd)
					.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))+"00";
		}else {
			logger.warn("format error getMinutes , T 으로 시작되지 않았음. : {}",type);
			return "";
		}
	}
	
	

	
	/**
	 * 수수료 계산 금액 x 비율 , 기본 HALF_EVEN 오사오입(0부터 4까지는 버리고 5부터 9까지는 올리는 방식)
	 * 사사오입(HALF_UP)과 동일하게 똑같이 숫자 5를 기준으로 하는데 이때 5의 앞자리가 홀수인 경우 올림을 하고 짝수인 경우 내림을 한다.
	 * @param amount
	 * @param rate
	 * @return
	 */
	public static long fee(Number amount, double rate) {
		return fee(BDUtils.valueOf(amount), new BigDecimal(rate), RoundingMode.HALF_EVEN );
	}
	

	/**
	 * 수수료 계산 금액 x 비율 , 기본 HALF_EVEN 오사오입(0부터 4까지는 버리고 5부터 9까지는 올리는 방식)
	 * @param amount
	 * @param rate
	 * @return
	 */
	public static long fee(BigDecimal amount,BigDecimal rate) {
		return fee(amount, rate, RoundingMode.HALF_EVEN);
	}
	
	
	/**
	 * 수수료 계산 금액 x 비율 , 라운딩 모드 지정 
	 * @param amount
	 * @param rate
	 * @return
	 */
	public static long fee(BigDecimal amount,BigDecimal rate, RoundingMode mode) {
		if(BDUtils.isZero(amount) ||  BDUtils.isZero(rate)) {
			return 0;
		}else {
			if(BDUtils.isNegative(amount)) {
				return -amount.multiply(new BigDecimal(-1)).multiply(rate).setScale(0,mode).longValue();
			}else {
				return amount.multiply(rate).setScale(0,mode).longValue();
			}
		}
	}
	
	
	/**
	 * 수수료 계산 금액 x 비율 , 라운딩 모드 지정 
	 * @param amount
	 * @param rate
	 * @return
	 */
	public static double fee(BigDecimal amount,BigDecimal rate,int scale, RoundingMode mode) {
		if(BDUtils.isZero(amount) ||  BDUtils.isZero(rate)) {
			return 0;
		}else {
			if(BDUtils.isNegative(amount)) {
				return -amount.multiply(new BigDecimal(-1)).multiply(rate).setScale(scale,mode).doubleValue();
			}else {
				return amount.multiply(rate).setScale(scale,mode).doubleValue();
			}
		}
	}
	
	
	/**
	 * 합계 금액 / 11 ,기본 HALF_EVEN 오사오입(0부터 4까지는 버리고 5부터 9까지는 올리는 방식)
	 * @param n
	 * @return
	 */
	public static long vatByAmount(Number n) {
		return vatByAmount(n,RoundingMode.HALF_EVEN);

	}
	
	/**
	 * 합계 금액 / 11 하고 RoundingMode 에 따라 산출된다.
	 * @param n
	 * @return
	 */
	public static long vatByAmount(Number n,RoundingMode mode) {
		BigDecimal b = BDUtils.valueOf(n);
		return b.divide(new BigDecimal(11),0,mode).longValue();
	}
	
	/**
	 * 공급가액 * 0.1  기본 HALF_EVEN 오사오입(0부터 4까지는 버리고 5부터 9까지는 올리는 방식)
	 * @param n
	 * @return
	 */
	public static long vatBySupply(Number n) {
		return vatBySupply(n,RoundingMode.HALF_EVEN);
	}
	
	
	
	/**
	 * 공급가액 * 0.1 하고 RoundingMode 에 따라 산출된다.
	 * @param n
	 * @return
	 */
	public static long vatBySupply(Number n,RoundingMode mode) {
		BigDecimal b = BDUtils.valueOf(n);
		return b.multiply(new BigDecimal(0.1)).setScale(0,mode).longValue();
	}
	
	
	/**
	 * 총 금액에서 공급가액 계산 기본 RoundingMode.HALF_EVEN
	 * 총금액 - VAT = 공급가액 
	 * @param n
	 * @return
	 */
	public static long vosByAmount(Number n) {
		BigDecimal b = BDUtils.valueOf(n);
		return b.subtract(BDUtils.valueOf(vatByAmount(n))).longValue();
	}
	
	
	/**
	 * 총 금액에서 공급가액 계산 기본 지정된 RoundMode 사용 
	 * 총금액 - VAT = 공급가액 
	 * @param n
	 * @return
	 */
	public static long vosByAmount(Number n,RoundingMode mode) {
		BigDecimal b = BDUtils.valueOf(n);
		return b.subtract(BDUtils.valueOf(vatByAmount(n,mode))).longValue();
	}
	
	
	
	public static void main(String[] args) {
		System.out.println(day("D+1"));
		System.out.println(day("D+4"));
		System.out.println(day("M+4"));
		System.out.println(day("W+1"));
		System.out.println(minutes("T+5"));
		System.out.println(minutes("T+120"));
	
		System.out.println(fee(39895.10,0.1));
		System.out.println(vatByAmount(10000));
		System.out.println(vatByAmount(-10000));
		System.out.println(vatBySupply(-10000));
		System.out.println(vatBySupply(10000));
		System.out.println(vosByAmount(10000));
		System.out.println(vosByAmount(-10000));
	}
	
	
	
	

}
