package com.example.javaaiday1.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RestClient restClient = RestClient.create();

    // 👇 替换成你注册的 API Key
    private static final String API_KEY = "xxxx";

    @PostMapping
    public String chat(@RequestBody Map<String, String> request) {
        String prompt = request.get("prompt");

        // 构造云端 API 的请求体（以 DeepSeek 格式为例）
        Map<String, Object> body = Map.of(
                "model", "glm-5.3",  // 或智谱的 "glm-4"
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "stream", false,
                "temperature",1
        );


        Map response = restClient.post()
                .uri("https://open.bigmodel.cn/api/paas/v4/chat/completions") // 或智谱的 URL
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(Map.class);


        // 提取回复内容（DeepSeek/智谱格式略有不同，仔细看文档）
        List choices = (List) response.get("choices");
        Map firstChoice = (Map) choices.get(0);
        Map message = (Map) firstChoice.get("message");
        return (String) message.get("content");
    }
}