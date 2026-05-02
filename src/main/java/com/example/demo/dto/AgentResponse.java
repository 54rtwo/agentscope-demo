package com.example.demo.dto;

/**
 * Agent 响应结果
 *
 * 用于封装 Agent 调用的返回信息，方便前端展示
 */
public record AgentResponse(
    String content,      // Agent 返回的文本内容
    String modelUsed,    // 使用的模型标识（如 "qwen-max"）
    long durationMs      // 调用耗时（毫秒）
) {}