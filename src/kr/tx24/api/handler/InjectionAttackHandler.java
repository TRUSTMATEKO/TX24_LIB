package kr.tx24.api.handler;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import kr.tx24.lib.netty.NettyUtils;

public class InjectionAttackHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(InjectionAttackHandler.class);
    
    // 공격 시도 카운터 (IP별)
    private static final ConcurrentHashMap<String, AtomicInteger> attackAttempts = new ConcurrentHashMap<>();
    
    // 블랙리스트 (공격 IP)
    private static final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();
    
    private final int maxAttemptsBeforeBlock;  // 블랙리스트 등록 임계값
    private final int blacklistDurationSeconds;  // 블랙리스트 유지 시간
    
    public InjectionAttackHandler(int maxAttemptsBeforeBlock, int blacklistDurationSeconds) {
        this.maxAttemptsBeforeBlock = maxAttemptsBeforeBlock;
        this.blacklistDurationSeconds = blacklistDurationSeconds;
    }
    
    // ========== SQL Injection 패턴 (50+ 패턴) ==========
    
    private static final Pattern[] SQL_INJECTION_PATTERNS = {
        // === 기본 SQL 키워드 ===
        Pattern.compile(".*\\b(union|select|insert|update|delete|drop|create|alter|exec|execute)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === SQL 주석 ===
        Pattern.compile(".*(-{2}|#|/\\*|\\*/).*"),  // --, #, /* */
        
        // === UNION 기반 공격 ===
        Pattern.compile(".*\\bunion\\s+(all\\s+)?select\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bunion\\s+.*\\s+from\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === SELECT 기반 공격 ===
        Pattern.compile(".*\\bselect\\s+.*\\s+from\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bselect\\s+.*\\s+where\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bselect\\s+\\*\\s+from\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bselect\\s+count\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bselect\\s+.*\\s+into\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === INSERT/UPDATE/DELETE 공격 ===
        Pattern.compile(".*\\binsert\\s+into\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bupdate\\s+.*\\s+set\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bdelete\\s+from\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === DROP/ALTER/CREATE (위험한 DDL) ===
        Pattern.compile(".*\\bdrop\\s+(table|database|schema|index|view)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\balter\\s+(table|database|schema)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bcreate\\s+(table|database|schema|index|view)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\btruncate\\s+table\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === EXEC/EXECUTE (저장 프로시저 실행) ===
        Pattern.compile(".*\\bexec(ute)?\\s+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bexec(ute)?\\s*\\(.*\\).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bsp_executesql\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bxp_cmdshell\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === Boolean-based Blind SQL Injection ===
        Pattern.compile(".*\\b(and|or)\\s+['\"]?\\d+['\"]?\\s*=\\s*['\"]?\\d+['\"]?.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(and|or)\\s+['\"]?[a-z]+['\"]?\\s*=\\s*['\"]?[a-z]+['\"]?.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\b(and|or)\\s+\\d+\\s*[<>=!]+\\s*\\d+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*(and|or)\\s*'.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\"\\s*(and|or)\\s*\".*", Pattern.CASE_INSENSITIVE),
        
        // === Time-based Blind SQL Injection ===
        Pattern.compile(".*\\b(sleep|benchmark|waitfor|pg_sleep)\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bwaitfor\\s+delay\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === INFORMATION_SCHEMA 접근 ===
        Pattern.compile(".*\\binformation_schema\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bsys\\.tables\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bsys\\.columns\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bmysql\\.user\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === LOAD_FILE / INTO OUTFILE (파일 접근) ===
        Pattern.compile(".*\\bload_file\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\binto\\s+(outfile|dumpfile)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === 특수 문자 조합 (Evasion 시도) ===
        Pattern.compile(".*['\";]\\s*(--|#).*"),  // '; --, "; --
        Pattern.compile(".*'\\s*;\\s*.*"),  // '; 
        Pattern.compile(".*\"\\s*;\\s*.*"),  // "; 
        Pattern.compile(".*'\\s*\\|\\|\\s*'.*"),  // ' || '
        Pattern.compile(".*\"\\s*\\|\\|\\s*\".*"),  // " || "
        
        // === SQL 함수 남용 ===
        Pattern.compile(".*\\b(concat|substring|mid|char|ascii|hex|unhex)\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bcast\\s*\\(.*\\s+as\\s+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bconvert\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        
        // === Stacked Queries ===
        Pattern.compile(".*;\\s*(select|insert|update|delete|drop|create)\\b.*", Pattern.CASE_INSENSITIVE),
        
        // === 인코딩 우회 시도 ===
        Pattern.compile(".*%27.*"),  // URL encoded '
        Pattern.compile(".*%22.*"),  // URL encoded "
        Pattern.compile(".*%3b.*", Pattern.CASE_INSENSITIVE),  // URL encoded ;
        Pattern.compile(".*&#x27;.*"),  // HTML entity '
        Pattern.compile(".*&#39;.*"),  // HTML entity '
        
        // === NoSQL Injection (MongoDB 등) ===
        Pattern.compile(".*\\$where.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\$ne\\s*:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\$gt\\s*:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\$regex\\s*:.*", Pattern.CASE_INSENSITIVE),
        
        // === 기타 위험 패턴 ===
        Pattern.compile(".*'\\s*\\+\\s*'.*"),  // ' + ' (문자열 연결)
        Pattern.compile(".*'\\s*\\|\\s*'.*"),  // ' | ' (OR 연산)
        Pattern.compile(".*having\\s+\\d+\\s*=\\s*\\d+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*group\\s+by\\s+.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*order\\s+by\\s+\\d+.*", Pattern.CASE_INSENSITIVE)
    };
    
    
    // ========== XSS 공격 패턴 (40+ 패턴) ==========
    
    private static final Pattern[] XSS_PATTERNS = {
        // === 기본 <script> 태그 ===
        Pattern.compile(".*<script[^>]*>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*</script>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<script\\s*.*", Pattern.CASE_INSENSITIVE),
        
        // === JavaScript 이벤트 핸들러 ===
        Pattern.compile(".*\\bon\\w+\\s*=.*", Pattern.CASE_INSENSITIVE),  // onclick, onload, onerror 등
        Pattern.compile(".*\\bonload\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonerror\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonclick\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonmouseover\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonmouseout\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonfocus\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonblur\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonchange\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bonsubmit\\s*=.*", Pattern.CASE_INSENSITIVE),
        
        // === javascript: 프로토콜 ===
        Pattern.compile(".*javascript\\s*:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*j\\s*a\\s*v\\s*a\\s*s\\s*c\\s*r\\s*i\\s*p\\s*t\\s*:.*", Pattern.CASE_INSENSITIVE),  // 공백 우회
        
        // === <iframe> 삽입 ===
        Pattern.compile(".*<iframe[^>]*>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*</iframe>.*", Pattern.CASE_INSENSITIVE),
        
        // === <embed>, <object>, <applet> ===
        Pattern.compile(".*<embed[^>]*>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<object[^>]*>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<applet[^>]*>.*", Pattern.CASE_INSENSITIVE),
        
        // === <img> 태그 악용 ===
        Pattern.compile(".*<img[^>]*src\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<img[^>]*onerror\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<img[^>]*onload\\s*=.*", Pattern.CASE_INSENSITIVE),
        
        // === <svg> 태그 악용 ===
        Pattern.compile(".*<svg[^>]*onload\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<svg[^>]*>.*<script.*", Pattern.CASE_INSENSITIVE),
        
        // === <link>, <style> 악용 ===
        Pattern.compile(".*<link[^>]*href.*javascript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<style[^>]*>.*expression\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        
        // === <form> 악용 ===
        Pattern.compile(".*<form[^>]*action\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<form[^>]*onsubmit\\s*=.*", Pattern.CASE_INSENSITIVE),
        
        // === <input> 악용 ===
        Pattern.compile(".*<input[^>]*onfocus\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<input[^>]*autofocus\\s*.*", Pattern.CASE_INSENSITIVE),
        
        // === <meta> 리다이렉트 ===
        Pattern.compile(".*<meta[^>]*http-equiv.*refresh.*", Pattern.CASE_INSENSITIVE),
        
        // === <base> 태그 ===
        Pattern.compile(".*<base[^>]*href\\s*=.*", Pattern.CASE_INSENSITIVE),
        
        // === document, window 객체 접근 ===
        Pattern.compile(".*\\bdocument\\.(write|cookie|location|domain).*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bwindow\\.(location|open|alert|confirm|prompt).*", Pattern.CASE_INSENSITIVE),
        
        // === eval, setTimeout, setInterval ===
        Pattern.compile(".*\\beval\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bsetTimeout\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\bsetInterval\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        
        // === String.fromCharCode (인코딩 우회) ===
        Pattern.compile(".*String\\.fromCharCode\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        
        // === HTML 엔티티 인코딩 우회 ===
        Pattern.compile(".*&#x3c;script.*", Pattern.CASE_INSENSITIVE),  // &#x3c; = <
        Pattern.compile(".*&#60;script.*", Pattern.CASE_INSENSITIVE),   // &#60; = <
        Pattern.compile(".*&lt;script.*", Pattern.CASE_INSENSITIVE),    // &lt; = <
        
        // === URL 인코딩 우회 ===
        Pattern.compile(".*%3cscript.*", Pattern.CASE_INSENSITIVE),  // %3c = <
        Pattern.compile(".*%3e.*", Pattern.CASE_INSENSITIVE),  // %3e = >
        
        // === 기타 위험 패턴 ===
        Pattern.compile(".*<.*onanimationstart\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*<.*ontransitionend\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*expression\\s*\\(.*\\).*", Pattern.CASE_INSENSITIVE),  // CSS expression
        Pattern.compile(".*vbscript\\s*:.*", Pattern.CASE_INSENSITIVE),  // VBScript
        Pattern.compile(".*data\\s*:.*text/html.*", Pattern.CASE_INSENSITIVE)  // data: URI
    };
    
    
    // ========== Path Traversal 패턴 ==========
    
    private static final Pattern[] PATH_TRAVERSAL_PATTERNS = {
        Pattern.compile(".*\\.\\.[\\\\/].*"),  // ../, ..\
        Pattern.compile(".*\\.\\.%2[fF].*"),  // URL encoded ../
        Pattern.compile(".*\\.\\.%5[cC].*"),  // URL encoded ..\
        Pattern.compile(".*%2e%2e[\\\\/].*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/etc/passwd.*"),
        Pattern.compile(".*/windows/system32.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\.\\./\\.\\./\\.\\./.*")  // ../../..
    };
    
    
    // ========== Command Injection 패턴 ==========
    
    private static final Pattern[] COMMAND_INJECTION_PATTERNS = {
        Pattern.compile(".*;\\s*(ls|cat|wget|curl|nc|bash|sh|cmd|powershell)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\|\\s*(ls|cat|wget|curl|nc)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*`.*`.*"),  // Command substitution
        Pattern.compile(".*\\$\\(.*\\).*"),  // Command substitution
        Pattern.compile(".*&&\\s*(ls|cat|rm|chmod)\\b.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\|\\|\\s*(ls|cat|rm)\\b.*", Pattern.CASE_INSENSITIVE)
    };
    
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }
        
        FullHttpRequest request = (FullHttpRequest) msg;
        String clientIp = NettyUtils.getClientIp(ctx, request);
        
        try {
            // 블랙리스트 체크
            if (isBlacklisted(clientIp)) {
                logger.warn("Blocked request from blacklisted IP: {}", clientIp);
                sendSecurityError(ctx, request, "IP blocked due to malicious activity");
                return;
            }
            
            // URI 검사
            String uri = request.uri();
            
            // Body 검사 (POST, PUT, PATCH)
            String body = "";
            if (request.content().readableBytes() > 0) {
                body = request.content().toString(CharsetUtil.UTF_8);
            }
            
            // 모든 헤더 검사
            StringBuilder headers = new StringBuilder();
            for (String headerName : request.headers().names()) {
                headers.append(request.headers().get(headerName)).append(" ");
            }
            String headersStr = headers.toString();
            
            // 공격 탐지
            AttackType attackType = detectAttack(uri, body, headersStr);
            
            if (attackType != AttackType.NONE) {
                recordAttackAttempt(clientIp);
                
                logger.warn("Attack detected - Type: {}, IP: {}, URI: {}", 
                    attackType, clientIp, uri);
                
                // 공격 시도 횟수 확인
                int attempts = getAttackAttempts(clientIp);
                if (attempts >= maxAttemptsBeforeBlock) {
                    addToBlacklist(clientIp);
                    logger.error("IP blacklisted due to repeated attacks: {} (attempts: {})", 
                        clientIp, attempts);
                }
                
                sendSecurityError(ctx, request, "Malicious request detected");
                return;
            }
            
            // 정상 요청 통과
            ctx.fireChannelRead(request.retain());
            
        } catch (Exception e) {
            logger.error("Error in injection detector: {}", e.getMessage(), e);
            ctx.fireChannelRead(request.retain());
        } finally {
            request.release();
        }
    }
    
    /**
     * 공격 탐지
     */
    private AttackType detectAttack(String uri, String body, String headers) {
        String combined = uri + " " + body + " " + headers;
        
        // SQL Injection 탐지
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(combined).matches()) {
                return AttackType.SQL_INJECTION;
            }
        }
        
        // XSS 탐지
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(combined).matches()) {
                return AttackType.XSS;
            }
        }
        
        // Path Traversal 탐지
        for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
            if (pattern.matcher(uri).matches()) {
                return AttackType.PATH_TRAVERSAL;
            }
        }
        
        // Command Injection 탐지
        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(combined).matches()) {
                return AttackType.COMMAND_INJECTION;
            }
        }
        
        return AttackType.NONE;
    }
    
    
    private void recordAttackAttempt(String ip) {
        attackAttempts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    private int getAttackAttempts(String ip) {
        AtomicInteger counter = attackAttempts.get(ip);
        return counter != null ? counter.get() : 0;
    }
    
    private boolean isBlacklisted(String ip) {
        Long blacklistUntil = blacklist.get(ip);
        if (blacklistUntil == null) return false;
        
        if (System.currentTimeMillis() > blacklistUntil) {
            blacklist.remove(ip);
            attackAttempts.remove(ip);
            return false;
        }
        return true;
    }
    
    private void addToBlacklist(String ip) {
        long blacklistUntil = System.currentTimeMillis() + (blacklistDurationSeconds * 1000L);
        blacklist.put(ip, blacklistUntil);
    }
    
    private void sendSecurityError(ChannelHandlerContext ctx, FullHttpRequest request, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.FORBIDDEN
        );
        
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            .setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    enum AttackType {
        NONE,
        SQL_INJECTION,
        XSS,
        PATH_TRAVERSAL,
        COMMAND_INJECTION
    }
}