package com.example.demo.dto;

import java.util.Collections;
import java.util.List;

/**
 * Agent 响应结果
 */
public record AgentResponse(
    String content,
    String modelUsed,
    long durationMs,
    List<String> toolCalls     // 工具调用记录（仅 ReActAgent 有值）
) {
    public AgentResponse(String content, String modelUsed, long durationMs) {
        this(content, modelUsed, durationMs, Collections.emptyList());
    }
}