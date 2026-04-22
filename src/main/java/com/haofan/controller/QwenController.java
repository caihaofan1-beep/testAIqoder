package com.haofan.controller;

import com.haofan.dto.FileDTO;
import com.haofan.service.QwenService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/qwen")
@CrossOrigin(origins = "*")
public class QwenController {

    private final QwenService qwenService;

    public QwenController(QwenService qwenService) {
        this.qwenService = qwenService;
    }

    /**
     * 普通聊天接口
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        try {
            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            String response = qwenService.chat(sessionId, request.getMessage());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            result.put("sessionId", sessionId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("聊天接口调用失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 带文件上传的聊天接口
     */
    @PostMapping("/chat-with-files")
    public ResponseEntity<Map<String, Object>> chatWithFiles(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "message", required = false, defaultValue = "") String message,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }

            List<FileDTO> fileDTOs = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    FileDTO fileDTO = new FileDTO();
                    fileDTO.setFileName(file.getOriginalFilename());
                    fileDTO.setFileType(file.getContentType());
                    fileDTO.setFileSize(file.getSize());
                    fileDTO.setBase64Content(java.util.Base64.getEncoder().encodeToString(file.getBytes()));
                    fileDTO.setImage(file.getContentType() != null && file.getContentType().startsWith("image/"));
                    fileDTOs.add(fileDTO);
                }
            }

            String response = qwenService.chatWithFiles(sessionId, message, fileDTOs);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            result.put("sessionId", sessionId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("带文件聊天接口调用失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 清空会话历史
     */
    @PostMapping("/clear-history")
    public ResponseEntity<Map<String, Object>> clearHistory(@RequestBody Map<String, String> request) {
        try {
            String sessionId = request.get("sessionId");
            if (sessionId != null) {
                qwenService.clearHistory(sessionId);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "历史已清空");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("清空历史失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "ok");
        result.put("model", "qwen-vl-plus");
        return ResponseEntity.ok(result);
    }

    @Data
    public static class ChatRequest {
        private String sessionId;
        private String message;
    }
}
