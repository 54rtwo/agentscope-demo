# Lesson 3 — Tool 工具 + ReActAgent：理解推理-行动循环

## 学习目标

从"只能生成文本"的 Model，升级到"能执行操作"的 ReActAgent。理解 @Tool 定义、Toolkit 注册、以及框架如何自动管理多工具调用的上下文。

---

## 核心概念：ReAct 模式

```
ReAct = Reasoning（推理） + Acting（行动）
```

| 纯 Model | ReActAgent |
|---------|------------|
| 只能生成文本回复 | 能调用工具执行操作 |
| 用户问"保存文件" → 回复"我建议你保存..." | 用户问"保存文件" → 调用 saveAsHtml 工具 → 真正保存 |
| 无循环 | 推理→行动→观察→再推理，直到完成 |

---

## 三大组件

### 1. @Tool — 定义工具方法

```java
@Component
public class FlowchartTools {

    @Tool(description = "将文本内容保存为 HTML 文件")
    public String saveAsHtml(
            @ToolParam(name = "content", description = "要保存的 HTML 内容") String content,
            @ToolParam(name = "filename", description = "文件名") String filename
    ) {
        // 工具实现
        Files.writeString(outputDir.resolve(filename + ".html"), content);
        return "文件已保存到: " + filePath;
    }
}
```

关键点：
- `@Tool(description)` — LLM 根据描述决定是否调用
- `@ToolParam(name, description)` — LLM 根据描述理解参数含义
- 返回值必须是 `String` — 结果会反馈给 LLM 作为"观察"

### 2. Toolkit — 注册工具

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(flowchartTools);  // 自动扫描 @Tool 方法
```

`registerTool(Object)` 会反射扫描对象上所有 `@Tool` 注解的方法。

### 3. ReActAgent — 创建带工具的 Agent

```java
ReActAgent agent = ReActAgent.builder()
        .name("FlowchartHelper")
        .sysPrompt("你是一个智能助手。当用户请求保存内容时，使用 saveAsHtml 工具。")
        .model(model)           // LLM 负责推理
        .toolkit(toolkit)       // Agent 知道有哪些工具可用
        .maxIters(5)            // 最大循环次数（防止无限循环）
        .build();
```

---

## ReActAgent 调用

```java
Msg userMsg = Msg.builder()
        .role(MsgRole.USER)
        .content(List.of(TextBlock.builder().text(userInput).build()))
        .build();

Msg response = agent.call(userMsg).block();
```

注意：
- **ReActAgent 有真正的 `call()` 方法**（继承自 AgentBase）
- 不需要像 Model 那样用 `stream().last().block()`

---

## 核心机制：框架自动管理上下文

这是 ReActAgent 的核心价值——**开发者不用管多工具调用的上下文传递**。

### 工作流程

```
用户输入 → Memory.addMessage(userMsg)
    ↓
LLM 推理 → Memory.addMessage(assistantMsg + ToolUseBlock)
    ↓
框架执行工具 → Memory.addMessage(ToolResultBlock)
    ↓
框架把 Memory 所有历史喂给 LLM → LLM 看到之前工具结果，决定下一步
    ↓
循环直到 LLM 说"完成"或达到 maxIters
```

### 职责分工

| 职责 | 处理方 | 说明 |
|------|--------|------|
| 记录对话历史 | **Memory** 自动 | 每个 Msg 自动存入 Memory |
| 执行工具调用 | **Toolkit** 自动 | 解析 ToolUseBlock，调用对应方法 |
| 把工具结果喂回 LLM | **ReActAgent** 自动 | 下轮推理时自动带上 ToolResultBlock |
| 决定是否继续调用工具 | **LLM** 自动 | LLM 根据工具结果判断任务是否完成 |
| 防止无限循环 | **maxIters** 配置 | 达到上限强制停止 |

### Memory 结构示例（调用 2 个工具）

```
Memory.messages:
  [0] Msg(role=USER, content=[TextBlock("请保存hello.html")])
  [1] Msg(role=ASSISTANT, content=[ToolUseBlock(name="getCurrentTime")])
  [2] Msg(role=TOOL, content=[ToolResultBlock("2026-05-09 01:00:57")])
  [3] Msg(role=ASSISTANT, content=[ToolUseBlock(name="saveAsHtml", input={content, filename})])
  [4] Msg(role=TOOL, content=[ToolResultBlock("文件已保存到: /path/hello.html")])
  [5] Msg(role=ASSISTANT, content=[TextBlock("文件已保存完成")])
