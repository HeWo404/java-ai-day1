package com.example.javaaiday1.util;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class HybridRetriever {

    private static final int K = 60; // RRF 常数

    public List<Document> hybridSearch(String query, List<Document> allChunks,
                                       List<Document> vectorResults, int topK) {

        // 1. 向量检索的排名映射 (docId -> rank)
        Map<String, Integer> vectorRanks = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            vectorRanks.put(vectorResults.get(i).getId(), i + 1);
        }

        // 2. 简易词法检索 (计算 Jaccard 相似度作为基础分，然后排名)
        // 提取查询的 2-gram 特征
        Set<String> queryGrams = getBigrams(query.toLowerCase());

        Map<String, Double> lexicalScores = new HashMap<>();
        for (Document doc : allChunks) {
            Set<String> docGrams = getBigrams(doc.getText().toLowerCase());
            // 计算交集大小
            long intersection = queryGrams.stream().filter(docGrams::contains).count();
            // 简化的 Jaccard 相似度 (交集 / 查询长度，偏向短文本)
            double score = (double) intersection / queryGrams.size();
            lexicalScores.put(doc.getId(), score);
        }

        // 3. 词法检索排名映射 (docId -> rank)
        List<Map.Entry<String, Double>> sortedLexical = lexicalScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20) // 取前 20
                .collect(Collectors.toList());

        Map<String, Integer> lexicalRanks = new HashMap<>();
        for (int i = 0; i < sortedLexical.size(); i++) {
            lexicalRanks.put(sortedLexical.get(i).getKey(), i + 1);
        }

        // 4. RRF 融合计算
        Map<String, Double> rrfScores = new HashMap<>();

        // 收集所有出现过的 docId
        Set<String> allDocIds = new HashSet<>();
        allDocIds.addAll(vectorRanks.keySet());
        allDocIds.addAll(lexicalRanks.keySet());

        for (String docId : allDocIds) {
            double score = 0.0;
            // 如果在向量结果里，加上向量排名的分
            if (vectorRanks.containsKey(docId)) {
                score += 1.0 / (K + vectorRanks.get(docId));
            }
            // 如果在词法结果里，加上词法排名的分
            if (lexicalRanks.containsKey(docId)) {
                score += 1.0 / (K + lexicalRanks.get(docId));
            }
            rrfScores.put(docId, score);
        }

        // 5. 按融合后的分数降序排序，取 topK
        List<String> finalDocIds = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 6. 映射回 Document 对象
        Map<String, Document> docMap = allChunks.stream()
                .collect(Collectors.toMap(Document::getId, doc -> doc));

        return finalDocIds.stream()
                .map(docMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // 辅助方法：提取字符串的 2-gram 集合 (例如 "发票" -> ["发票"])
    private Set<String> getBigrams(String text) {
        Set<String> bigrams = new HashSet<>();
        String cleanText = text.replaceAll("[\\p{P}\\s]+", ""); // 去掉标点和空格
        for (int i = 0; i < cleanText.length() - 1; i++) {
            bigrams.add(cleanText.substring(i, i + 2));
        }
        return bigrams;
    }
}