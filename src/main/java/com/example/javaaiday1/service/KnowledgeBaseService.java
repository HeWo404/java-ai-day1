package com.example.javaaiday1.service;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.FileReader;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;

    //注入 spring AI 自动配置的EmBeddingModel 并初始化一个向量库
    public KnowledgeBaseService(EmbeddingModel emBeddingModel) {
        this.vectorStore = SimpleVectorStore.builder(emBeddingModel).build();
    }

    @PostConstruct
    public void init() {
        //读取文件内容
        TextReader textReader = new TextReader(new ClassPathResource("docs/company Faq.text"));
        List<Document> documents = textReader.read();

        //把文档进行切分成小片段
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);

        //存入向量数据库
        vectorStore.add(splitDocuments);

        System.out.println("知识库加载完成，共切分"+splitDocuments.size()+"个片段");
    }

    //对外提供向量库检索的方法
    public List<Document> search(String prompt) {
        return vectorStore.similaritySearch(prompt);
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }
}
