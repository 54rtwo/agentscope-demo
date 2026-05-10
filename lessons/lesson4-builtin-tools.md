# Lesson 4 — 内置 Tool：ReadFileTool 与 WriteFileTool

## 学习目标

了解 AgentScope 1.0.4 提供的内置文件操作工具，理解如何直接使用预置工具而非自己定义。

---

## 内置 Tool vs 自定义 Tool

| 方式 | 适用场景 | 代码量 |
|------|---------|--------|
| **自定义 @Tool** | 业务特定逻辑（如 Mermaid 渲染、特定格式保存） | 需写方法体 |
| **内置 Tool** | 通用操作（文件读写、目录遍历） | 直接实例化注册 |

AgentScope 1.0.4 内置两个文件操作 Tool：
- `ReadFileTool` — 读文本文件、列目录
- `WriteFileTool` — 写文本文件、插入内容

---

## ReadFileTool — 文件读取工具

### 提供的方法

| 方法 | 功能 | 参数 |
|------|------|------|
| `viewTextFile` | 读取文本文件内容（带行号） | `path`, `encoding` |
| `listDirectory` | 列出目录内容 | `path` |

### 使用方式

```java
// 创建实例（可指定 baseDir 限制访问范围）
ReadFileTool readFileTool = new ReadFileTool();  // 无限制
// 或限制在某个目录下
ReadFileTool readFileTool = new ReadFileTool("/Users/54rtwo/project");

// 注册到 Toolkit
Toolkit toolkit = new Toolkit();
toolkit.registerAgentTool(readFileTool);
```

**注意**：内置 Tool 是 `AgentTool` 接口实现，用 `registerAgentTool()` 注册（不是 `registerTool()`）。

### LLM 视角

注册后，LLM 能看到两个可用工具：
- `viewTextFile(path, encoding)` — 读取文件
- `listDirectory(path)` — 列出目录

LLM 会根据用户请求自行决定调用哪个。

---

## WriteFileTool — 文件写入工具

### 提供的方法

| 方法 | 功能 | 参数 |
|------|------|------|
| `writeTextFile` | 写入/覆盖文本文件 | `path`, `content`, `encoding` |
| `insertTextFile` | 在指定行插入内容 | `path`, `content`, `lineNumber` |

### 使用方式

```java
WriteFileTool writeFileTool = new WriteFileTool();
// 或限制写入范围
WriteFileTool writeFileTool = new WriteFileTool("/Users/54rtwo/output");

Toolkit toolkit = new Toolkit();
toolkit.registerAgentTool(writeFileTool);
```

---

## 完整示例：让 Agent 操作文件

```java
@Service
public class FileAgentService {

    private final ReActAgent agent;

    public FileAgentService(
            @Value("${agentscope.dashscope.api-key}") String apiKey,
            @Value("${agentscope.dashscope.model-name}") String modelName) {

        // 1. 创建 Model
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        // 2. 创建 Toolkit 并注册内置 Tool
        Toolkit toolkit = new Toolkit();
        toolkit.registerAgentTool(new ReadFileTool());   // 文件读取
        toolkit.registerAgentTool(new WriteFileTool());  // 文件写入

        // 3. 创建 ReActAgent
        this.agent = ReActAgent.builder()
                .name("FileOperator")
                .sysPrompt("你是一个文件操作助手。可以使用 viewTextFile、listDirectory、writeTextFile 工具操作文件。")
                .model(model)
                .toolkit(toolkit)
                .maxIters(10)
                .build();
    }

    public Msg operate(String instruction) {
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(instruction).build()))
                .build();
        return agent.call(userMsg).block();
    }
}
```

---

## 测试请求示例

```bash
# 列出目录
curl -X POST http://localhost:8080/api/file/operate \
  -H "Content-Type: text/plain" \
  -d "列出 /Users/54rtwo/project 目录下的所有文件"

# 读取文件
curl -X POST http://localhost:8080/api/file/operate \
  -H "Content-Type: text/plain" \
  -d "读取 /Users/54rtwo/project/README.md 的内容"

# 写入文件
curl -X POST http://localhost:8080/api/file/operate \
  -H "Content-Type: text/plain" \
  -d "把 'Hello World' 写入 /Users/54rtwo/output/test.txt"
```

---

## 内置 Tool vs 自定义 @Tool 对比

| 方面 | 内置 Tool（ReadFileTool/WriteFileTool） | 自定义 @Tool |
|------|----------------------------------------|-------------|
| 注册方式 | `toolkit.registerAgentTool(tool)` | `toolkit.registerTool(obj)` |
| 接口类型 | 实现 `AgentTool` 接口 | `@Tool` 注解的方法 |
| 工具数量 | 一个实例提供多个方法（viewTextFile + listDirectory） | 一个方法 = 一个工具 |
| 灵活性 | 固定功能，无法修改 | 完全自定义逻辑 |
| 适用场景 | 文件读写、目录遍历等通用操作 | 业务特定逻辑 |

---

## baseDir 参数：安全限制

```java
// 无限制：Agent 可以访问任意路径（危险！）
new ReadFileTool();
new WriteFileTool();

// 限制范围：Agent 只能操作 baseDir 下的文件
new ReadFileTool("/safe/output/dir");
new WriteFileTool("/safe/output/dir");
```

生产环境强烈建议设置 `baseDir`，防止 Agent 读取/写入敏感文件。

---

## 关键理解

> 内置 Tool = 开箱即用的通用能力，自定义 @Tool = 业务特定逻辑

设计原则：
- 通用操作（文件、网络、数据库）→ 尽量用内置 Tool
- 业务特定逻辑（渲染流程图、调用业务 API）→ 自定义 @Tool
- 混合使用：一个 Toolkit 可以同时注册内置和自定义工具

```java
Toolkit toolkit = new Toolkit();
toolkit.registerAgentTool(new ReadFileTool());      // 内置
toolkit.registerAgentTool(new WriteFileTool());     // 内置
toolkit.registerTool(new FlowchartTools());         // 自定义
```

Agent 会根据 sysPrompt 和工具描述自动选择合适的工具调用。

---

## 踩坑总结

| 错误 | 原因 | 正确写法 |
|------|------|---------|
| `registerTool()` 无法注册内置 Tool | 内置 Tool 是 AgentTool 接口，不是 @Tool 方法 | 用 `registerAgentTool()` |
| Agent 访问了敏感文件 | 未设置 baseDir | `new ReadFileTool("/safe/dir")` |
| LLM 不知道有哪些工具可用 | sysPrompt 没提到工具 | sysPrompt 中列出工具名和用途 |