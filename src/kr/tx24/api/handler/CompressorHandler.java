package kr.tx24.api.handler;

import java.util.Set;

import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;

public class CompressorHandler extends HttpContentCompressor {
	
	// 압축 제외 MIME 타입
	private static final Set<String> EXCLUDED_MIME_TYPES = Set.of(
        // 이미지
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
        "image/avif", "image/heic", "image/heif", "image/jxl",
        
        // 비디오
        "video/mp4", "video/mpeg", "video/webm", "video/ogg",
        "video/quicktime", "video/x-msvideo", "video/x-flv",
        "video/3gpp", "video/h264", "video/h265",
        
        // 오디오
        "audio/mpeg", "audio/mp3", "audio/mp4", "audio/ogg",
        "audio/webm", "audio/aac", "audio/flac", "audio/wav",
        "audio/x-m4a",
        
        // 압축 파일
        "application/zip", "application/x-zip-compressed",
        "application/gzip", "application/x-gzip",
        "application/x-compressed",
        "application/x-7z-compressed", "application/x-rar-compressed",
        "application/x-tar", "application/x-bzip", "application/x-bzip2",
        "application/x-xz", "application/zstd",
        
        // 문서 
        "application/pdf",
        "application/vnd.ms-excel",  // 구 Excel
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",  // .xlsx
        "application/vnd.ms-powerpoint",  // 구 PowerPoint
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",  // .pptx
        "application/vnd.ms-word",  // 구 Word
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
        "application/vnd.oasis.opendocument.text",  // .odt
        "application/vnd.oasis.opendocument.spreadsheet",  // .ods
        "application/vnd.oasis.opendocument.presentation",  // .odp
        
        // 바이너리
        "application/octet-stream",
        "application/x-msdownload",
        "application/x-executable",
        "application/x-sharedlib",
        
        // 폰트
        "font/woff", "font/woff2", "font/ttf", "font/otf",
        "application/font-woff", "application/font-woff2",
        "application/vnd.ms-fontobject"
    );
	
	
	private final int threshold;
	
	
	
    public CompressorHandler(int threshold) {
    
        super();  // compressionLevel 6 (기본값)
        this.threshold = threshold;
    }
    
    @Override
    protected Result beginEncode(HttpResponse httpResponse, String acceptEncoding) throws Exception {
        // 1. Content-Length 체크
        String contentLength = httpResponse.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        if (contentLength != null) {
            try {
                long length = Long.parseLong(contentLength);
                if (length < threshold) {
                    return null;  // threshold 미만은 압축 안함
                }
            } catch (NumberFormatException e) {
                // 무시
            }
        }
        
        // 2. Content-Type 체크
        String contentType = httpResponse.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            String normalized = normalizeContentType(contentType);
            
            if (EXCLUDED_MIME_TYPES.contains(normalized)) {
                return null; 
            }
            
            if (isExcludedByWildcard(normalized)) {
                return null;  
            }
            
        }
        
        // 3. Zlib 압축 수행
        return super.beginEncode(httpResponse, acceptEncoding);
    }
    
    /**
     * Content-Type 정규화
     */
    private String normalizeContentType(String contentType) {
        int semicolonIndex = contentType.indexOf(';');
        String normalized = semicolonIndex != -1 
            ? contentType.substring(0, semicolonIndex).trim()
            : contentType.trim();
        return normalized.toLowerCase();
    }
    
    /**
     * 와일드카드 패턴 매칭
     */
    private boolean isExcludedByWildcard(String contentType) {
        return contentType.startsWith("video/") || 
               contentType.startsWith("audio/") ||
               contentType.startsWith("image/");
    }
    
}
