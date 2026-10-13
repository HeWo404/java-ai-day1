package com.example.javaaiday1.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                //todo  设置为内存存储历史对话，服务器重启会丢失（生产环境 建议换到redis）
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                //限制每个sessionId 最多20条对话，防止无线叠加。
                .maxMessages(20)
                .build();
    }
}