```

框架在第 5 步把 Memory 所有历史喂给 LLM 时，LLM 能看到：
- 用户原始请求
- 自己之前调用了哪些工具
- 每个工具返回什么结果

**开发者只需要：**
1. 定义 `@Tool` 方法
2. 用 `Toolkit.registerTool()` 注册
3. 调用 `agent.call(msg)` — 一行代码

**框架负责整个 ReAct 循环的上下文传递。**

---

## 从 Memory 中提取工具调用记录

```java
List<String> toolCalls = agent.getMemory().getMessages().stream()
        .flatMap(msg -> msg.getContent().stream())
        .filter(cb -> cb instanceof ToolUseBlock)
        .map(cb -> {
            ToolUseBlock tub = (ToolUseBlock) cb;
            return tub.getName() + "(" + tub.getInput() + ")";
        })
        .toList();
```

可用于：
- 可观测性日志
- 前端展示 Agent 调用了哪些工具
- 分析 Agent 行为

---

## 对比：纯 Model vs ReActAgent

| 方面 | 纯 Model | ReActAgent |
|------|---------|------------|
| 调用方式 | `model.stream(...).last().block()` | `agent.call(msg).block()` |
| 工具能力 | 无 | 有（通过 Toolkit） |
| 循环机制 | 无 | 推理→行动→观察→再推理 |
| 上下文管理 | 手动传 Msg 列表 | Memory 自动管理 |
| 返回类型 | `ChatResponse` | `Msg` |

---

## API 层对比

```java
// ReActAgent 调用（有工具）
@PostMapping("/chat")
public AgentResponse chat(@RequestBody String userInput) {
    return agentService.chat(userInput);  // 调用 ReActAgent
}

// 纯 Model 调用（无工具，对比基准）
@PostMapping("/chat/simple")
public AgentResponse chatSimple(@RequestBody String userInput) {
    return agentService.simpleChat(userInput);  // 调用纯 Model
}
```

---

## 测试验证

**触发工具调用的请求：**
```bash
curl -X POST http://localhost:8080/api/test/chat \
  -H "Content-Type: text/plain" \
  -d "请把'Hello World'保存为 hello.html"
```

预期：
- `toolCalls` 字段显示 `["saveAsHtml(...)"]`
- `modelUsed` 显示 `"MiniMax-M2.5 + ReActAgent"`
- `output/hello.html` 文件被创建

**纯 Model 的请求：**
```bash
curl -X POST http://localhost:8080/api/test/chat/simple \
  -H "Content-Type: text/plain" \
  -d "请把'Hello World'保存为 hello.html"
```

预期：
- 只返回文本建议，不会真正保存文件
- `toolCalls` 为空

---

## 踩坑总结

| 错误 | 原因 | 正确写法 |
|------|------|---------|
| `ToolUseBlock` 在响应中找不到 | Memory 中 ToolUseBlock 在中间消息，不是最终响应 | 从 `agent.getMemory().getMessages()` 中提取 |
| 工具没被调用 | sysPrompt 没引导 LLM 使用工具 | sysPrompt 中明确"当 X 时使用 Y 工具" |
| Agent 无限循环 | maxIters 太大或任务无法完成 | 设置合理的 maxIters（如 5） |
| 工具参数错误 | @ToolParam description 不够清晰 | 写清楚参数含义和格式 |

---

## 关键理解

> **你写工具，框架管流程。**

ReActAgent 的设计哲学：
- 工具定义（@Tool）是业务逻辑 → 开发者写
- 推理-行动循环、上下文传递、Memory 管理 → 框架自动
- maxIters 是安全兜底 → 配置即可

开发者只需要关心"工具做什么"，不需要关心"怎么在多工具之间传递上下文"。