package kr.tx24.lib.lang;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CommonUtils {
	private static final Logger logger 	= LoggerFactory.getLogger( CommonUtils.class );
	public static final String SPACE	= " ";
	public static final String EMPTY	= "";
	public static final String LF		= "\n";
	public static final String CR		= "\r";
	
	
	
	
	/**
	 * object null 여부 확인
	 * @param o
	 * @return
	 */
	public static boolean isNull(Object o) {
		if(o == null) {
			return true;
		}else {
			return false;
		}
	}
	
	
	/**
	 * object null 여부 확인
	 * @param o
	 * @return
	 */
	public static boolean isNotNull(Object o) {
		if(o == null) {
			return false;
		}else {
			return true;
		}
	}
	
	/**
	 * String null 여부 확인 및 NULL 일 경우 대체값 리턴 
	 * @param o
	 * @return
	 */
	public static String isNull(String str,String replace) {
		if(str == null) {
			return replace;
		}else {
			return str;
		}
	}
	
	
	/**
	 * String 이 NULL 이거나 공백만 있는지 여부 확인 
	 * @param str
	 * @return
	 */
	public static boolean isNullOrSpace(String str) {
		if(str == null || str.trim().isEmpty()) {
			return true;
		}else {
			return false;
		}
	}
	
	/**
	 * String NULL or 공백이라면 대체값 리턴 
	 * @param str
	 * @param replace
	 * @return
	 */
	public static String isNullOrSpace(String str,String replace) {
		if(str == null || str.trim().isEmpty()) {
			return replace;
		}else {
			return str;
		}
	}
	
	
	/**
	 * String 이 NULL 또는 공백이 아닌지 여부 확인 {@link CommonUtils#isNullOrSpace(String)} 의 반대 개념
	 * @param str
	 * @return
	 */
	public static boolean hasValue(String str) {
		if(str == null || str.isEmpty()) {
			return false;
		}else {
			return true;
		}
	}
	
	public static boolean hasValues(String... str) {
		if(str == null) {
			return false;
		}
		boolean result = true;
		for(String s : str) {
			if(s == null || s.equals("")) {
				result = false;
				break;
			}
		}
		return result;
	}
	
	/**
	 * String 이 NULL 또는 공백인지 여부 확인 {@link CommonUtils#isNullOrSpace(String)} 와 동일함.
	 * @param str
	 * @return
	 */
	public static boolean hasNotValue(String str) {
		if(str == null || str.isEmpty()) {
			return true;
		}else {
			return false;
		}
	}
	
	
	public static boolean hasNotValues(String... str) {
		return !hasValues(str);
	}
	
	/**
	 * String a , b 가 일치하지 않는지 여부 
	 * @param v1
	 * @param v2
	 * @return
	 */
	public static boolean isNotEquals(String v1,String v2) {
		return !v1.equals(v2);
	}
	
	
	
	/**
	 * Null to Space 로 변환
	 * @param str
	 * @return
	 */
	public static String nToB(String str) {
		if(str == null) {
			return "";
		}else {
			return str;
		}
	}
	
	
	public static boolean isEmpty(Object obj) {
		if(obj == null) {
			return true;
		}
		
		if (obj instanceof String s) return s.isEmpty();
        if (obj instanceof Collection<?> c) return c.isEmpty();
        if (obj instanceof Map<?,?> m) return m.isEmpty();
        if (obj instanceof Object[] arr) return arr.length == 0;
        if (obj instanceof Optional<?> opt) return opt.isEmpty();

	
		return false;
	}
	
	public static boolean isNotEmpty(Object obj) {
		return !isEmpty(obj);
	}
	
	/**
	 * @deprecated 더이상 사용하지 말고 가급적 String.split 을 사용하도록. 특수문자는 이스케이프문자(\\) 로 구분하여 사용해야 함.
	 * delimiter  사용시 | 은 [|] 로 사용하고  ,(쉼표), _(언더바),  (공백) ->  ,|_| 와 같이 여러 종류로 split  을 할때 구분자이다.
	 * * = [*]  , + = [+] , & = [&] , | = [|]  ? = [?]
	 * @param str
	 * @param delimiter
	 * @param trim
	 * @return
	 */
	@Deprecated
	public static String[] split(String str, String delimiter, boolean trim) {
		if(isNullOrSpace(str)) {
			return new String[0];
		}
		if(trim) {
			return Arrays.stream(str.split(delimiter))
            .map(String::trim)
            .toArray(String[]::new);
		}else {
			return str.split(delimiter,-1);
		}
	}
	
	
	/**
	 * String 을 구분하여 List<String> 을 반환한다.
	 * 특수문자는 이스케이프문자(\\) 로 구분하여 사용해야 함.
	 * delimiter  사용시 | 은 [|] 로 사용하고  ,(쉼표), _(언더바),  (공백) ->  ,|_| 와 같이 여러 종류로 split  을 할때 구분자이다.
	 * @param str
	 * @param delimiter
	 * @param trim
	 * @return
	 */
	public static List<String> stringToList(String str, String delimiter) {
        if (str == null || delimiter == null) return Collections.emptyList();
        return Stream.of(str.split(delimiter, -1))
                     .map(String::trim)
                     .toList(); // JDK 16+ : Collectors.toList() 대신
    }

	
	
	/**
	 * String array 의 size 를 조정할 때 사용한다. 줄이거나 추가하거나.
	 * @param array
	 * @param size
	 * @return
	 */
	public static String[] resizeArray(String[] array,int size) {
		List<String> list = resizeList(Arrays.asList(array),size);
		
		return list.toArray(new String[list.size()]);
	}
	
	
	/**
	 * List<String> 의 size 를 조정할 때 사용한다. 줄이거나 추가하거나 추가시는 공백이 자동으로 들어간다.
	 * @param list
	 * @param size
	 * @return
	 */
	public static List<String> resizeList(List<String> list, int size) {
	    if (list.size() >= size) return new ArrayList<>(list.subList(0, size));
	    List<String> extended = new ArrayList<>(list);
	    extended.addAll(Collections.nCopies(size - list.size(), ""));
	    return extended;
	}
	
	
	
	
	/**
	 * SPACE 0x20 을 길이만큼 추가하여 리턴한다.
	 * @param length
	 * @return
	 */
	public static String paddingSpace(int length){
		byte[] add = new byte[length];
		Arrays.fill(add,(byte)0x20);
		return CommonUtils.toString(add);
	}
	
	/**
	 * Object 를 문자로 변환하여 주어진 길이만큼으로 0x20 을 추가하여 회신한다. 
	 * 이때 charsetName 을 지정하면 해당 charset 으로 인코딩 된다. 
	 * charset 을 지정하지 않으면 기본 Charset.defaultCharset() 을 사용하게 된다.
	 * @param o
	 * @param length
	 * @param charsetNames
	 * @return
	 */
	public static String paddingSpace(Object o,int length,String... charsetNames) {
		String charsetName = Charset.defaultCharset().name();
		if(charsetNames.length > 0) {
			charsetName = charsetNames[0];
		}
		
		byte[] b = null; 
		try{
			if(o == null) {o = "";}
			b = CommonUtils.toString(o).getBytes(charsetName);
		}catch(Exception e){
			b = "".getBytes();
		}
	
		if(b.length == length){
			return CommonUtils.toString(b, charsetName);
		}else if(b.length > length){
			return CommonUtils.toString(b,0,length, charsetName);
		}else{
			byte[] n = new byte[length];
			Arrays.fill(n,(byte)0x20);
			System.arraycopy(b, 0, n, 0, b.length);
			return CommonUtils.toString(n, charsetName);
		}	
	}
	
	/**
	 * Object 를 문자로 변환하여 주어진 길이만큼으로 0x20 을 추가하여 회신한다. 
	 * 이때 charsetName 을 지정하면 해당 charset 으로 인코딩 된다. 
	 * charset 을 지정하지 않으면 기본 Charset.defaultCharset() 을 사용하게 된다.
	 * @param o
	 * @param length
	 * @param charsetNames
	 * @return
	 */
	public static byte[] paddingSpaceToByte(Object o,int length,String... charsetNames) {
		String charsetName = Charset.defaultCharset().name();
		if(charsetNames.length > 0) {
			charsetName = charsetNames[0];
		}
		
		byte[] b = null; 
		try{
			if(o == null) {o = "";}
			b = CommonUtils.toString(o).getBytes(charsetName);
		}catch(Exception e){
			b = "".getBytes();
		}
	
		if(b.length == length){
			return b;
		}else if(b.length > length){
			byte[] n = new byte[length];
			System.arraycopy(b, 0, n, 0, length);
			return n;
		}else{
			byte[] n = new byte[length];
			Arrays.fill(n,(byte)0x20);
			System.arraycopy(b, 0, n, 0, b.length);
			return n;
		}	
	}
	
	/**
	 * Object 를 숫자형으로 변경 후 해당 길이만큼 앞에 0 을 추가하여 리턴한다.
	 * @param o
	 * @param length
	 * @return
	 */
	public static String paddingZero(Object o,int length) {
		long val = 0;
		if(o instanceof java.lang.Integer 				|| o instanceof java.lang.Long
				|| o instanceof java.lang.Double 		|| o instanceof java.lang.Boolean
				|| o instanceof java.lang.Byte 			|| o instanceof byte[]
				|| o instanceof java.math.BigDecimal	|| o instanceof java.math.BigInteger
				){
			val = CommonUtils.parseLong(o);
			
		}else{
			try{
				String s = CommonUtils.toString(o);
				byte[] b = s.getBytes();
				if(b.length > length){
					s = new String(b,0,length);
				}
				val = Long.parseLong(s);
			}catch(Exception e){
			}
			
		}
		return String.format("%0" + length + "d", val);
	}
	
	/**
	 * Object 를 숫자형으로 변경 후 해당 길이만큼 앞에 0 을 추가하여 byte array 로 리턴한다.
	 * @param o
	 * @param length
	 * @return
	 */
	public static byte[] paddingZeroToByte(Object o,int length) {
		return paddingZero(o, length).getBytes();
	}
	
	
	/**
	 * byte array 에서 시작/종료까지를 추출하여 byte array  로 리턴한다.
	 * b 가 NULL or b.length < start or  end-start < 0 일 경우 NULL 을 리턴한다.
	 * b.length 가 end 보다 작은 경우는 b 를 리턴한다.
	 * @param b
	 * @param start
	 * @param end
	 * @return
	 */
	public static byte[] getBytes(byte[] b, int start, int end) {
		int len = b.length;
		
		
		if(len == 0 || len < start || end-start <0){
			return null;
		}
		if(len < end){
			return b;
		}else{
			byte[] dest = new byte[end-start];
			System.arraycopy(b, start, dest, 0, end-start);
			return dest;
		}
	}
	
	
	/**
	 * 2개 이상의 byte[] 를 합쳐서 리턴한다.
	 * @param a
	 * @param b
	 * @return
	 */
	public static byte[] concat(byte[]... arrays ) {
		if(arrays.length == 1) {
			return arrays[0];
		}
		int length = Arrays.stream(arrays)
						.filter(Objects::nonNull)
						.mapToInt(s -> s.length).sum();
		 
		ByteBuffer byteBuffer = ByteBuffer.allocate(length);
		if (arrays != null) {
            Arrays.stream(arrays).filter(Objects::nonNull).forEach(byteBuffer::put);
        }
		return byteBuffer.array();
	}
	
	
	
	/**
	 * @deprecated 
	 * System.arraycopy 를 간단하게 쓸 목적으로 만든것이나. System.arraycopy 를 사용하는 것이 더 현명하다.
	 * @param destBuffer
	 * @param destPos
	 * @param srcBuffer
	 * @param srcLen
	 */
	@Deprecated
	public static void arraycopy(Object destBuffer,int destPos,Object srcBuffer,int srcLen){
		System.arraycopy(srcBuffer,0,destBuffer,destPos,srcLen);
	}
	
	
	/**
	 * Exception 에 대한 stacktrace 출력 기본값 10행
	 * @param e
	 * @return
	 */
	public static String getExceptionMessage(Exception e){
		return getExceptionMessage(e, 10);
	}
	
	/**
	 * 
	 * @param e
	 * @param length
	 * @return
	 */
	public static String getExceptionMessage(Exception e, int length){
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] trace = null;
		if(e instanceof SQLException){
			SQLException sql = (SQLException)e;
			sb.append(System.lineSeparator())
				.append("code:").append(sql.getErrorCode ())
				.append(",message:").append(sql.getMessage())
				.append(System.lineSeparator());
			trace = e.getStackTrace();
		}else{
			sb.append("message =")
				.append(e.getMessage())
				.append(System.lineSeparator());
			trace = e.getStackTrace();
		}
		
		if(trace != null && trace.length > 0){
			int depth = Math.min(length, trace.length);
			for(int i = 0; i < depth; i++ ) {
				sb.append(trace[i].toString())
					.append(System.lineSeparator());
			}
		}
		
		return sb.toString();
	}
	
	public static String getExceptionMessage(Throwable cause) {
		return getExceptionMessage(cause,10);
	}
	
	
	public static String getExceptionMessage(Throwable cause , int length) {
		StringBuilder sb = new StringBuilder();
	
		StackTraceElement[] trace =  cause.getStackTrace();		
		if(trace != null && trace.length > 0){
			int depth = trace.length > length ? length : trace.length;
			sb.append(cause.getMessage());
			for(int i = 0; i < depth; i++ ) {
				
				sb.append(trace[i].toString())
					.append(System.lineSeparator());
			}
		}	
		return sb.toString();
		
	}

	
	
	
	
	/**
	 * null이거나, 비어있는 문자열/컬렉션이거나, false/0 이면 false를 반환합니다.
	 * @param o 검사할 객체
	 * @return 값이 존재하거나 참(True)을 의미하면 true
	 */
	public static boolean toBoolean(Object o) {
	    if (o == null) {
	        return false;
	    }

	    // ⭐ JDK 17+ Pattern Matching for instanceof를 사용하여 타입 체크와 형 변환을 동시에 수행
	    
	    // 1. Boolean 타입 처리
	    if (o instanceof Boolean boolValue) {
	        return boolValue;
	    }

	    // 2. String 타입 처리
	    if (o instanceof String strValue) {
	        String trimmed = strValue.trim();
	        
	        // 공백이거나 비어있는 문자열이면 false
	        if (trimmed.isEmpty()) {
	            return false;
	        }
	        
	        // "true", "1", "Y" 등을 명시적으로 참으로 간주
	        String lower = trimmed.toLowerCase();
	        return lower.equals("true") || lower.equals("1") || lower.equals("y");
	    }

	    // 3. Number 타입 처리 (byte, short, int, long, float, double 등)
	    // 값이 0이 아닐 경우 참으로 간주
	    if (o instanceof Number numValue) {
	        return numValue.doubleValue() != 0;
	    }

	    // 4. Collection / Map / Array 타입 처리 (값이 하나라도 존재하면 true)
	    if (o instanceof java.util.Collection<?> collection) {
	        return !collection.isEmpty();
	    }
	    
	    if (o instanceof java.util.Map<?, ?> map) {
	        return !map.isEmpty();
	    }
	    
	    // Array 타입
	    if (o.getClass().isArray()) {
	        return java.lang.reflect.Array.getLength(o) > 0;
	    }

	    // 5. 그 외 모든 Non-null 객체 (Class, Enum, Custom Object 등)는 true 반환
	    return true;
	}
	
	
	
	public static String toString(byte[] b, Charset charset) {
		if(b == null) {
			return "";
		}else {
			try {
				return new String(b, charset);
			}catch(Exception e) {
				logger.info("exception : {}",e.getMessage());
				return "";
			}
		}
	}
	
	
	public static String toString(byte[] b, String charsetName) {
		if(b == null) {
			return "";
		}else {
			try {
				return new String(b, charsetName);
			}catch(UnsupportedEncodingException e) {
				logger.info("exception : {}",e.getMessage());
				return "";
			}
		}
	}
	
	public static String toString(byte[] b, int offset , int length) {
		int len = b.length;
		if(len < offset || len == 0) {
			return "";
		}else if(len < offset + length) {
			return new String(b,offset,len);
		}else {
			return new String(b,offset,length);
		}
	}
	
	public static String toString(byte[] b, int offset , int length,String charsetName) {
		int len = b.length;
		if(len < offset || len == 0) {
			return "";
		}else if(len < offset + length) {
			try {
				return new String(b,offset,len,charsetName);
			}catch(Exception e) {
				logger.info("exception : {}",e.getMessage());
			}
		}else {
			try {
			return new String(b,offset,length,charsetName);
			}catch(Exception e) {
				logger.info("exception : {}",e.getMessage());
			}
		}
		return "";
	}
	
	
	public static String toString(byte[] b, int offset , int length,Charset charset) {
		int len = b.length;
		if(len < offset || len == 0) {
			return "";
		}else if(len < offset + length) {
			try {
				return new String(b,offset,len,charset);
			}catch(Exception e) {
				logger.info("exception : {}",e.getMessage());
			}
		}else {
			try {
			return new String(b,offset,length,charset);
			}catch(Exception e) {
				logger.info("exception : {}",e.getMessage());
			}
		}
		return "";
	}
	
	
	public static String toString(byte[] b, int offset ) {
		return toString(b,offset,b.length-offset);
	}
	
	
	public static String toStringByStream(byte[] b,String charset){
		return toStringByStream(b, 0, b.length, charset);
	}
	
	
	public static String toStringByStream(byte[] b,int offset, String charset){
		return toStringByStream(b, offset, b.length, charset);
	}

	public static String toStringByStream(byte[] b,int offset , int len , String charset){
		String s = "";
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try{
			bos.write(b, offset, len);;
			s = bos.toString(charset);
			bos.close();
		}catch(Exception e){}
		return s;
	}
	
	
	public static String toString(Object o) {
	    if (o == null) return "";

	    // 1. 문자열 / 숫자 / 불리언 등 기본형 계열
	    if (o instanceof String s) return s;
	    if (o instanceof Character c) return String.valueOf(c);
	    if (o instanceof Boolean b) return b.toString();

	    if (o instanceof Byte v) return v.toString();
	    if (o instanceof Short v) return v.toString();
	    if (o instanceof Integer v) return v.toString();
	    if (o instanceof Long v) return v.toString();
	    if (o instanceof Float v) return v.toString();
	    if (o instanceof Double v) return v.toString();
	    if (o instanceof AtomicInteger v) return Integer.toString(v.get());
	    if (o instanceof AtomicLong v) return Long.toString(v.get());

	    if (o instanceof BigInteger v) return v.toString();
	    if (o instanceof BigDecimal v) return v.toString();

	    // 2. 배열 / 버퍼
	    if (o instanceof char[] arr) return String.valueOf(arr);
	    if (o instanceof byte[] arr) return new String(arr, Charset.defaultCharset());
	    if (o instanceof String[] arr) return String.join(",", arr);
	    if (o instanceof ByteBuffer bb) {
	        ByteBuffer dup = bb.duplicate(); // position 보호
	        byte[] dst = new byte[dup.remaining()];
	        dup.get(dst);
	        return new String(dst, Charset.defaultCharset());
	    }

	    // 3. 날짜/시간 관련
	    if (o instanceof Timestamp ts) return DateUtils.toString(ts);
	    if (o instanceof java.sql.Date d) return DateUtils.toString(new Timestamp(d.getTime()));
	    if (o instanceof java.util.Date d) return DateUtils.toString(new Timestamp(d.getTime()));
	    if (o instanceof Calendar cal) return DateUtils.toString(new Timestamp(cal.getTimeInMillis()));
	    if (o instanceof LocalDate ld) return ld.toString();
	    if (o instanceof LocalDateTime ldt) return ldt.toString();
	    if (o instanceof Instant inst) return inst.toString();

	    // 4. 컬렉션 / 맵 / Enum / Optional
	    if (o instanceof Collection<?> c) {
	        return String.join(",", c.stream().map(Objects::toString).toList());
	    }
	    if (o instanceof Map<?,?> m) return m.entrySet().toString();
	    if (o instanceof Enum<?> e) return e.name();
	    if (o instanceof Optional<?> opt) return opt.map(Objects::toString).orElse("");

	    // 5. 예외 및 그 외
	    if (o instanceof Throwable t) return getExceptionMessage(t);

	    // Default
	    return o.toString();
	}

	
	
	@SuppressWarnings("unchecked")
    public static <T> T parse(Object obj, Class<T> targetClass) {
        if (obj == null) return defaultValue(targetClass);

        try {
        	Object result;
            if (obj instanceof String s) {
                result = convertFromString(s.trim(), targetClass);
            } else if (obj instanceof Number n) {
                result = convertFromNumber(n, targetClass);
            } else if (obj instanceof Boolean b) {
                result = convertFromNumber(b ? 1 : 0, targetClass);
            } else if (obj instanceof byte[] arr) {
                result = convertFromString(new String(arr, Charset.defaultCharset()).trim(), targetClass);
            } else {
                result = defaultValue(targetClass);
            }
            return (T) result;
        } catch (Exception e) {
            return defaultValue(targetClass);
        }
    }

    private static Object convertFromString(String s, Class<?> targetClass) {
        if (targetClass == Integer.class || targetClass == int.class) return Integer.parseInt(s);
        if (targetClass == Long.class || targetClass == long.class)       return Long.parseLong(s);
        if (targetClass == Double.class || targetClass == double.class)   return Double.parseDouble(s);
        if (targetClass == BigDecimal.class)                              return new BigDecimal(s);
        if (targetClass == BigInteger.class)                              return new BigInteger(s);
        return defaultValue(targetClass);
    }

    private static Object convertFromNumber(Number n, Class<?> targetClass) {
        if (targetClass == Integer.class || targetClass == int.class) return n.intValue();
        if (targetClass == Long.class || targetClass == long.class)   return n.longValue();
        if (targetClass == Double.class || targetClass == double.class) return n.doubleValue();
        if (targetClass == BigDecimal.class)                          return n instanceof BigDecimal ? n : BigDecimal.valueOf(n.doubleValue());
        if (targetClass == BigInteger.class)                          return n instanceof BigInteger ? n : BigInteger.valueOf(n.longValue());
        return defaultValue(targetClass);
    }

    @SuppressWarnings("unchecked")
    private static <T> T defaultValue(Class<T> targetClass) {
        if (targetClass == Integer.class || targetClass == int.class) return (T) Integer.valueOf(0);
        if (targetClass == Long.class || targetClass == long.class)   return (T) Long.valueOf(0L);
        if (targetClass == Double.class || targetClass == double.class) return (T) Double.valueOf(0.0);
        if (targetClass == BigDecimal.class)                          return (T) BigDecimal.ZERO;
        if (targetClass == BigInteger.class)                          return (T) BigInteger.ZERO;
        return null;
    }

    // 기존 사용하던 호환성을 위하여 
    public static int parseInt(Object obj) { return parse(obj, int.class); }
    public static long parseLong(Object obj) { return parse(obj, long.class); }
    public static double parseDouble(Object obj) { return parse(obj, double.class); }
    public static BigDecimal parseBigDecimal(Object obj) { return parse(obj, BigDecimal.class); }
    public static BigInteger parseBigInteger(Object obj) { return parse(obj, BigInteger.class); }

    public static int toInt(Object obj) { return parse(obj, int.class); }
    public static long toLong(Object obj) { return parse(obj, long.class); }
    public static double toDouble(Object obj) { return parse(obj, double.class); }
    public static BigDecimal toBigDecimal(Object obj) { return parse(obj, BigDecimal.class); }
    public static BigInteger toBigInteger(Object obj) { return parse(obj, BigInteger.class); }
	
	
	public static byte[] hexToByte(String hex) { 
		if(hex == null || hex.length() == 0) {
			return null;
		}
		byte[] b = new byte[hex.length() / 2];
		for(int i = 0; i < b.length; i++) {
			b[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16); 	
		}
		return b; 
	}
	
	public static String byteToHex(byte[] b) {
		if (b == null || b.length == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder(b.length * 2); 	
		String hex; 	
		for(int x = 0; x < b.length; x++) { 
			hex = "0" + Integer.toHexString(0xff & b[x]);  
			sb.append(hex.substring(hex.length() - 2)); 	
		}
		return sb.toString();
	}
	
	
	
	public static boolean hasEquals(Object dest, String str) {
	    if (dest == null || str == null) return false;

	    if (dest instanceof String[] arr) {
	        return Arrays.asList(arr).contains(str);
	    } else if (dest instanceof Collection<?> coll) {
	        return coll.contains(str);
	    }

	    return false; // 지원하지 않는 타입
	}

	public static boolean hasNotEquals(Object dest, String str) {
	    return !hasEquals(dest, str);
	}
	
	
	
	public static boolean startsWith(Object dest, String str) {
	    if (dest == null || str == null) return false;

	    if (dest instanceof String[] arr) {
	        return Arrays.stream(arr).anyMatch(s -> s.startsWith(str));
	    } else if (dest instanceof Collection<?> coll) {
	        return coll.stream()
	                   .filter(Objects::nonNull)
	                   .map(Object::toString)
	                   .anyMatch(s -> s.startsWith(str));
	    }

	    return false; // 지원하지 않는 타입
	}

	public static boolean isNotStartWith(Object dest, String str) {
	    return !startsWith(dest, str);
	}
	
	
	
	/**
	 * str이 dest 중 하나로 시작하는지 확인
	 * @param str 체크할 문자열
	 * @param dest 문자열 배열 또는 리스트
	 * @return 시작하면 true, 아니면 false
	 */
	public static boolean startsWith(String str, Object dest) {
	    if (str == null || dest == null) return false;

	    if (dest instanceof String[] arr) {
	        return Arrays.stream(arr).anyMatch(s -> s != null && str.startsWith(s));
	    } else if (dest instanceof Collection<?> coll) {
	        return coll.stream()
	                   .filter(Objects::nonNull)
	                   .map(Object::toString)
	                   .anyMatch(s -> str.startsWith(s));
	    }

	    return false; // 지원하지 않는 타입
	}

	/**
	 * str이 dest 중 하나로 시작하지 않는지 확인
	 */
	public static boolean isNotStartWith(String str, Object dest) {
	    return !startsWith(str, dest);
	}

	
	
	/**
     * str이 dest 중 하나로 끝나는지 확인
     * @param str 체크할 문자열
     * @param dest 문자열 배열 또는 컬렉션
     * @return 끝나면 true, 아니면 false
     */
    public static boolean endsWith(String str, Object dest) {
        if (str == null || dest == null) return false;

        if (dest instanceof String[] arr) {
            return Arrays.stream(arr)
                         .filter(Objects::nonNull)
                         .anyMatch(str::endsWith);
        } else if (dest instanceof Collection<?> coll) {
            return coll.stream()
                       .filter(Objects::nonNull)
                       .map(Object::toString)
                       .anyMatch(str::endsWith);
        }

        return false; // 지원하지 않는 타입
    }

    /**
     * str이 dest 중 하나로 끝나지 않는지 확인
     */
    public static boolean isNotEndsWith(String str, Object dest) {
        return !endsWith(str, dest);
    }
	
	
	
    /**
     * dest 중 str을 포함하는 값이 있는지 확인
     * 또는 str이 dest 중 하나를 포함하는지 확인
     * @param str 체크할 문자열
     * @param dest 문자열 배열(String[]), 컬렉션(Collection<String>) 지원
     * @return 포함되면 true, 아니면 false
     */
    public static boolean isInclude(String str, Object dest) {
        if (str == null || dest == null) return false;

        if (dest instanceof String[] arr) {
            return Arrays.stream(arr)
                         .filter(Objects::nonNull)
                         .anyMatch(str::contains);
        } else if (dest instanceof Collection<?> coll) {
            return coll.stream()
                       .filter(Objects::nonNull)
                       .map(Object::toString)
                       .anyMatch(str::contains);
        }

        return false; // 지원하지 않는 타입
    }

    /**
     * str과 dest가 포함되지 않는지 확인
     */
    public static boolean isNotInclude(String str, Object dest) {
        return !isInclude(str, dest);
    }
    
    
	
	
    /**
     * dest 중 str과 같은 값이 있는지 확인 (대소문자 구분)
     * @param str 체크할 문자열
     * @param dest 배열 또는 컬렉션 지원
     * @return 포함되면 true, 아니면 false
     */
    public static boolean isContains(String str, Object dest) {
        if (str == null || dest == null) return false;

        if (dest instanceof String[] arr) {
            return Arrays.stream(arr)
                         .filter(Objects::nonNull)
                         .anyMatch(str::equals);
        } else if (dest instanceof Collection<?> coll) {
            return coll.stream()
                       .filter(Objects::nonNull)
                       .map(Object::toString)
                       .anyMatch(str::equals);
        }
        return false;
    }

    /**
     * str이 dest에 포함되지 않는지 확인 (대소문자 구분)
     */
    public static boolean isNotContains(String str, Object dest) {
        return !isContains(str, dest);
    }

    /**
     * dest 중 str과 같은 값이 있는지 확인 (대소문자 무시)
     */
    public static boolean isContainsIgnore(String str, Object dest) {
        if (str == null || dest == null) return false;

        if (dest instanceof String[] arr) {
            return Arrays.stream(arr)
                         .filter(Objects::nonNull)
                         .anyMatch(d -> d.equalsIgnoreCase(str));
        } else if (dest instanceof Collection<?> coll) {
            return coll.stream()
                       .filter(Objects::nonNull)
                       .map(Object::toString)
                       .anyMatch(d -> d.equalsIgnoreCase(str));
        }
        return false;
    }

    /**
     * str이 dest에 포함되지 않는지 확인 (대소문자 무시)
     */
    public static boolean isNotContainsIgnore(String str, Object dest) {
        return !isContainsIgnore(str, dest);
    }
	
	
    
    
    
	
    public static String substring(String str, int start) {
        return substring(str, start, str != null ? str.length() : 0);
    }

    public static String substring(String str, int start, int end) {
        if (str == null || str.isEmpty()) {
            return "";
        }

        int length = str.length();
        start = Math.max(0, Math.min(start, length)); // start 범위 보정
        end = Math.max(start, Math.min(end, length)); // end 범위 보정

        return str.substring(start, end);
    }
	
	
	
	
		
	public static String changeCharset(String str,String charset){
		return changeCharset(str,Charset.defaultCharset().name(), charset);
	}
	
	public static String changeCharset(String str, String oldCharset, String newCharset) {
	    if (str == null || oldCharset == null || newCharset == null) return "";
	    try {
	        byte[] bytes = str.getBytes(oldCharset);
	        return new String(bytes, newCharset);
	    } catch (UnsupportedEncodingException e) {
	        // 필요시 로깅
	        logger.info(getExceptionMessage(e));
	        return "";
	    }
	}
	
	
	
	public static String rtrim(String src) {
	    return src == null ? "" : src.stripTrailing(); // JDK 11+
	}

	public static String ltrim(String src) {
	    return src == null ? "" : src.stripLeading();
	}
	
	
	
	
	
	public static String hashSHA256(String txt) {
	    if (txt == null) return "";
	    try {
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
	        byte[] hashBytes = digest.digest(txt.getBytes(StandardCharsets.UTF_8));
	        StringBuilder sb = new StringBuilder(hashBytes.length * 2);
	        for (byte b : hashBytes) {
	            sb.append(String.format("%02x", b));
	        }
	        return sb.toString();
	    } catch (NoSuchAlgorithmException e) {
	        // SHA-256은 항상 존재하므로 사실상 발생하지 않음
	        logger.error("SHA-256 not supported", e);
	        return "";
	    }
	}
	
	
	
	/**
	 * Map 계열 데이터를 URL 쿼리 문자열로 변환
	 * @param map Map 또는 Map 유사 객체
	 * @param charsetNames optional charset
	 * @return key=value&key2=value2 형태
	 */
	public static String toQueryString(Map<?, ?> map, String... charsetNames) {
	    if (map == null || map.isEmpty()) return "";
	    return map.entrySet()
	              .stream()
	              .map(entry -> URIUtils.encode(String.valueOf(entry.getKey()), charsetNames) + "="
	                          + URIUtils.encode(String.valueOf(entry.getValue()), charsetNames))
	              .collect(Collectors.joining("&"));
	}
	
	
	/**
     * URL Query String을 파싱하여 Map으로 변환
     * Key는 항상 String
     * Map<String, String> map1 = parseQueryString(query, Function.identity(), LinkedHashMap::new);
     * Map<String, Integer> map = QueryUtils.parseQueryString(
    query,
    s -> {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return 0; } // 변환 실패시 0
    },
    LinkedHashMap::new
	);
     * Map<String, String> map = parseQueryString(query,Function.identity(), HashMap::new);
     * Map<String, String> map = parseQueryString(query,Function.identity(), SharedMap::new);
     *
     * @param query       URL Query String (예: "a=1&b=2")
     * @param valueMapper value를 변환할 Function
     * @param mapSupplier Map 구현체 공급 Supplier (HashMap::new, LinkedHashMap::new 등)
     * @param charset     디코딩에 사용할 Charset (UTF-8 추천)
     * @param <V>         Value 타입
     * @param <M>         Map 구현체 타입
     * @return Map<String, V>
     */
    public static <V, M extends Map<String, V>> M parseQueryString(
            String query,
            Function<String, V> valueMapper,
            Supplier<M> mapSupplier,
            Charset charset
    ) {
        M resultMap = mapSupplier.get();
        if (query == null || query.isEmpty()) {
            return resultMap;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            try {
                String key;
                String valueStr;
                if (idx > -1) {
                    key = URLDecoder.decode(pair.substring(0, idx), charset.name());
                    valueStr = URLDecoder.decode(pair.substring(idx + 1), charset.name());
                } else {
                    key = URLDecoder.decode(pair, charset.name());
                    valueStr = "";
                }
                resultMap.put(key, valueMapper.apply(valueStr));
            } catch (Exception e) {
                // 디코딩 오류시 기본 처리: key/value 그대로 저장
                resultMap.put(pair, valueMapper.apply(""));
            }
        }
        return resultMap;
    }

    /**
     * 기본 Charset UTF-8 사용
     */
    public static <V, M extends Map<String, V>> M parseQueryString(
            String query,
            Function<String, V> valueMapper,
            Supplier<M> mapSupplier
    ) {
        return parseQueryString(query, valueMapper, mapSupplier, StandardCharsets.UTF_8);
    }
	
	
	
	/**
	 * 문자열을 byte 기준으로 지정 길이만큼 잘라 반환 (한글 포함)
	 * @param str 대상 문자열
	 * @param length 최대 byte 길이
	 * @param charset 사용할 문자셋
	 * @return byte 길이를 초과하지 않는 문자열
	 */
	public static String cut(String str, int length, Charset charset) {
	    if (str == null || str.isEmpty()) return "";

	    str = str.trim();
	    byte[] strBytes = str.getBytes(charset);
	    if (strBytes.length <= length) return str;

	    StringBuilder sb = new StringBuilder();
	    int cnt = 0;
	    for (char ch : str.toCharArray()) {
	        byte[] chBytes = String.valueOf(ch).getBytes(charset);
	        if (cnt + chBytes.length > length) break;
	        sb.append(ch);
	        cnt += chBytes.length;
	    }
	    return sb.toString();
	}

	/**
	 * 기본 Charset (환경 기본) 사용
	 */
	public static String cut(String str, int length) {
	    return cut(str, length, Charset.defaultCharset());
	}
	
	
	
	public static byte[] getBytes(String str) {
	    return getBytes(str, Charset.defaultCharset());
	}

	public static byte[] getBytes(String str, Charset charset) {
	    if (str == null) return new byte[0];
	    return str.getBytes(charset);
	}

	public static byte[] getBytes(String str, String charsetName) {
	    if (str == null) return new byte[0];
	    try {
	        return str.getBytes(charsetName);
	    } catch (UnsupportedEncodingException e) {
	        // 필요시 로깅
	        return new byte[0];
	    }
	}
	
	
	
	
	/**
	 * 특정 문자를 반복적으로 하여 리턴 
	 * @param ch
	 * @param repeat
	 * @return
	 */
	public static String repeat(final char ch, int repeat) {
		if(repeat <=0) {
			return "";
		}
		final char[] buf = new char[repeat];
		Arrays.fill(buf, ch);
		return new String(buf);
	}
	
	
	/**
	 * 문자열 길이를 반환합니다. null이면 0.
	 * @param cs 문자열
	 * @return 문자 개수
	 */
	public static int length(CharSequence cs) {
	    return cs == null ? 0 : cs.length();
	}

	/**
	 * 문자열의 바이트 길이를 반환합니다. null이면 0.
	 * @param cs 문자열
	 * @param charset 바이트 변환에 사용할 Charset
	 * @return 바이트 길이
	 */
	public static int byteLength(CharSequence cs, Charset charset) {
	    if (cs == null) return 0;
	    return cs.toString().getBytes(charset).length;
	}

	/**
	 * 문자열의 바이트 길이를 반환합니다. null이면 0.
	 * @param cs 문자열
	 * @param charsetName 바이트 변환에 사용할 Charset 이름
	 * @return 바이트 길이
	 */
	public static int byteLength(CharSequence cs, String charsetName) {
	    if (cs == null) return 0;
	    try {
	        return cs.toString().getBytes(charsetName).length;
	    } catch (UnsupportedEncodingException e) {
	        // 필요 시 로깅
	        return 0;
	    }
	}
	
	
	
	
	/**
	 * 문자열의 첫 글자를 대문자로 변경. surrogate pair도 처리.
	 */
	public static String capitalize(String str) {
	    if (isEmpty(str)) return "";

	    int firstCodePoint = str.codePointAt(0);
	    int newCodePoint = Character.toTitleCase(firstCodePoint);

	    if (firstCodePoint == newCodePoint) return str;

	    int codePointCount = str.codePointCount(0, str.length());
	    int[] newCodePoints = new int[codePointCount];
	    int outOffset = 0;
	    newCodePoints[outOffset++] = newCodePoint;

	    for (int inOffset = Character.charCount(firstCodePoint); inOffset < str.length(); ) {
	        int cp = str.codePointAt(inOffset);
	        newCodePoints[outOffset++] = cp;
	        inOffset += Character.charCount(cp);
	    }

	    return new String(newCodePoints, 0, outOffset);
	}

	/**
	 * 문자열에서 모든 이모지 제거.
	 */
	public static String removeEmoji(String str) {
	    if (isEmpty(str)) return "";

	    // 이모지 및 특수 유니코드 블록 제거 (emoticons, symbols, pictographs 등)
	    return str.replaceAll("[\\p{So}\\p{Cn}\\p{Cs}]", "");
	}
	
	
	
	
	/**
	 * keys 중 map에 없는 키 목록 반환
	 */
	public static <K, V> List<K> findEmptyKeys(Map<K, V> map, List<K> keys) {
	    List<K> emptyKeys = new ArrayList<>();
	    if (map == null || map.isEmpty() || keys == null || keys.isEmpty()) {
	        return emptyKeys;
	    }
	    for (K key : keys) {
	        if (!map.containsKey(key)) {
	            emptyKeys.add(key);
	        }
	    }
	    return emptyKeys;
	}

	/**
	 * keys 중 map에 없는 첫 번째 키 반환 (없으면 null)
	 */
	public static <K, V> K findEmptyKey(Map<K, V> map, List<K> keys) {
	    List<K> emptyKeys = findEmptyKeys(map, keys);
	    return emptyKeys.isEmpty() ? null : emptyKeys.get(0);
	}
	
	
	
	
	
	/**
	 * keys 중 map에서 값이 없거나 null/빈 문자열인 항목 반환
	 */
	public static <K, V> List<K> findEmptyValues(Map<K, V> map, List<K> keys) {
	    List<K> emptyKeys = new ArrayList<>();
	    if (map == null || map.isEmpty() || keys == null || keys.isEmpty()) {
	        return emptyKeys;
	    }

	    for (K key : keys) {
	        if (!map.containsKey(key)) {
	            emptyKeys.add(key);
	        } else {
	            Object value = map.get(key);
	            if (value == null || (value instanceof CharSequence cs && cs.toString().trim().isEmpty())) {
	                emptyKeys.add(key);
	            }
	        }
	    }
	    return emptyKeys;
	}

	/**
	 * keys 중 map에서 값이 없거나 null/빈 문자열인 항목이 하나라도 있으면 true
	 */
	public static <K, V> boolean isEmptyValues(Map<K, V> map, List<K> keys) {
	    return !findEmptyValues(map, keys).isEmpty();
	}

	/**
	 * keys 중 map에서 값이 없거나 null/빈 문자열인 항목이 없으면 true
	 */
	public static <K, V> boolean isNotEmptyValues(Map<K, V> map, List<K> keys) {
	    return findEmptyValues(map, keys).isEmpty();
	}

	
	
	public static <T> String getEmptyField(T obj, List<String> keys) {
	    List<String> emptyFields = getEmptyFields(obj, keys);
	    return emptyFields.isEmpty() ? null : emptyFields.get(0);
	}

	public static <T> List<String> getEmptyFields(T obj, List<String> keys) {
	    List<String> emptyFields = new ArrayList<>();
	    if (obj == null || keys == null || keys.isEmpty()) return emptyFields;

	    Class<?> clazz = obj.getClass();
	    for (String key : keys) {
	        try {
	            Field field = getFieldRecursive(clazz, key);
	            if (field == null) continue;

	            field.setAccessible(true);
	            Object value = field.get(obj);

	            if (value == null || isEmptyValue(value)) {
	                emptyFields.add(key);
	            }
	        } catch (Exception e) {
	            // 필드별 예외는 로그만 남기고 계속 진행
	            logger.info("Reflect error for field '{}': {}", key, getExceptionMessage(e));
	        }
	    }
	    return emptyFields;
	}

	/**
	 * 상위 클래스까지 포함해서 필드 탐색
	 */
	private static Field getFieldRecursive(Class<?> clazz, String fieldName) {
	    while (clazz != null) {
	        try {
	            return clazz.getDeclaredField(fieldName);
	        } catch (NoSuchFieldException e) {
	            clazz = clazz.getSuperclass();
	        }
	    }
	    return null;
	}

	/**
	 * 값이 비어있는지 확인
	 */
	private static boolean isEmptyValue(Object value) {
	    if (value == null) return true;
	    if (value instanceof Number n) return n.longValue() == 0;
	    if (value instanceof CharSequence cs) return cs.toString().trim().isEmpty();
	    if (value instanceof Collection<?> c) return c.isEmpty();
	    if (value instanceof Map<?, ?> m) return m.isEmpty();
	    if (value.getClass().isArray()) return Array.getLength(value) == 0;
	    return false;
	}
	
	
	// System Property가 없으면 기본값 설정
    public static void setSystemPropertyIfAbsent(String key, String defaultValue) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, defaultValue);
        }
    }
    
    /**
     * 두 객체의 값을 타입에 상관없이 비교
     * @param a
     * @param b
     * @return true : 값이 같음, false : 값이 다름
     */
    public static boolean equals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        if (a instanceof Number && b instanceof Number) {
            double da = ((Number) a).doubleValue();
            double db = ((Number) b).doubleValue();
            return da == db;
        }

        return a.toString().equals(b.toString());
    }

    /**
     * 두 객체를 문자열로 비교, 대소문자 무시
     * @param a
     * @param b
     * @return true : 값이 같음, false : 값이 다름
     */
    public static boolean equalsIgnoreCase(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        return a.toString().equalsIgnoreCase(b.toString());
    }
    

}
