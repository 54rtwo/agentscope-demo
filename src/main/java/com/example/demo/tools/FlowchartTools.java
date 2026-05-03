package com.example.demo.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 流程图工具集
 *
 * ===== 教学点：@Tool 注解 =====
 * 定义一个 Java 方法为 Agent 可用的工具。
 * AgentScope 会通过反射扫描 @Tool 注解的方法，
 * 自动生成工具描述（schema），发送给 LLM。
 *
 * LLM 看到工具描述后，会自行决定是否需要调用此工具。
 *
 * ===== 教学点：@ToolParam 注解 =====
 * 标注方法参数，LLM 会根据 description 理解每个参数的含义。
 */
@Component
public class FlowchartTools {

    /**
     * 工具 1：保存 HTML 文件
     *
     * LLM 看到的工具描述示例：
     *   saveAsHtml(content: 要保存的HTML内容, filename: 文件名)
     *   → 将文本内容保存为 HTML 文件
     */
    @Tool(description = "将文本内容保存为 HTML 文件。当需要保存流程图、报告等内容时使用此工具。")
    public String saveAsHtml(
            @ToolParam(name = "content", description = "要保存的 HTML 内容") String content,
            @ToolParam(name = "filename", description = "文件名（不含扩展名）") String filename
    ) {
        try {
            Path outputDir = Paths.get("output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            Path filePath = outputDir.resolve(filename + ".html");
            Files.writeString(filePath, content);

            return "文件已保存到: " + filePath.toAbsolutePath();
        } catch (IOException e) {
            return "保存失败: " + e.getMessage();
        }
    }

    /**
     * 工具 2：获取当前时间
     *
     * 这是一个无参数工具，演示最简单的 Tool 用法。
     */
    @Tool(description = "获取当前日期时间，用于在文件名中加入时间戳。")
    public String getCurrentTime() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
