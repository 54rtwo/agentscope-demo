package com.example.demo.service;

import com.example.demo.dto.AgentResponse;
import com.example.demo.tools.FlowchartTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Agent 服务
 *
 * 提供两种 Agent 调用方式：
 * 1. simpleChat() — 纯 Model 调用（无工具），对比基准
 * 2. chat()       — ReActAgent 调用（有工具），真正的 Agent
 */
@Service
public class SimpleAgentService {

    private final DashScopeChatModel model;
    private final ReActAgent reactAgent;

    /**
     * 构造函数：同时创建 Model 和 ReActAgent
     *
     * ===== 教学点：Toolkit 注册工具 =====
     * Toolkit 是工具的容器。
     * registerTool(Object) 会扫描传入对象上所有 @Tool 注解的方法。
     */
    public SimpleAgentService(
            FlowchartTools flowchartTools,
            @Value("${agentscope.dashscope.api-key}") String apiKey,
            @Value("${agentscope.dashscope.model-name}") String modelName) {

        // 1. 创建 Model
        this.model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();

        // 2. 创建 Toolkit 并注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(flowchartTools);

        // ===== 教学点：ReActAgent 创建 =====
        // ReActAgent 是能"推理→行动"循环的 Agent
        // - model: LLM 负责推理
        // - toolkit: 让 Agent 知道有哪些工具可用
        // - maxIters: 最大循环次数（防止无限循环）
        // - sysPrompt: 定义 Agent 的角色和行为准则
        this.reactAgent = ReActAgent.builder()
                .name("FlowchartHelper")
                .sysPrompt("你是一个智能助手。当用户请求保存内容时，使用 saveAsHtml 工具。")
                .model(this.model)
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /**
     * 纯 Model 调用（无工具，对比基准）
     *
     * ===== 教学点：Model 本身就是最简单的 Agent =====
     * 这种方式下，LLM 只能生成文本回复，无法执行实际操作。
     */
    public AgentResponse simpleChat(String userInput) {
        long start = System.currentTimeMillis();

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userInput).build()))
                .build();

        ChatResponse response = model.stream(
                List.of(userMsg),
                Collections.emptyList(),
                null
        ).last().block();

        long duration = System.currentTimeMillis() - start;

        String responseText = response.getContent().stream()
                .filter(cb -> cb instanceof TextBlock)
                .map(cb -> ((TextBlock) cb).getText())
                .findFirst()
                .orElse("");

        return new AgentResponse(
                responseText,
                model.getModelName() + " (无工具)",
                duration
        );
    }

    /**
     * ReActAgent 调用（有工具）
     *
     * ===== 教学点：ReActAgent 的调用 =====
     * 调用方式和纯 Model 一样：agent.call(msg).block()
     * 但内部执行 ReAct 循环：
     *   1. Reasoning — LLM 推理：用户想要什么？应该用什么工具？
     *   2. Acting    — 如果决定调用工具 → 执行工具 → 获取结果
     *   3. 把工具结果反馈给 LLM → 继续推理
     *   4. 直到 LLM 认为任务完成
     */
    public AgentResponse chat(String userInput) {
        long start = System.currentTimeMillis();

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userInput).build()))
                .build();

        // ===== 教学点：ReActAgent.call() 返回 Mono<Msg> =====
        // 注意：ReActAgent 有真正的 call() 方法（继承自 AgentBase）
        // 不需要像 Model 那样用 stream().last().block()
        Msg response = reactAgent.call(userMsg).block();

        long duration = System.currentTimeMillis() - start;

        String responseText = response.getContent().stream()
                .filter(cb -> cb instanceof TextBlock)
                .map(cb -> ((TextBlock) cb).getText())
                .findFirst()
                .orElse("");

        return new AgentResponse(
                responseText,
                model.getModelName() + " + ReActAgent",
                duration
        );
    }

    /**
     * 流式调用（ReActAgent）
     *
     * 注意：ReActAgent 的流式输出比较复杂，因为中间涉及工具调用。
     * 当前版本先提供纯 Model 的流式作为对比。
     */
    public SseEmitter chatStream(String userInput) {
        SseEmitter emitter = new SseEmitter(0L);

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(userInput).build()))
                .build();

        model.stream(
                List.of(userMsg),
                Collections.emptyList(),
                null
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

    /**
     * 暴露 Toolkit（供后续 Task 查看已注册工具）
     */
    public ReActAgent getAgent() {
        return reactAgent;
    }
}
