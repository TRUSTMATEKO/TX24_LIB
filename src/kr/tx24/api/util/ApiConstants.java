package kr.tx24.api.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.netty.util.AttributeKey;
import mik.proc.http.bean.Env;

public class ApiConstants {
	
	public static final String FAVICON_ICO 				= "/favicon.ico";
	public static final int HUGE_LIMIT					= 10*1024*1024;
	public static final int COMPRESSION_THRESHOLD		 = 2 * 1024;  // 2KB = 2048 bytes
	public static final AttributeKey<String> KEY_LOG 	= AttributeKey.valueOf("log");
    public static final AttributeKey<Long> KEY_START 	= AttributeKey.valueOf("startTime");
    
	
    
    public static final Set<String> DENIED_EXTENSIONS = Set.of(
            // 서버 스크립트
            ".jsp", ".php", ".asp", ".aspx", ".cgi", ".pl", ".py", ".rb",
            ".php3", ".php4", ".php5", ".phtml",
            
            // 실행 파일
            ".exe", ".sh", ".bat", ".cmd", ".com", ".msi",
            
            // 설정/민감 파일
            ".env", ".config", ".ini", ".conf", ".properties", 
            ".xml", ".yml", ".yaml", ".json",
            
            // 백업/임시 파일
            ".bak", ".old", ".tmp", ".temp", ".swp", ".log",
            ".sql", ".db", ".sqlite", ".dump", ".sqlitedb",
            
            // 버전 관리
            ".git", ".svn", ".hg", ".DS_Store",
            
            // 압축 파일
            ".zip", ".rar", ".tar", ".gz", ".7z",
            
            // 데이터베이스
            ".mdb", ".accdb"
        );
    
    // 허용된 Content-Type 목록
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "application/json",
        "application/xml",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
        "text/plain"
    );
    
    
    
    
	
	public static final String ID						= "id";
	public static final String URI						= "uri";
	public static final String HOST						= "host";
	public static final String FUNCTIONS				= "functions";
	public static final String REMOTE_IP				= "remoteIp";
	public static final String CONTENT_TYPE				= "contentType";
	public static final String USER_AGENT				= "userAgent";
	public static final String CONTENT_LENGTH			= "contentLength";
	public static final String ACCEPT_LANGUAGE			= "language";
	public static final String AUTHORIZATION			= "authorization";
	public static final String HTTP_METHOD				= "method";
	public static final String KEEP_ALIVE				= "keepAlive";
	
	public static final String PAYLOAD					= "payload";
	public static final String PAYLOAD_MAP				= "payloadMap";
	public static final String PAYLOAD_BUF				= "payloadBuf";
	public static final String CTX						= "ctx";
	public static final String START_TIME				= "startTime"; 
	public static final String CURRENT_DAY				= "currentDay";
	public static final String CURRENT_TIME				= "currentTime";
	public static final String SESSION					= "session";
	
	
	public static final String CONTENT_TYPE_HTML		= "text/html;charset=utf-8";
	public static final String CONTENT_TYPE_TEXT		= "text/plain;charset=utf-8";
	public static final String CONTENT_TYPE_JSON		= "application/json;charset=utf-8";
	public static final String DEFAULT_CHARSET			= "utf-8";
	
	public static final String X_REAL_IP 				= "X-Real-IP";
	public static final String X_FORWARDED_FOR			= "X-Forwarded-For";
	
	

	public static boolean STATIC_CACHE					= false;
	public static final CacheMap staticMap				= new CacheMap(15);	//15분
	

	
	
	public static String[] INCLUDE_ZONE					= {"192.168.10."};

	public static final int MINUTES	       				= 60 * 1000;
    public static final int HOURS		        		= 60 * 60 * 1000;
	public static final int DAYS		        		= 24 * 60 * 60 * 1000;

	
	public static final Map<String,String> HTTP_MIME_TYPE;
	static{
		
		HTTP_MIME_TYPE = new HashMap<String,String>();
		HTTP_MIME_TYPE.put(".aac"	,"audio/aac");
		HTTP_MIME_TYPE.put(".bin"	,"application/octet-stream; charset=utf-8");
		HTTP_MIME_TYPE.put(".css"	,"text/css");
		HTTP_MIME_TYPE.put(".csv"	,"text/csv");
		HTTP_MIME_TYPE.put(".gif"	,"image/gif");
		HTTP_MIME_TYPE.put(".png"	,"image/png");
		HTTP_MIME_TYPE.put(".htm"	,"text/html; charset=utf-8");
		HTTP_MIME_TYPE.put(".html"	,"text/html; charset=utf-8");
		HTTP_MIME_TYPE.put(".ico"	,"image/x-icon");
		HTTP_MIME_TYPE.put(".jpeg"	,"image/jpeg");
		HTTP_MIME_TYPE.put(".jpg"	,"image/jpeg");
		HTTP_MIME_TYPE.put(".js"	,"application/js; charset=utf-8");
		HTTP_MIME_TYPE.put(".json"	,"application/json; charset=utf-8");
		HTTP_MIME_TYPE.put(".pdf"	,"application/pdf");
		HTTP_MIME_TYPE.put(".ppt"	,"application/vnd.ms-powerpoint");
		HTTP_MIME_TYPE.put(".rtf"	,"application/rtf");
		HTTP_MIME_TYPE.put(".svg"	,"image/svg+xml");
		HTTP_MIME_TYPE.put(".swf"	,"application/x-shockwave-flash");
		HTTP_MIME_TYPE.put(".tar"	,"application/x-tar");
		HTTP_MIME_TYPE.put(".tif"	,"image/tiff");
		HTTP_MIME_TYPE.put(".tiff"	,"image/tiff");
		HTTP_MIME_TYPE.put(".ttf"	,"application/x-font-ttf");
		HTTP_MIME_TYPE.put(".woff"	,"application/x-font-woff");
		HTTP_MIME_TYPE.put(".xhtml"	,"application/xhtml+xml; charset=utf-8");
		HTTP_MIME_TYPE.put(".xml"	,"application/xml; charset=utf-8");
		HTTP_MIME_TYPE.put(".zip"	,"application/zip");
		HTTP_MIME_TYPE.put(""		,"text/html; charset=utf-8");
		HTTP_MIME_TYPE.put(".txt"	,"text/plain; charset=utf-8");
		HTTP_MIME_TYPE.put(".map"	,"application/json; charset=utf-8");
		
	}

	
	
	
	
	
	

	

	
}
