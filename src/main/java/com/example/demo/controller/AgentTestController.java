package com.example.demo.controller;

import com.example.demo.dto.AgentResponse;
import com.example.demo.service.SimpleAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 测试接口
 *
 * 提供两组对比接口：
 * - /chat        → ReActAgent（有工具）
 * - /chat/simple → 纯 Model（无工具）
 */
@RestController
@RequestMapping("/api/test")
public class AgentTestController {

    private final SimpleAgentService agentService;

    public AgentTestController(SimpleAgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * ReActAgent 调用（有工具）
     *
     * 示例测试：
     * curl -X POST http://localhost:8080/api/test/chat \
     *   -H "Content-Type: text/plain" \
     *   -d "你好"
     *
     * curl -X POST http://localhost:8080/api/test/chat \
     *   -H "Content-Type: text/plain" \
     *   -d "请把'Hello World'保存为 hello.html"
     */
    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody String userInput) {
        return agentService.chat(userInput);
    }

    /**
     * 纯 Model 调用（无工具，对比基准）
     *
     * 示例测试：
     * curl -X POST http://localhost:8080/api/test/chat/simple \
     *   -H "Content-Type: text/plain" \
     *   -d "请把'Hello World'保存为 hello.html"
     */
    @PostMapping("/chat/simple")
    public AgentResponse chatSimple(@RequestBody String userInput) {
        return agentService.simpleChat(userInput);
    }

    /**
     * 流式输出（纯 Model）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam("input") String userInput) {
        return agentService.chatStream(userInput);
    }
}
