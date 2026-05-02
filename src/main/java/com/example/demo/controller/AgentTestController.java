package com.example.demo.controller;

import com.example.demo.dto.AgentResponse;
import com.example.demo.service.SimpleAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Agent 测试接口
 */
@RestController
@RequestMapping("/api/test")
public class AgentTestController {

    private final SimpleAgentService agentService;

    public AgentTestController(SimpleAgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 一次性返回接口
     */
    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody String userInput) {
        return agentService.chat(userInput);
    }

    /**
     * 流式返回接口（SSE）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam("input") String userInput) {
        return agentService.chatStream(userInput);
    }
}