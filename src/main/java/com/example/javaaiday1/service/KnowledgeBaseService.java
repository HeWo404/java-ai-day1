package com.example.javaaiday1.service;

import com.example.javaaiday1.util.HybridRetriever;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private final static Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final SimpleVectorStore vectorStore;
    private final Resource knowledgeSoure;  //语言库url
    private final String storeDir;  //向量库缓存路径
    private final int chunkSize = 90;
    private List<Document> allChunk = new ArrayList<Document>();
    private final HybridRetriever hybridRetriever;

    //注入 spring AI 自动配置的EmBeddingModel 并初始化一个向量库
    public KnowledgeBaseService(EmbeddingModel emBeddingModel,HybridRetriever hybridRetriever,
                                @Value("${app.knowledge.source}") Resource knowledgeSoure,
                                @Value("${app.vector-store.dir}") String storeDir) {
        this.vectorStore = SimpleVectorStore.builder(emBeddingModel).build();
        this.knowledgeSoure = knowledgeSoure;
        this.storeDir = storeDir;
        this.hybridRetriever =hybridRetriever;
    }

    @PostConstruct
    public void init() throws IOException, NoSuchAlgorithmException {
        //文件名
        String cacheKey = sha256(knowledgeSoure.getInputStream().readAllBytes())+"-"+chunkSize;
        //生成文件目录
        Files.createDirectories(Path.of(storeDir));
        //生成文件
        File cacheFile = Path.of(storeDir, "vector-store-" + cacheKey + ".json").toFile();
        if(cacheFile.exists()){
            //todo 修改文件时需要强制删除缓存文件
            //判断是否有缓存，有则用缓存的文件
            vectorStore.load(cacheFile);
            if(allChunk == null || allChunk.isEmpty()){
                //读取文件内容
                TextReader textReader = new TextReader(knowledgeSoure);
                List<Document> documents = textReader.read();
                List<Document> splitDocuments = TokenTextSplitter.builder()
                        .withChunkSize(chunkSize)
                        .withMaxNumChunks(1000)
                        .build()
                        .apply(documents);
                //存入内存用于词法检索
                this.allChunk = splitDocuments;
            }
            log.info("命中向量缓存 {}，跳过向量化", cacheFile.getName());
            return;
        }
        //读取文件内容
        TextReader textReader = new TextReader(knowledgeSoure);
        List<Document> documents = textReader.read();

        //把文档进行切分成小片段
//        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMaxNumChunks(1000)
                .build()
                .apply(documents);
        //存入内存用于词法检索
        this.allChunk = splitDocuments;
        //存入向量库
        vectorStore.add(splitDocuments);
        //把内容写进向量文件
        vectorStore.save(cacheFile);

        log.info("向量库重建完成，切分 {} 个片段，已写入 {}", splitDocuments.size(), cacheFile.getAbsolutePath());
    }

    /**
     *生成向量库的文件名，语义文件内容的哈希后前12位。
     */
    private String sha256(byte[] bytes)  throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        bytes = digest.digest(bytes);
        return HexFormat.of().formatHex(bytes).substring(0,12);
    }

    //对外提供纯向量库检索的方法
    public List<Document> search(String prompt,int topK) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(prompt)
                .topK(topK)    //最多召回几条 限制塞进 prompt 的 context 数量，每条都占 token，多了费钱还稀释重点。
                //todo 0.3 为粗切分下的权宜值，切分细化+评测建好后重新标定
                .similarityThreshold(0.3)   //相似度及格线。余弦相似度范围 0~1，低于这条线的 chunk 直接丢弃。默认 0.0 等于“没有及格线”：哪怕用户问了一个语料里压根没有的问题，系统照样把“最像的 4 条”硬塞进 prompt。
                .build();

        List<Document> docs = vectorStore.similaritySearch(searchRequest);
//        docs.forEach(d -> log.info("召回 score={} id={}", d.getScore(), d.getId()));
        return docs;
    }

    //混合检索方法
    public List<Document> hybridSearch(String query, int finalTopK) {
        // 1. 先拿 Top-20 的向量结果作为候选池
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(20)
                .similarityThreshold(0.3)
                .build();
        List<Document> vectorResults = vectorStore.similaritySearch(request);

        // 2. 丢给 HybridRetriever 进行 RRF 融合排序，返回最终的 Top-K
        return hybridRetriever.hybridSearch(query, allChunk, vectorResults, finalTopK);
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }


}
