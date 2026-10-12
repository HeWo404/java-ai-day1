package com.example.javaaiday1.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v2/chat")
public class ChatControllerV2 {
    private final ChatClient chatClient;

    public ChatControllerV2(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostMapping("/analyze")
    public SentimentResult analyze(@RequestBody ChatRequest chatRequest) {
        return chatClient.prompt()
                .system("""
                    你是一个情感分析引擎。
                    分析用户输入的文本情感，严格返回 JSON，不要任何额外说明、不要 markdown 代码块。
                    JSON 格式：{"sentiment": "POSITIVE|NEGATIVE|NEUTRAL", "score": 0.0-1.0, "reason": "简短理由"}
                    """)
                .user(chatRequest.prompt())
                .call()
                .entity(SentimentResult.class);
    }

    public record SentimentResult(
            String sentiment,   //情感倾向：正向、负向、中性
            double score,   //置信度 0.0-1.0
            String reason   //分析理由
    ){}

    @GetMapping(value = "/template")
    public String template(@RequestParam String topic,@RequestParam String level) {
        //根据level 生成不同style 的 instrction
        String styleInstruction = getStyleInstruction(level);
        String systemPrompt = """
                你是一位资深的老师。
                请用以下风格解释{topic}：
                {style}
                并给出一个生活化的例子。
                控制在 150 字以内。
            """;
        return chatClient.prompt()
                .system(s->s.text(systemPrompt)
                        .param("style",styleInstruction)
                        .param("topic",topic))
                .user("请开始讲解！")
                .call()
                .content();
    }

    private String getStyleInstruction(String level) {
        return switch (level) {
            case "小学" -> """
                    用 8 岁小孩能听懂的语言。禁止使用任何专业术语。
                    只能用生活中的比喻，比如玩具、零食、游戏。
                    句子要短，每句话不超过 15 个字。
                    """;
            case "大学" -> """
                    使用计算机科学专业的术语，比如哈希、共识机制、分布式账本、非对称加密。
                    引用比特币或以太坊的实际技术细节。
                    假设读者有编程基础，不需要解释基础概念。
                    """;
            default -> "用通俗易懂的语言解释，适合普通成年人理解。";
        };
    }
    @GetMapping(value = "/stream",produces ="text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest chatRequest){
        return chatClient.prompt()
                .user(chatRequest.prompt)
                .call()
                .content();
    }
    public record ChatRequest(String prompt) {}
}
