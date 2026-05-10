# Lesson 2 — 最小 Agent 调用：理解 Agent + Model + Msg

## 学习目标

跑通 AgentScope 的最简调用流程，理解三大核心抽象：Agent、Model、Msg。

---

## 核心三组件

```
Msg (消息)  →  Model (LLM 封装)  →  Agent (执行实体)
```

### 1. Model — LLM 的抽象封装

Model 是 LLM 的接口层，AgentScope 通过统一的接口支持多种 LLM。

**当前项目使用的模型：**

```java
DashScopeChatModel model = DashScopeChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .build();
```

- `DashScopeChatModel`：阿里云通义千问的封装
- 所有 Model 都通过 **builder 模式** 创建，接口风格统一
- Model 本身实现了 `Agent` 接口，可以直接当最简 Agent 用

---

### 2. Msg — 消息结构

`Msg` 是 AgentScope 的消息抽象，包含：

| 字段 | 类型 | 说明 |
|---|---|---|
| `role` | `MsgRole` | 发送者角色（USER / ASSISTANT / SYSTEM） |
| `content` | `List<ContentBlock>` | 消息内容，支持多模态 |

**构建用户消息：**

```java
Msg userMsg = Msg.builder()
        .role(MsgRole.USER)
        .content(List.of(TextBlock.builder().text(userInput).build()))
        .build();
```

关键点：
- **1.0.4 中 `content` 是 `List<ContentBlock>`**，不是单个 `ContentBlock`
- `TextBlock` 是最常用的 ContentBlock，位于 `io.agentscope.core.message` 包
- 用 `TextBlock.builder().text(...).build()` 构建，**没有 `TextBlock.of()` 快捷方法**

**从响应中提取文本：**

```java
ChatResponse response = model.stream(...).last().block();

String text = response.getContent().stream()
        .filter(cb -> cb instanceof TextBlock)
        .map(cb -> ((TextBlock) cb).getText())
        .findFirst()
        .orElse("");
```

`ChatResponse.getContent()` 返回 `List<ContentBlock>`，需要过滤和转换。

---

### 3. Agent — 最简调用方式（纯 Model）

**注意**：本节介绍的是"纯 Model 调用"，即 Model 本身就是最简 Agent。关于带工具调用能力的 **ReActAgent**，详见 [Lesson 3](lesson3-tool-and-react-agent.md)。

AgentScope 1.0.4 中，**Model 只有 `stream()` 方法，没有 `call()` 方法**。

**一次性返回（模拟 call）：**

```java
ChatResponse response = model.stream(
        List.of(userMsg),           // 消息列表
        Collections.emptyList(),    // 工具列表
        null                        // 生成选项
).last().block();
```

`stream().last().block()` 等价于其他版本中的 `call()`。

**流式返回：**

```java
Flux<String> tokens = model.stream(
        List.of(userMsg),
        Collections.emptyList(),
        null
).flatMap(response ->
        Flux.fromIterable(response.getContent())
                .filter(cb -> cb instanceof TextBlock)
                .map(cb -> ((TextBlock) cb).getText())
);
```

`stream()` 返回 `Flux<ChatResponse>`，需要 flatMap 提取文本。

---

## 实战：API 层

### 一次性返回接口

```java
@PostMapping("/chat")
public AgentResponse chat(@RequestBody String userInput) {
    return agentService.chat(userInput);
}
```

返回 JSON：`{ content, modelUsed, durationMs }`

