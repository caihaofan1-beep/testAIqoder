package com.haofan.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haofan.dto.ChatMessage;
import com.haofan.dto.FileDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class QwenService {

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.model.name}")
    private String modelName;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    // 对话历史存储（限制每个会话最多5条消息）
    private final Map<String, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 5;

    public QwenService() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 普通聊天（无文件）
     */
    public String chat(String sessionId, String userMessage) {
        return chatWithFiles(sessionId, userMessage, null);
    }

    /**
     * 带文件的聊天（支持多模态和对话记忆）
     */
    public String chatWithFiles(String sessionId, String userMessage, List<FileDTO> files) {
        try {
            log.info("处理请求 - 会话: {}, 文本: {}, 文件数: {}", 
                    sessionId, userMessage, files != null ? files.size() : 0);

            // 获取或创建会话历史
            List<ChatMessage> history = conversationHistory.computeIfAbsent(sessionId, k -> new ArrayList<>());

            // 构建当前消息内容
            List<Map<String, Object>> contentList = new ArrayList<>();

            // 先添加图片（如果有）
            if (files != null && !files.isEmpty()) {
                for (FileDTO file : files) {
                    if (file.isImage()) {
                        String mimeType = file.getFileType();
                        if (mimeType == null || mimeType.isEmpty()) {
                            mimeType = "image/png";
                        }

                        // 确保 Base64 内容不包含前缀
                        String base64Content = file.getBase64Content();
                        if (base64Content != null && base64Content.contains(",")) {
                            base64Content = base64Content.substring(base64Content.indexOf(",") + 1);
                        }

                        // 检查 Base64 大小，如果太大会导致 Internal Server Error
                        if (base64Content != null && base64Content.length() > 5 * 1024 * 1024) { // 5MB
                            log.warn("图片过大，已跳过: {}, 大小: {} bytes", file.getFileName(), base64Content.length());
                            continue;
                        }

                        String dataUrl = "data:" + mimeType + ";base64," + base64Content;

                        Map<String, Object> imageContent = new LinkedHashMap<>();
                        imageContent.put("type", "image_url");
                        Map<String, String> imageUrl = new LinkedHashMap<>();
                        imageUrl.put("url", dataUrl);
                        imageContent.put("image_url", imageUrl);

                        contentList.add(imageContent);
                        log.info("添加图片: {}, Base64大小: {} bytes", file.getFileName(), 
                                base64Content != null ? base64Content.length() : 0);
                    }
                }
            }

            // 添加文本内容
            String textContent = userMessage != null ? userMessage : "";
            if (!textContent.isEmpty() || (files != null && !files.isEmpty())) {
                Map<String, Object> textContentMap = new LinkedHashMap<>();
                textContentMap.put("type", "text");
                textContentMap.put("text", textContent);
                contentList.add(textContentMap);
            }

            // 构建用户消息
            ChatMessage userChatMessage = new ChatMessage();
            userChatMessage.setRole("user");
            userChatMessage.setContent(contentList.isEmpty() ? textContent : contentList);

            // 添加到历史记录
            history.add(userChatMessage);

            // 限制历史记录大小（保留最近5次对话）
            if (history.size() > MAX_HISTORY_SIZE * 2) { // *2 因为每次对话有 user 和 assistant 两条消息
                history = history.subList(history.size() - MAX_HISTORY_SIZE * 2, history.size());
                conversationHistory.put(sessionId, new ArrayList<>(history));
            }

            // 构建请求
            Map<String, Object> requestMap = new LinkedHashMap<>();
            requestMap.put("model", modelName);
            requestMap.put("messages", history);
            requestMap.put("temperature", 0.7);
            requestMap.put("max_tokens", 2000);

            String requestBody = objectMapper.writeValueAsString(requestMap);
            log.info("发送请求到模型: {}, 历史消息数: {}", modelName, history.size());

            // 发送请求
            WebClient client = WebClient.builder()
                    .baseUrl(apiUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();

            ResponseEntity<String> response = client.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(String.class)
                    .block();

            if (response != null && response.getStatusCode() == HttpStatus.OK) {
                log.info("API 调用成功");
                
                // 解析响应
                Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, String> message = (Map<String, String>) choice.get("message");
                    String assistantReply = message.get("content");

                    // 保存助手回复到历史
                    ChatMessage assistantMessage = new ChatMessage();
                    assistantMessage.setRole("assistant");
                    assistantMessage.setContent(assistantReply);
                    history.add(assistantMessage);

                    log.info("返回结果长度: {}", assistantReply.length());
                    return assistantReply;
                }
            } else if (response != null) {
                log.error("API 错误: {} - {}", response.getStatusCode(), response.getBody());
                return "API 调用失败 (" + response.getStatusCode() + "): " + response.getBody();
            }

            return "调用失败，请稍后重试";
        } catch (Exception e) {
            log.error("调用 Qwen API 失败", e);
            return "调用失败: " + e.getMessage();
        }
    }

    /**
     * 清空会话历史
     */
    public void clearHistory(String sessionId) {
        conversationHistory.remove(sessionId);
        log.info("已清空会话历史: {}", sessionId);
    }

    /**
     * 获取会话历史长度
     */
    public int getHistorySize(String sessionId) {
        List<ChatMessage> history = conversationHistory.get(sessionId);
        return history != null ? history.size() : 0;
    }
}
