package kr.tx24.lib.logback;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import kr.tx24.lib.lang.CommonUtils;

/**
 * */
public class MaskConverter extends ClassicConverter {
	
	 
	private static final String[] keyword 		= new String[]{"rrn","phone","email","name","cvv","cvc","passwd","password","card","number","account","telno","brn","mchtBRN","pw","ownerRrn"};
	private static final Pattern JSON_PATTERN 	= Pattern.compile("(?:\\\"|\\')([^\"]*)(?:\\\"|\\')(?:\\s*)(?=:)(?:\\:\\s*)(?:\\\")?(true|false|[-0-9]+[\\.]*[\\d]*(?=,)|[0-9a-zA-Z가-힣\\(\\)\\@\\:\\,\\/\\!\\+\\-\\.\\$\\ \\\\\\']*)(?:\\\")?");
	private static final String LAST6_REGEX 	= "(.{6}$)";
	private static final int DEVIDE_LEN			= 4;
	
	private static List<String> custom		= new ArrayList<String>();
	
	
	@Override
	public String convert(ILoggingEvent iLoggingEvent) {
		String message = iLoggingEvent.getFormattedMessage();
		if(CommonUtils.isNullOrSpace(message)) {
			return "";
		}else {
			return filter(message);
		}
	}
	
	private static String filter(String message){
		Matcher match = JSON_PATTERN.matcher(message);
		StringBuilder sb = new StringBuilder(message.length()); 
		try{
			while (match.find()) {
				if(match.groupCount() == 2){
					if(!CommonUtils.isNullOrSpace(match.group(2))){
						String t = mask(match.group(1), match.group(2));
						if (!t.equals(match.group(2))) {
							String endStr = "";
							if(match.group(0).endsWith("\"")) {
								endStr = "\"";
							}
							String replacement = "\""+match.group(1)+"\": \""+t+endStr;
							match.appendReplacement(sb, Matcher.quoteReplacement(replacement)); 
						}
					}
				}
			}
			match.appendTail(sb);
		}catch(Exception e){
			System.out.println(e.getMessage());
		}

		return sb.toString();
	}
	
	
	public static String mask(String name,String value){
		value = CommonUtils.isNull(value,"").trim();
		String lower = CommonUtils.isNull(name,"").toLowerCase().trim();
		
		if(lower.indexOf(keyword[0]) > -1 || lower.indexOf(keyword[10]) > -1 || lower.indexOf(keyword[15]) > -1){			//rrn, account, ownerRrn
			return value.replaceAll(LAST6_REGEX, "*".repeat(6)); // String.repeat() 적용
		}else if(lower.indexOf(keyword[1]) > -1 || lower.indexOf(keyword[11]) > -1){	//phone , telno
			if( value.length() > 6 ){	return value.substring(0,3)+"****"+value.substring(7);
			}else{	return value;}
		}else if(lower.indexOf(keyword[2]) > -1){										//email
			return maskEmail(value);
		}else if(lower.indexOf(keyword[3]) == 0){										//name
			return nameMasking(value);
		}else if(lower.indexOf(keyword[4]) > -1 || lower.indexOf(keyword[5]) > -1){		//cvv,cvc
			return fill(value,0,value.length());
		}else if(lower.indexOf(keyword[6]) > -1 || lower.indexOf(keyword[7]) > -1 || lower.indexOf(keyword[14]) > -1){		//passwd,password
			return fill(value,0,value.length());
		}else if(lower.indexOf(keyword[8]) > -1 || lower.indexOf(keyword[9]) > -1){		//card,number
			return fill(value,6,value.length());
		}else{
			if(MaskConverter.custom.size() > 0){
				for(String key : MaskConverter.custom){
					if(lower.indexOf(key) > -1){
						return fill(value,6,value.length());
					}
				}
				
			}
			return value;
		}
	}
	
	public static String maskEmail(String email){
		try{
			String[] t = email.split("[@]");
			if(t.length != 2){
				return email;
			}
			
			int len = t[0].length();
			StringBuilder sb = new StringBuilder(email.length()); // StringBuilder 및 capacity 최적화
		
			int mask = 0;
			if(len <= 2){
				mask = 1;
				sb.append(t[0].substring(0,len - mask));
				sb.append("*");
				
			}else if(len > 2 && len <= 5){
				mask = 2;
				sb.append(t[0].substring(0,len - mask));
				sb.append("*".repeat(mask)); 
			}else {
				sb.append(t[0].substring(0,len -4 -1))
				  .append("*".repeat(4))
				  .append(t[0].substring(len - 1));
			}
			
			
			sb.append("@");
			sb.append(t[1]);
			return sb.toString();
		}catch(Exception e){
			return "";
		}
	}
	
	
	private static String fill(String value , int init , int len){
	    if (value.length() <= init) {
	        return value;
	    }
	    
	    int maskLen = len - init;
	    
	    if (maskLen <= 0) {
	        return value.substring(0, init);
	    }
	    
	    String mask = "*".repeat(maskLen);
	    
	    return new StringBuilder(value.length()) 
	        .append(value.substring(0, init))
	        .append(mask)
	        .toString();
	}
	
	
	
	public static String nameMasking(String name){
		int len = name.length();
		if(len < 2){
			return name;
		}else if(len == 2){
			return name.substring(0,1)+"*";
		}else if(len == 3){
			return name.substring(0,1)+"*"+name.substring(2,len);
		}else{
			int quot = len / DEVIDE_LEN;
			StringBuilder sb =new StringBuilder(len); 
			for(int i=0 ; i < quot; i++){
				if(quot -1 == i){
					if(i ==0){
						sb.append(name.substring(0,2)).append("*").append(name.substring(3,DEVIDE_LEN));
					}else{
						sb.append(name.substring(i*DEVIDE_LEN));
					}
				}else{
					sb.append(name.substring(i*DEVIDE_LEN,(i*DEVIDE_LEN)+3)).append("*");
				}
			}
			return sb.toString();
		}
	}
	
	

}