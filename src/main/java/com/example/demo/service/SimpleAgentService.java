package com.example.demo.service;

import com.example.demo.dto.AgentResponse;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

import java.util.Collections;
import java.util.List;

/**
 * 最简单的 Agent 服务
 *
 * 教学目标：理解 AgentScope 三大核心抽象
 *
 * ┌─────────────────────────────────────────────────┐
 * │ Model = LLM 的封装                              │
 * │   - DashScopeChatModel: 通义千问                │
 * │   - 所有 Model 通过 builder 模式创建            │
 * └─────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────┐
 * │ Msg = 消息结构                                  │
 * │   - role: 发送者角色 (USER/ASSISTANT/SYSTEM)    │
 * │   - content: List<ContentBlock>                 │
 * └─────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────┐
 * │ Agent = 能处理消息的实体                         │
 * │   - 最简形式：Model 本身就是 Agent              │
 * │   - 复杂形式：ReActAgent 包装 Model + Tool      │
 * └─────────────────────────────────────────────────┘
 */
@Service
public class SimpleAgentService {

    private final DashScopeChatModel model;

    /**
     * 构造函数：创建 Model
     *
     * ===== 教学点：Model 的创建 =====
     * DashScopeChatModel 是 AgentScope 对阿里云通义千问的封装
     * 所有 Model 都通过 builder 模式创建，统一接口
     */
    public SimpleAgentService(
            @Value("${agentscope.dashscope.api-key}") String apiKey,
            @Value("${agentscope.dashscope.model-name}") String modelName) {

        this.model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                // 可选配置：temperature、maxTokens 等
                // .temperature(0.7)
                // .maxTokens(2000)
                .build();

        // 注意：Model 创建后就可以直接当 Agent 用了
        // 因为 Model 实现了 Agent 接口
    }

    /**
     * 最简单的 Agent 调用（一次性返回）
     *
     * ===== 教学点：Msg 的构建 =====
     * Msg 是 AgentScope 的消息抽象
     *
     * ===== 教学点：Agent 的最小调用 =====
     * 1.0.4 中 Model 只有 stream() 方法，没有 call()
     * 通过 .stream(msgs, tools, options).last().block() 获取一次性结果
     *
     * stream() 返回 Flux<ChatResponse>，不是 Flux<Msg>
     * ChatResponse.getContent() 返回 List<ContentBlock>
     */
    public AgentResponse chat(String userInput) {
        long start = System.currentTimeMillis();

        // 构建 user 消息
        // ===== 教学点：content 是 List<ContentBlock> =====
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userInput).build()))
                .build();

        // 调用 Model：stream().last().block() 模拟 call()
        ChatResponse response = model.stream(
                List.of(userMsg),
                Collections.emptyList(),  // tools
                null                      // options
        ).last().block();

        long duration = System.currentTimeMillis() - start;

        // 封装响应结果
        // ===== 教学点：从 ChatResponse 提取文本 =====
        String responseText = response.getContent().stream()
                .filter(cb -> cb instanceof TextBlock)
                .map(cb -> ((TextBlock) cb).getText())
                .findFirst()
                .orElse("");

        return new AgentResponse(
                responseText,
                model.getModelName(),
                duration
        );
    }

    /**
     * 流式 Agent 调用（实时返回）
     *
     * ===== 教学点：响应式流式输出 =====
     * AgentScope 使用 Reactor 响应式编程
     *
     * stream() 返回 Flux<ChatResponse>，需要映射为文本流
     *
     * SseEmitter 是 Spring MVC Servlet 堆栈下 SSE 的标准写法
     * 每个文本块通过 emitter.send() 推送，框架自动处理 data: 前缀和 \n\n 分隔符
     */
    public SseEmitter chatStream(String userInput) {
        SseEmitter emitter = new SseEmitter(0L);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userInput).build()))
                .build();

        model.stream(
                List.of(userMsg),
                Collections.emptyList(),  // tools
                null                      // options
        ).flatMap(response ->
                Flux.fromIterable(response.getContent())
                        .filter(cb -> cb instanceof TextBlock)
                        .map(cb -> ((TextBlock) cb).getText())
        ).subscribe(
                text -> {
                    try { emitter.send(text); }
                    catch (IOException e) { emitter.completeWithError(e); }
                },
                emitter::completeWithError,
                emitter::complete
        );

        return emitter;
    }
}
