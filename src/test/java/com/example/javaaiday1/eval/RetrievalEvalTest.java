package com.example.javaaiday1.eval;

import com.example.javaaiday1.service.KnowledgeBaseService;
import com.example.javaaiday1.util.HybridRetriever;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

@SpringBootTest
@JsonIgnoreProperties(ignoreUnknown = true)
class RetrievalEvalTest {
    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    @Autowired
    private HybridRetriever hybridRetriever;

    record EvalItem(String question, String goldSnippet, String expectedAnswer, String type) {}

    @Test
    //纯向量检索测试方法
    void retrievalMetrics() throws Exception {
        List<EvalItem> items = loadEvalSet();          // Jackson 读 classpath:eval/eval-set.json
        int[] ks = {1, 3, 5};
        int[] hits = new int[ks.length];
        double mrrSum = 0;

        System.out.println("| question | type | rank |");
        System.out.println("|---|---|---|");

        for (EvalItem item : items) {

//            if (item.goldSnippet().isEmpty()) continue;   // irrelevant 跳过，day7 再评
            List<Document> docs = knowledgeBaseService.search(item.question(), 5);

            int rank = -1;
            for (int i = 0; i < docs.size(); i++) {
                if (docs.get(i).getText().contains(item.goldSnippet())) { rank = i + 1; break; }
                //为什么是i+1，不是直接+1? 这个循环是为了找到正确答案的排名，所以是i+1；
            }
            if (rank > 0) {
                for (int k = 0; k < ks.length; k++) if (rank <= ks[k]) hits[k]++;
                mrrSum += 1.0 / rank;
            }
            System.out.printf("| %s | %s | %s |%n", shorten(item.question()), item.type(), rank < 0 ? "MISS" : rank);
        }

        System.out.printf("recall@1=%.0f%%  recall@3=%.0f%%  recall@5=%.0f%%  MRR=%.3f%n",
                100.0 * hits[0] / items.size(),
                100.0 * hits[1] / items.size(),
                100.0 * hits[2] / items.size(),
                mrrSum / items.size());
    }

    @Test
    //混合检索 (BM25 + 向量 RRF)测试方法
    void hybridRetrievalMetrics() throws Exception {
        List<EvalItem> items = loadEvalSet();
        int[] ks = {1, 3, 5};
        int[] hits = new int[ks.length];
        double mrrSum = 0;
        System.out.println("混合检索（BM25+向量RRF）测试");
        System.out.println("| question | type | rank |");
        System.out.println("|---|---|---|");

        for (EvalItem item : items) {
            List<Document> docs = knowledgeBaseService.hybridSearch(item.question(), 5);
            int rank = -1;
            for (int i = 0; i < docs.size(); i++) {
                if(docs.get(i).getText().contains(item.goldSnippet())){ rank = i+1; break; }
            }
            if (rank > 0) {
                for (int k=0;k<ks.length;k++){
                    if (rank <= ks[k]) hits[k]++;
                }
                mrrSum += 1.0 / rank;
            }
            System.out.printf("| %s | %s | %s |%n", shorten(item.question()), item.type(), rank < 0 ? "MISS" : rank);
        }
        System.out.printf("混合检索结果: recall@1=%.0f%%  recall@3=%.0f%%  recall@5=%.0f%%  MRR=%.3f%n",
                100.0 * hits[0] / items.size(),
                100.0 * hits[1] / items.size(),
                100.0 * hits[2] / items.size(),
                mrrSum / items.size());
    }

    // loadEvalSet(): ObjectMapper + new TypeReference<List<EvalItem>>(){}
    private List<EvalItem> loadEvalSet() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new ClassPathResource("eval/eval-set.json").getInputStream()) {
            return mapper.readValue(is, new TypeReference<List<EvalItem>>() {
            });
        }
    }

    // shorten(): 文字过长时，截断 question 便于表格显示
    private String shorten(String s) {
        int maxLen = 20;
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
