package kr.tx24.was.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import kr.tx24.lib.lang.CommonUtils;

/**
 * @author juseop
 *
 */
public class Was {

	public static final List<String> EXCLUDE_PREFIXS = Arrays.asList("/static/","/upload/","/assets/","/axios/","/dashboard/","/otp/");
	public static final List<String> EXCLUDE_SUFFIXS = Arrays.asList(".js", ".css", ".jpg", ".gif", ".png",".eot",".svg",".ttf",".woff",".woff2",".otf",".ico",".zip",".map");
	public static final String COMPRESSABLE	= "text/html,text/xml,text/plain,application/javascript,application/json,text/css,text/csv,application/x-javascript,application/vnd.api+json,image/svg+xml";
	public static final List<String> PRIVATE_NETWORK = Arrays.asList("10","192.168.","172.16.");
	public static final Charset DEFAULT_CHARSET= StandardCharsets.UTF_8;
	
	public static final String X_FORWARDED_FOR			= "X-Forwarded-For";
	public static final String X_REAL_IP				= "X-Real-IP";
	public static final String USER_AGENT_MAP			= "userAgentMap";
	public static final String REMOTE_IP				= "remoteIp";
	public static final String FILE_MAP					= "fileMap";
	
	public static final int SESSION_EXPIRE				= 30*60; // 배포 환경
	public static final String SESSION_ID				= "XSESSION";
	public static final int TOKEN_EXPIRE_MINUTE			= 30;
	public static final String LANG_EN					= "en";
	public static final String LANG_JA					= "ja";
	public static final String LANG_ZH					= "zh";
	public static final String LANG_KO					= "ko";
	public static final String[] QUALYS_SCAN			=  {"64.39","139.87.112."};
	

	
	public static String getRemoteIp(HttpServletRequest request) {
		
		String ip = request.getHeader(X_FORWARDED_FOR);
		if(CommonUtils.isEmpty(ip)){
			ip = request.getHeader(X_REAL_IP);
		}
		if(CommonUtils.isEmpty(ip)){
			ip = request.getRemoteAddr();
		}		
		return ip;
		
	}
	
	
	public static String getBody(HttpServletRequest request)throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = null;
 
		try {
			InputStream inputStream = request.getInputStream();
			if (inputStream != null) {
				br = new BufferedReader(new InputStreamReader(inputStream));
				char[] charBuffer = new char[128];
				int bytesRead = -1;
				while ((bytesRead = br.read(charBuffer)) > 0) {
					sb.append(charBuffer, 0, bytesRead);
				}
			}
		} catch (IOException ex) {
			throw ex;
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException ex) {
					throw ex;
				}
			}
		}
 
		return sb.toString();
	}
	
	
	public static String getHeader(HttpServletRequest request,String name) {
		return CommonUtils.nToB(request.getHeader(name)).trim();
	}
	
	
	public static String getAcceptLanguage(HttpServletRequest request){
		String lang = Was.getHeader(request,"Accept-Language");
		if(lang.equals("")){
			return "ko";
		}else{
			if(lang.indexOf(",") > 0){	lang = lang.split(",")[0]; }
			if(lang.indexOf("en") > -1){
				return "en";
			}else if(lang.indexOf("ja") > -1){
				return "ja";
			}else if(lang.indexOf("zh") > -1){
				return "zh";
			}else{
				return "ko";
			}
		}
	}
	
	
	
	public static boolean isQualysScan(String remoteIp){
		for(String qualys : QUALYS_SCAN){
			if(remoteIp.startsWith(qualys)){
				return true;
			}
		}
		return false;
	}
	
	
	public static String changeCharset(String str, String charset) {
		try {
			byte[] bytes = str.getBytes(charset);
			return new String(bytes, charset);
		} catch(UnsupportedEncodingException e) { }
		return "";
	}
	
	

	

}