### 流式返回接口（SSE）

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestParam("input") String userInput) {
    return agentService.chatStream(userInput);
}
```

- 使用 `SseEmitter`（Spring MVC Servlet 堆栈下的 SSE 标准写法）
- 每个文本块通过 `emitter.send()` 推送
- 前端用 `fetch` + `ReadableStream` 逐块读取

**重要踩坑记录：**
- `Flux<String>` + `text/event-stream` **不会自动添加 SSE 格式**，前端解析不到 `data:` 前缀
- 必须用 `SseEmitter`，Spring 自动处理 `data:` 前缀和 `\n\n` 分隔符

---

## 知识点：替换为自部署模型服务器

这是本任务最重要的扩展知识。

### 核心原则

> **Model 接口统一，换实现类就行**

不管用阿里云 DashScope、本地 vLLM、还是 Ollama，调用方式完全一样。只需要改两点：
1. 换 Model 实现类
2. 改配置参数

### 常见自部署场景对比

| 你的模型服务器 | 对应的 Model 类 | 需要配置的参数 |
|---|---|---|
| 阿里云 DashScope | `DashScopeChatModel` | `apiKey`, `modelName` |
| vLLM / TGI（OpenAI 兼容） | `OpenAIChatModel` | `baseUrl`, `apiKey`, `modelName` |
| Ollama | `OllamaChatModel` | `baseUrl`, `modelName` |

### 具体改法

#### 场景 A：vLLM / 其他 OpenAI 兼容服务

**改代码（只需换 builder 参数）：**

```java
// 原来：DashScopeChatModel
this.model = DashScopeChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .build();

// 改为：OpenAIChatModel
this.model = OpenAIChatModel.builder()
        .baseUrl("http://localhost:8000/v1")   // 你的 vLLM 地址
        .apiKey(apiKey)                        // vLLM 的 key，或占位符
        .modelName("Qwen2.5-72B-Instruct")     // 你的模型名
        .build();
```

**改配置文件：**

```yaml
# 原来
agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-max

# 改为
agentscope:
  openai:
    base-url: http://localhost:8000/v1
    api-key: ${OPENAI_API_KEY}     # 或 "sk-" 占位符（vLLM 不验证）
    model-name: Qwen2.5-72B-Instruct
```

#### 场景 B：Ollama

```java
this.model = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("qwen2.5:7b")
        .build();
```

```yaml
agentscope:
  ollama:
    base-url: http://localhost:11434
    model-name: qwen2.5:7b
```

### 关键理解

- **代码改动量很小**：只改 Model 实现类和参数，`stream()`、`call()`、`Msg` 全部不变
- **Msg 消息结构不变**：不管什么 Model，输入输出都是 `Msg`，Controller 和前端完全不需要改
- **AgentScope 1.0.4 内置支持**：`OpenAIChatModel`、`OllamaChatModel` 都在同一个 jar 包里，不需要额外加依赖
- **不需要改 Spring Web 配置**：SSE、SseEmitter 与具体 Model 实现无关

### 自部署推荐方案

| 场景 | 推荐 | 特点 |
|---|---|---|
| 有 GPU，想本地跑 Qwen/GLM | **vLLM** | 性能最好，支持多并发 |
| 无 GPU，CPU 推理 | **Ollama** | 一键安装，开箱即用 |
| 想接远程模型服务 | OpenAI 兼容的任何端点 | 灵活性强 |

### vLLM 快速启动示例

```bash
# 安装 vLLM
pip install vllm

# 启动服务
vllm serve Qwen/Qwen2.5-7B-Instruct \
    --host 0.0.0.0 \
    --port 8000

# 验证 OpenAI 兼容接口
curl http://localhost:8000/v1/models
```

启动后访问 `http://localhost:8000/v1` 就是标准的 OpenAI API 格式，AgentScope 的 `OpenAIChatModel` 可直接对接。

---

## 踩坑总结

| 错误 | 原因 | 正确写法 |
|---|---|---|
| `io.agentscope.core.message.content.TextBlock` 不存在 | 包路径错误 | `io.agentscope.core.message.TextBlock` |
| `TextBlock.of()` 找不到 | 1.0.4 没有快捷工厂方法 | `TextBlock.builder().text(...).build()` |
| `model.call()` 找不到 | 1.0.4 没有 `call()` 方法 | `model.stream(...).last().block()` |
| `ContentBlock` 无法转换为 `TextBlock` | content 是 `List<ContentBlock>` | `.content(List.of(TextBlock...))` |
| `stream()` 参数不匹配 | 需要三个参数 | `stream(List<Msg>, List<ToolSchema>, GenerateOptions)` |
| SSE 前端拿不到数据 | `Flux<String>` 不自动加 `data:` 前缀 | 用 `SseEmitter` |
| 打字机效果不出来 | 后端没发合法的 SSE 格式 | 修复后端后用 SseEmitter |
| Markdown 不渲染 | `textContent` 直接展示原始文本 | 引入 marked.js 渲染 HTML |
