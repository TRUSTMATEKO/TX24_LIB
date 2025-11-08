package kr.tx24.naverwork.bot;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.tx24.naverwork.oauth.NwAccessTokenManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NwBotMessenger {
	
	private static final Logger logger = LoggerFactory.getLogger(NwBotMessenger.class);

	/** 네이버웍스 API Base URL */
    private static final String API_BASE_URL = "https://www.worksapis.com/v1.0";
    
    /** Bot ID */
    private final String botId;
    
    /** OkHttp 클라이언트 */
    private final OkHttpClient okHttpClient;
    
    /** JSON 매퍼 */
    private final ObjectMapper objectMapper;
    
    /**
     * NaverWorksBotMessenger 생성자
     * 
     * @param botId Bot ID
     */
    public NwBotMessenger(String botId) {
        if (botId == null || botId.trim().isEmpty()) {
            throw new IllegalArgumentException("botId는 필수입니다.");
        }
        
        this.botId = botId;
        this.okHttpClient = createOkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    /**
     * OkHttp 클라이언트 생성
     * 
     * @return 설정된 OkHttpClient
     */
    private OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                // Authorization 헤더 자동 추가
                NwAccessTokenManager manager = NwAccessTokenManager.getInstance();
                Request authorized = chain.request().newBuilder()
                    .header("Authorization", manager.getAuthorizationHeader())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .build();
                return chain.proceed(authorized);
            })
            .build();
    }
    
    /**
     * 텍스트 메시지 전송 (채널)
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendTextMessage(String channelId, String text) throws IOException {
        TextContent content = new TextContent(text);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessage(channelId, request);
    }
    
    /**
     * 텍스트 메시지 전송 (사용자)
     * 
     * @param userId 사용자 ID
     * @param text 메시지 텍스트
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendTextMessageToUser(String userId, String text) throws IOException {
        TextContent content = new TextContent(text);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessageToUser(userId, request);
    }
    
    /**
     * 버튼 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param buttons 버튼 목록
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendButtonMessage(String channelId, String text, List<Button> buttons) 
            throws IOException {
        ButtonTemplateContent content = new ButtonTemplateContent(text, buttons);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessage(channelId, request);
    }
    
    /**
     * 리스트 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param header 헤더 텍스트
     * @param items 리스트 아이템
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendListMessage(String channelId, String header, List<ListItem> items) 
            throws IOException {
        ListTemplateContent content = new ListTemplateContent(header, items);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessage(channelId, request);
    }
    
    /**
     * 이미지 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param imageUrl 이미지 URL
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendImageMessage(String channelId, String imageUrl) throws IOException {
        ImageContent content = new ImageContent(imageUrl);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessage(channelId, request);
    }
    
    /**
     * 링크 메시지 전송 (텍스트 + URL)
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param linkUrl 링크 URL
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public MessageResponse sendLinkMessage(String channelId, String text, String linkUrl) 
            throws IOException {
        LinkContent content = new LinkContent(text, linkUrl);
        MessageRequest request = new MessageRequest(content);
        
        return sendMessage(channelId, request);
    }
    
    /**
     * 메시지 전송 (채널)
     * 
     * @param channelId 채널 ID
     * @param messageRequest 메시지 요청 객체
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    private MessageResponse sendMessage(String channelId, MessageRequest messageRequest) 
            throws IOException {
        String url = String.format("%s/bots/%s/channels/%s/messages", 
            API_BASE_URL, botId, channelId);
        
        return executeRequest(url, messageRequest);
    }
    
    /**
     * 메시지 전송 (사용자)
     * 
     * @param userId 사용자 ID
     * @param messageRequest 메시지 요청 객체
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    private MessageResponse sendMessageToUser(String userId, MessageRequest messageRequest) 
            throws IOException {
        String url = String.format("%s/bots/%s/users/%s/messages", 
            API_BASE_URL, botId, userId);
        
        return executeRequest(url, messageRequest);
    }
    
    /**
     * HTTP 요청 실행
     * 
     * @param url 요청 URL
     * @param messageRequest 메시지 요청 객체
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    private MessageResponse executeRequest(String url, MessageRequest messageRequest) 
            throws IOException {
        String jsonBody = objectMapper.writeValueAsString(messageRequest);
        
        RequestBody body = RequestBody.create(
            jsonBody,
            MediaType.get("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();
        
        logger.info("url : {},body: {}",url,body);
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            logger.info("Response Code: {}, Body: {}", response.code(), responseBody);
            
            if (!response.isSuccessful()) {
                throw new IOException(
                    String.format("메시지 전송 실패: HTTP %d - %s", 
                        response.code(), responseBody)
                );
            }
            
            // ========== 핵심 수정: 빈 응답 처리 ==========
            // HTTP 204 No Content 또는 빈 응답인 경우
            if (response.code() == 204 || responseBody == null || responseBody.trim().isEmpty()) {
                logger.info("메시지 전송 성공 (응답 본문 없음)");
                MessageResponse emptyResponse = new MessageResponse();
                // 필요시 URL에서 channelId 추출하여 설정
                return emptyResponse;
            }
            
            // JSON 응답이 있는 경우에만 파싱
            return objectMapper.readValue(responseBody, MessageResponse.class);
        }
    }
    
    // ==================== 내부 클래스 (메시지 구조) ====================
    
    /**
     * 메시지 요청
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageRequest {
        @JsonProperty("content")
        private Content content;
        
        public MessageRequest(Content content) {
            this.content = content;
        }
        
        public Content getContent() {
            return content;
        }
    }
    
    /**
     * 컨텐츠 인터페이스
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public interface Content {
        String getType();
    }
    
    /**
     * 텍스트 컨텐츠
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TextContent implements Content {
        @JsonProperty("type")
        private final String type = "text";
        
        @JsonProperty("text")
        private String text;
        
        public TextContent(String text) {
            this.text = text;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        public String getText() {
            return text;
        }
    }
    
    /**
     * 이미지 컨텐츠
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageContent implements Content {
        @JsonProperty("type")
        private final String type = "image";
        
        @JsonProperty("resourceUrl")
        private String resourceUrl;
        
        public ImageContent(String resourceUrl) {
            this.resourceUrl = resourceUrl;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        public String getResourceUrl() {
            return resourceUrl;
        }
    }
    
    /**
     * 링크 컨텐츠
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LinkContent implements Content {
        @JsonProperty("type")
        private final String type = "link";
        
        @JsonProperty("text")
        private String text;
        
        @JsonProperty("linkUrl")
        private String linkUrl;
        
        public LinkContent(String text, String linkUrl) {
            this.text = text;
            this.linkUrl = linkUrl;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        public String getText() {
            return text;
        }
        
        public String getLinkUrl() {
            return linkUrl;
        }
    }
    
    /**
     * 버튼 템플릿 컨텐츠
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ButtonTemplateContent implements Content {
        @JsonProperty("type")
        private final String type = "button_template";
        
        @JsonProperty("contentText")
        private String contentText;
        
        @JsonProperty("buttons")
        private List<Button> buttons;
        
        public ButtonTemplateContent(String contentText, List<Button> buttons) {
            this.contentText = contentText;
            this.buttons = buttons;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        public String getContentText() {
            return contentText;
        }
        
        public List<Button> getButtons() {
            return buttons;
        }
    }
    
    /**
     * 리스트 템플릿 컨텐츠
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ListTemplateContent implements Content {
        @JsonProperty("type")
        private final String type = "list_template";
        
        @JsonProperty("header")
        private String header;
        
        @JsonProperty("elements")
        private List<ListItem> elements;
        
        public ListTemplateContent(String header, List<ListItem> elements) {
            this.header = header;
            this.elements = elements;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        public String getHeader() {
            return header;
        }
        
        public List<ListItem> getElements() {
            return elements;
        }
    }
    
    /**
     * 버튼
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Button {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("label")
        private String label;
        
        @JsonProperty("value")
        private String value;
        
        @JsonProperty("url")
        private String url;
        
        /**
         * 포스트백 버튼 생성
         */
        public Button(String label, String value) {
            this.type = "postback";
            this.label = label;
            this.value = value;
        }
        
        /**
         * URL 버튼 생성
         */
        public static Button urlButton(String label, String url) {
            Button button = new Button();
            button.type = "uri";
            button.label = label;
            button.url = url;
            return button;
        }
        
        private Button() {}
        
        public String getType() {
            return type;
        }
        
        public String getLabel() {
            return label;
        }
        
        public String getValue() {
            return value;
        }
        
        public String getUrl() {
            return url;
        }
    }
    
    /**
     * 리스트 아이템
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ListItem {
        @JsonProperty("title")
        private String title;
        
        @JsonProperty("subtitle")
        private String subtitle;
        
        @JsonProperty("link")
        private String link;
        
        public ListItem(String title) {
            this.title = title;
        }
        
        public ListItem(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
        
        public ListItem(String title, String subtitle, String link) {
            this.title = title;
            this.subtitle = subtitle;
            this.link = link;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getSubtitle() {
            return subtitle;
        }
        
        public String getLink() {
            return link;
        }
    }
    
    /**
     * 메시지 전송 응답
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MessageResponse {
        @JsonProperty("messageId")
        private String messageId;
        
        @JsonProperty("channelId")
        private String channelId;
        
        @JsonProperty("userId")
        private String userId;
        
        @JsonProperty("createdTime")
        private String createdTime;
        
        public MessageResponse() {}
        
        public String getMessageId() {
            return messageId;
        }
        
        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }
        
        public String getChannelId() {
            return channelId;
        }
        
        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
        
        public String getCreatedTime() {
            return createdTime;
        }
        
        public void setCreatedTime(String createdTime) {
            this.createdTime = createdTime;
        }
        
        @Override
        public String toString() {
            return "MessageResponse{" +
                "messageId='" + messageId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", userId='" + userId + '\'' +
                ", createdTime='" + createdTime + '\'' +
                '}';
        }
    }
}
