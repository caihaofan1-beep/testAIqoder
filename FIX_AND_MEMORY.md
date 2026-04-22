# 问题修复和功能增强说明

## 修复时间
2026-04-22

## 问题1：Internal Server Error

### 问题描述
上传图片后，API 返回 "Internal Server Error"

### 问题原因
1. **Base64 图片数据过大** - 导致 HTTP 请求体超过服务器限制
2. **缺少图片大小检查** - 没有对过大的图片进行过滤
3. **可能重复的 Base64 前缀** - data URL 格式可能不正确

### 修复方案

#### 1. 添加图片大小检查
```java
// 检查 Base64 大小，如果超过 5MB 则跳过
if (base64Content != null && base64Content.length() > 5 * 1024 * 1024) {
    log.warn("图片过大，已跳过: {}, 大小: {} bytes", file.getFileName(), base64Content.length());
    continue;
}
```

#### 2. 确保 Base64 格式正确
```java
// 确保 Base64 内容不包含 data:image/xxx;base64, 前缀
String base64Content = file.getBase64Content();
if (base64Content != null && base64Content.contains(",")) {
    base64Content = base64Content.substring(base64Content.indexOf(",") + 1);
}

String dataUrl = "data:" + mimeType + ";base64," + base64Content;
```

#### 3. 增强错误处理和日志
- 添加详细的日志输出
- 返回具体的错误信息
- 记录请求和响应的关键信息

## 功能2：对话记忆（限制5次）

### 功能描述
实现对话上下文记忆功能，AI 能够记住最近 5 次对话内容，提供更连贯的交流体验。

### 实现方案

#### 后端实现

**1. 会话历史存储**
```java
// 使用 ConcurrentHashMap 存储多个会话的历史
private final Map<String, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();
private static final int MAX_HISTORY_SIZE = 5; // 最多保留5次对话
```

**2. 会话ID管理**
- 首次对话时自动生成 UUID 作为 sessionId
- 后续对话携带相同的 sessionId
- 前端自动保存和传递 sessionId

**3. 历史记录管理**
```java
// 添加到历史记录
history.add(userChatMessage);

// 限制历史记录大小（保留最近5次对话 = 10条消息）
if (history.size() > MAX_HISTORY_SIZE * 2) {
    history = history.subList(history.size() - MAX_HISTORY_SIZE * 2, history.size());
    conversationHistory.put(sessionId, new ArrayList<>(history));
}
```

**4. API 接口**
- `POST /api/qwen/chat` - 普通聊天（自动管理 sessionId）
- `POST /api/qwen/chat-with-files` - 带文件聊天（支持 sessionId）
- `POST /api/qwen/clear-history` - 清空会话历史

#### 前端实现

**1. SessionId 管理**
```javascript
let currentSessionId = null; // 当前会话ID

// 发送消息时携带 sessionId
if (currentSessionId) {
    formData.append('sessionId', currentSessionId);
}

// 保存后端返回的 sessionId
if (result.sessionId && !currentSessionId) {
    currentSessionId = result.sessionId;
}
```

**2. 新建对话时重置**
```javascript
function startNewChat() {
    currentSessionId = null; // 重置会话ID
    // ... 其他逻辑
}
```

### 使用示例

**场景1：连续对话**
```
用户：这张图片是什么？
AI：这是一张风景照片...

用户：它的主要特点是什么？（AI 知道"它"指的是上一张图片）
AI：这张风景照的主要特点是...

用户：能换个角度描述吗？（继续上下文）
AI：从另一个角度看，这张照片...
```

**场景2：带图片的连续对话**
```
用户：[上传图片1] 这张图有什么？
AI：图中有...

用户：[上传图片2] 这两张有什么区别？
AI：第一张图片... 第二张图片... 主要区别是...

用户：哪个更好看？
AI：从艺术角度看...
```

**场景3：开启新对话**
```
用户：点击"新对话"按钮
系统：清空历史记录，生成新的 sessionId
用户：重新开始全新的话题
```

## 修改文件清单

### 新增文件
1. ✅ `ChatMessage.java` - 对话消息 DTO
2. ✅ 重新创建所有后端文件（由于文件丢失）

