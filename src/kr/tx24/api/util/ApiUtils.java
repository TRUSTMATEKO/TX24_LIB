package kr.tx24.api.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import kr.tx24.api.conf.ApiConfigLoader;
import kr.tx24.api.conf.HandlerConfig;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lang.URIUtils;
import kr.tx24.lib.map.LinkedMap;


public class ApiUtils {
	private static Logger logger = LoggerFactory.getLogger(ApiUtils.class);
	
	public ApiUtils() {
		// TODO Auto-generated constructor stub
	}
	
	public static String getExtractPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "/";
        }
        
        // Query string 제거 (?로 시작)
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            uri = uri.substring(0, queryIndex);
        }
        
        // Fragment 제거 (#으로 시작)
        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex > 0) {
            uri = uri.substring(0, fragmentIndex);
        }
        
        return uri;
    }
	
	
	
	/**
     * 설정 리스트를 읽어서 파이프라인에 핸들러 추가
     * 
     * @param pipeline ChannelPipeline
     * @param handlerConfigs 핸들러 설정 리스트
     */
    public static void addHandlers(ChannelPipeline pipeline, List<HandlerConfig> handlerConfigs) {
        if (handlerConfigs == null || handlerConfigs.isEmpty()) {
            logger.warn("No handlers to add");
            return;
        }
        
        for (HandlerConfig config : handlerConfigs) {
            try {
                // 클래스 로드
                Class<?> clazz = Class.forName(config.className);
                
                // 인스턴스 생성
                Object instance = createInstance(clazz, config.params);
                
                // 파이프라인에 추가
                if (instance instanceof ChannelHandler) {
                    pipeline.addLast(config.name, (ChannelHandler) instance);
                    logger.info("Added handler: {} ({})", config.name, config.className);
                } else {
                    logger.error("Not a ChannelHandler: {}", config.className);
                }
                
            } catch (Exception e) {
                logger.error("Failed to add handler: {} - {}", config.name, e.getMessage());
            }
        }
    }
    
    /**
     * 인스턴스 생성 (리플렉션)
     */
    private static Object createInstance(Class<?> clazz, kr.tx24.lib.map.LinkedMap<String, Object> params) throws Exception {
        
        // 파라미터가 없으면 기본 생성자
        if (params == null || params.isEmpty()) {
            return clazz.getDeclaredConstructor().newInstance();
        }
        
        // 파라미터가 있으면 파라미터 개수에 맞는 생성자 찾기
        Object[] args = params.values().toArray();
        Class<?>[] argTypes = new Class[args.length];
        
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = getType(args[i]);
        }
        
        try {
            return clazz.getConstructor(argTypes).newInstance(args);
        } catch (NoSuchMethodException e) {
            // 매칭되는 생성자가 없으면 기본 생성자
            logger.warn("No matching constructor, using default for: {}", clazz.getName());
            return clazz.getDeclaredConstructor().newInstance();
        }
    }
    
    /**
     * Object의 타입 추론
     */
    private static Class<?> getType(Object obj) {
        if (obj instanceof Integer) return int.class;
        if (obj instanceof Long) return long.class;
        if (obj instanceof Double) return double.class;
        if (obj instanceof Boolean) return boolean.class;
        if (obj instanceof String) return String.class;
        return obj.getClass();
    }
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public static String getMimeType(String uri){
		String s =  "";
		int pos = uri.lastIndexOf(".");
		if(pos > -1){
			s =  uri.substring(pos);
		}
		
		s = ApiConstants.HTTP_MIME_TYPE.get(s.toLowerCase());
		if(s == null){
			s = "text/html"; //"application/octet-stream"
		}
		return s;
			
	}
	
	
	
	
	

	
	public static HttpResponseStatus getHttpResponse(int code,String message){
		return new HttpResponseStatus(code, message);
	}
	
	
	
	public static String getRemoteAddress(Channel channel) {
		SocketAddress remoteSocketAddr = channel.remoteAddress();
		if (null != remoteSocketAddr && InetSocketAddress.class.isAssignableFrom(remoteSocketAddr.getClass())) {
			InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteSocketAddr;
			if (inetSocketAddress.getAddress() != null) {
				InetAddress address = inetSocketAddress.getAddress();
				if (address instanceof Inet6Address) {
					try {
						return InetAddress.getByAddress(address.getAddress()).getHostAddress();
					}catch (UnknownHostException e) {
						throw new RuntimeException(e);
					}
				}else if (address instanceof Inet4Address) {
					return address.getHostAddress();
				}else {
					return "";
				}
			}
		}
		return "";
	}
	
	
	public static void printMap(LinkedMap<String,Object> requestMap){
		if(SystemUtils.deepview()){
			for(String key : requestMap.keySet()){
				if(!(key.equals(ApiConstants.CTX)|| key.equals(ApiConstants.PAYLOAD_BUF) || key.equals(ApiConstants.START_TIME) )){
					logger.info("{}   : {}",String.format("%14s",key),requestMap.getString(key));
				}
			}
		}
		
	}
	
	public static String toString(ByteBuf byteBuf,String charset){
		return CommonUtils.toString(toBytes(byteBuf),charset).trim();
	}
	
	public static String toString(ByteBuf byteBuf){
		return toString(byteBuf,"utf-8");
	}
	
	
	public static byte[] toBytes(ByteBuf byteBuf){
		if(byteBuf == null){
			return null;
		}else{
			
			byte[] b = null;
			if(byteBuf.hasArray()){
				b = byteBuf.array();
			} else {
				int len = byteBuf.readableBytes();
				b = new byte[len];
				byteBuf.readBytes(b);
			}
			return b;
		}
	}
	
	
	public static List<String> getParameterArgs(String uri){
		if(CommonUtils.isEmpty(uri)){
			return new ArrayList<String>();
		}
		
		if(uri.startsWith("/")){
			uri = uri.substring(1);
		}
		
		return Arrays.asList(uri.split("/"));
	}
	
	
	
	
	
	
	
	public static String getUri(String uri){
		if(CommonUtils.isEmpty(uri)){
			return "/";
		}
		
		if(!uri.startsWith("/")){
			uri = "/"+uri;
		}
		
		int idx = uri.indexOf("?");
		if(idx > 1){
			uri =  uri.substring(0,idx);
		}
		
		if(uri.endsWith("/")){
			uri = uri.substring(0,uri.length()-1);
		}
		
		return uri;
		
	}
	
	
	
	
	
	
	public static String getSubUri(String uri,String remove){
		String subUri	 = uri.replaceAll(remove,"");
		if(subUri.endsWith("/")){
			subUri = subUri.substring(0,subUri.length()-1);
		}
		return subUri;
	}
	
	public static String mergeList(List<String> uri,int merge){
		StringBuilder sb = new StringBuilder();
		if(uri == null){
			uri = new ArrayList<String>();
		}
		
		if(uri.size() >= merge){
			for(int i=0;i<merge;i++){
				sb.append("/").append(uri.get(i).trim());
			}
		}else{
			for(int i=0;i<uri.size();i++){
				sb.append("/").append(uri.get(i).trim());
			}
			
		}
		return sb.toString();
	}
	
	public static String mergeExclude(List<String> uri,int... exclude){
		StringBuilder sb = new StringBuilder();
		if(uri == null){
			uri = new ArrayList<String>();
		}
		
		for(int i=0 ; i < uri.size() ; i++){
			if(Arrays.binarySearch(exclude,i) < 0){
				sb.append("/").append(uri.get(i).trim());
			}
		}
		return sb.toString();
	}
	
	
	public static boolean hasStartsWith(List<String> list,String str){
		if(list == null){
			return false;
		}
		for(String s: list){
			if(str.startsWith(s) && !s.equals("")){
				return true;
			}
		}
		return false;
	}
	
	public static String getSafety(List<String> list, int index){
		if(list == null || list.size() == 0){
			return "";
		}
		if(list.size() < index){
			return "";
		}else{
			String s = "";
			try{
				s = CommonUtils.nToB(list.get(index));
			}catch(Exception e){}
			return s;
		}
	}
	
	
	public synchronized static String getMD5(String value){
		String MD5 = "";
		try{
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(value.getBytes());
			byte[] data = md.digest();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < data.length; i++) {
				sb.append(Integer.toString((data[i] & 0xff) + 0x100, 16).substring(1));
			}
			MD5 = sb.toString().toUpperCase();
		}catch(NoSuchAlgorithmException e){}
		
		
		return MD5;
	}
	
	
	public static String decodeBase64(String s){
		try{
		return new String(Base64.getDecoder().decode(s),"utf-8");
		}catch(Exception e){
			return s;
		}
	}
	
	

	
	
	public static int generateRandomDigit(){
		java.util.Random generator = new java.util.Random();
		generator.setSeed(System.currentTimeMillis());
		return generator.nextInt(1000000) % 1000000;
	}
	
	
	public static String generateTrxToken(){
		String tk = CommonUtils.toString(System.currentTimeMillis());
		return tk+CommonUtils.paddingZero(new java.util.Random().nextInt(10000),3);
	}
	
	
	
	public static <T> T fromJson(String payload,TypeReference<T> typeOfT) throws Exception{
		ObjectMapper mapper = new ObjectMapper();
		mapper.setDefaultPropertyInclusion( Include.NON_NULL);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		return mapper.readValue(payload, typeOfT);
	}
	
	
	public static <T> T fromJson(String payload,Class<T> valueType) throws Exception{
		ObjectMapper mapper = new ObjectMapper();
		mapper.setDefaultPropertyInclusion( Include.NON_NULL);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		
		return mapper.readValue(payload, valueType);
	}

	
	
	
	public static String toQueryString(LinkedMap<String,Object> param){
		StringBuilder sb = new StringBuilder();
		try{
			
			if(param.size() !=0){
				int i=0;
				for (Entry<String, Object> entry :param.entrySet()) {
					sb.append(entry.getKey()).append("=").append(URIUtils.encode(CommonUtils.toString(entry.getValue()),"utf-8"));
					
					i++;
					if(param.size() != i){
						sb.append("&");
					}
		        }
			}
		}catch(Exception e){}
		return sb.toString();
	}
	
	
	
	public static String getAcceptLanguage(HttpHeaders header){
		String language = CommonUtils.nToB(header.get(HttpHeaderNames.ACCEPT_LANGUAGE));
		if(language.equals("") || language.indexOf("ko-") > -1){
			return "ko";
		}else{
			return "en";
		}
	}
	
	public static String convertByte(long size){
		
		try{
			double kb = size / 1024;
			double mb = size / 1024 / 1024;
			double gb = size / 1024 / 1024 / 1024;
			
			if (gb > 0){
				return  String.format("%.1f GB",gb) ;
			}else if(mb > 0){
				return  String.format("%.1f MB",mb) ;
			}else if(kb > 0){
				return  String.format("%.1f KB",kb) ;
			}else{
				return size + " B";
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return "";
			
	}
	
	public static boolean isDeny(String uri){
		String uris = getUri(uri).toLowerCase();
		
		if(ApiConfigLoader.env.denyUris != null){
			for(String deny : ApiConfigLoader.env.denyUris){
				if(uris.startsWith(deny.toLowerCase())){
					logger.info("deny uri : {}",uris);
					return true;
				}
			}
		}
		
		
		if(ApiConfigLoader.env.denyExts != null){
			String ext = "";
			int pos = uri.lastIndexOf(".");
			if(pos > -1){
				ext =  uri.substring(pos).toLowerCase();
			}
			if(!ext.equals("") && ApiConfigLoader.env.denyExts.contains(ext.toLowerCase())){
				logger.info("deny extention : {}",ext);
				return true;
			}
		}
		
		
		return false;
	}
	
	
	public static boolean isStaticExtenstion(String uri){
		String ext =  "";
		int pos = uri.lastIndexOf(".");
		if(pos > -1){
			ext =  uri.substring(pos).toLowerCase();
		}
		
		if(!ext.equals("") && ApiConstants.HTTP_MIME_TYPE.containsKey(ext.toLowerCase())){
			return true;
		}else{
			return false;
		}
	}

	

	public static String getCharset(String contentType){
		if(!CommonUtils.isEmpty(contentType)){
			if(contentType.indexOf("=") > 0 && contentType.toLowerCase().indexOf("charset") > -1){
				String[] ctype = contentType.split("=");
				if(ctype.length == 2){	
					return ctype[1];
				}
			}
		}
		
		return Charset.defaultCharset().toString();
		
	}
	
	
	public static String toString(Throwable cause)throws java.lang.Exception {
		StringBuilder sb = new StringBuilder();
		StackTraceElement[] trace =  cause.getStackTrace();
				
		if(trace != null && trace.length > 0){
			int depth = trace.length > 10 ? 10 : trace.length;
			sb.append(cause.getMessage());
			for(int i = 0; i < depth; i++ ) {
				sb.append(System.getProperty("line.separator"))
				.append(trace[i].toString());
			}
		}
		return sb.toString();
		
	}
	
	
	

	public static LinkedMap<String,String> setQueryString(String parameter,String charset){
		LinkedMap<String,String> map = new LinkedMap<String,String>();
		try{
			String[] paramArray = parameter.split("&");
	        for (String param : paramArray) {
	            String[] keyValue = param.split("=");
	            if (keyValue.length == 2) {
	                String key = URLDecoder.decode(keyValue[0], charset);
	                String value = URLDecoder.decode(keyValue[1], charset);
	                map.put(key, value);
	            }
	        }
		}catch(Exception e){}
		return map;
	}
	
	
	
	
	
	
	
	

}
