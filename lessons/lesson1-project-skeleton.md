# Lesson 1 — 项目骨架 + AgentScope 依赖

## 学习目标

搭建一个可运行的 Spring Boot + AgentScope 项目骨架，理解依赖引入方式。

---

## 技术栈

- JDK 21 + Maven
- Spring Boot 3.4.4
- AgentScope Java 1.0.4（含 DashScope 支持）

---

## 知识点

### 1. pom.xml 依赖引入

AgentScope 提供 all-in-one 依赖，包含核心框架 + DashScope 支持 + MCP 协议支持：

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope</artifactId>
    <version>1.0.4</version>
</dependency>
```

不需要额外引入 Spring WebFlux 之外的其他依赖。

### 2. 配置文件 application.yml

```yaml
server:
  port: 8080

agentscope:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}
    model-name: qwen-max

spring:
  application:
    name: agentscope-demo
```

- 环境变量 `${DASHSCOPE_API_KEY}` 从系统读取，避免硬编码
- 模型名 `qwen-max` 是通义千问的旗舰模型

### 3. Spring Boot 主入口

```java
@SpringBootApplication
public class FlowchartAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlowchartAgentApplication.class, args);
    }
}
```

标准的 Spring Boot 启动方式，`@SpringBootApplication` 自动扫描所有组件。

### 4. 项目目录结构

```
agentscope-demo/
── pom.xml
├── src/main/java/com/example/demo/
│   └── FlowchartAgentApplication.java
└── src/main/resources/
    └── application.yml
```

---

## 关键理解

- **AgentScope 1.x** 采用 all-in-one 依赖，不需要分别引入 core、dashscope、mcp 等模块
- **环境变量注入**：通过 `${ENV_VAR}` 语法从系统读取敏感配置，比硬编码更安全
- **Maven 版本管理**：Spring Boot parent POM 统一管理依赖版本，不需要在 pom.xml 中指定每个依赖的版本号