### 修改文件
1. ✅ `QwenService.java`
   - 添加会话历史管理
   - 添加图片大小检查
   - 优化 Base64 处理
   - 增强错误处理

2. ✅ `QwenController.java`
   - 添加 sessionId 支持
   - 添加清空历史接口
   - 添加跨域支持

3. ✅ `index.html`
   - 添加 sessionId 管理
   - 发送消息时携带 sessionId
   - 新建对话时重置 sessionId

4. ✅ `application.properties`
   - 确认使用 qwen-vl-plus 模型

## 测试验证

### 测试步骤

1. **启动项目**
   ```bash
   mvn clean spring-boot:run
   ```

2. **测试图片上传**
   - 上传一张小于 5MB 的图片
   - 输入："请描述这张图片"
   - 预期：成功识别并描述图片内容

3. **测试对话记忆**
   ```
   第一轮：
   用户：这张图片是什么颜色？
   AI：这张图片主要是蓝色...
   
   第二轮（不上传图片）：
   用户：还有其他颜色吗？
   AI：除了蓝色，还有少量白色...（AI 记得之前的图片）
   
   第三轮：
   用户：整体感觉如何？
   AI：整体给人清新宁静的感觉...（继续上下文）
   ```

4. **测试新对话**
   - 点击"新对话"按钮
   - 开始全新话题
   - 预期：AI 不记得之前的对话内容

### 预期日志输出

```
INFO  处理请求 - 会话: xxx, 文本: 请描述这张图片, 文件数: 1
INFO  添加图片: test.png, Base64大小: 123456 bytes
INFO  发送请求到模型: qwen-vl-plus, 历史消息数: 1
INFO  API 调用成功
INFO  返回结果长度: 156
```

## 注意事项

### 1. 图片大小限制
- **前端**：20MB（上传限制）
- **后端**：5MB Base64 编码后大小
- **建议**：上传前压缩图片到 2MB 以内

### 2. 对话记忆限制
- **最多保留**：5 次完整对话（10 条消息）
- **超出后**：自动删除最早的消息
- **新对话**：点击"新对话"按钮清空历史

### 3. SessionId 管理
- **自动生成**：首次发送消息时生成
- **持久化**：在同一对话中保持不变
- **重置**：点击"新对话"或刷新页面时重置

### 4. 性能优化
- 大图片会被自动跳过（>5MB）
- 历史记录限制防止内存溢出
- 使用 ConcurrentHashMap 保证线程安全

## 常见问题

### Q: 为什么图片还是无法识别？

**检查清单：**
1. 图片是否小于 5MB？
2. 图片格式是否支持？（JPEG, PNG, GIF, WebP）
3. API Key 是否有效？
4. 查看控制台日志是否有详细错误

**解决方法：**
- 压缩图片到 2MB 以内
- 转换为 JPEG 格式（文件更小）
- 检查 API Key 和配额

### Q: 对话记忆不工作？

**可能原因：**
1. SessionId 没有正确传递
2. 页面刷新导致 sessionId 丢失
3. 点击了"新对话"按钮

**解决方法：**
- 在同一页面连续对话，不要刷新
- 查看浏览器控制台，确认 sessionId 存在
- 检查后端日志，确认历史消息数递增

### Q: 如何清空对话历史？

**方法1：前端**
- 点击左侧"新对话"按钮

**方法2：API**
```bash
curl -X POST http://localhost:8080/api/qwen/clear-history \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "your-session-id"}'
```

### Q: 可以调整记忆次数吗？

**可以！** 修改 `QwenService.java` 中的常量：
```java
private static final int MAX_HISTORY_SIZE = 5; // 改为想要的次数
```

## 后续优化建议

- [ ] 添加图片自动压缩功能
- [ ] 支持图片格式自动转换
- [ ] 添加对话历史持久化（数据库）
- [ ] 支持导出对话记录
- [ ] 添加图片预览优化
- [ ] 支持更长的对话记忆（可配置）
- [ ] 添加对话摘要功能
- [ ] 支持多轮对话中的图片引用
