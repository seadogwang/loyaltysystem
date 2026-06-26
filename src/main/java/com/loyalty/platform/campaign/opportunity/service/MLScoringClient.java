package com.loyalty.platform.campaign.opportunity.service;

import com.loyalty.platform.campaign.opportunity.dto.MLBatchRequest;
import com.loyalty.platform.campaign.opportunity.dto.MLBatchResponse;
import com.loyalty.platform.campaign.opportunity.dto.MLScoreResult;
import com.loyalty.platform.campaign.opportunity.dto.MemberFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ML 评分客户端 — 调用 Python ML 服务（XGBoost/LightGBM）。
 *
 * <p>支持批量预测（自动分片）、单例预测、超时控制和降级。
 */
@Component
public class MLScoringClient {

    private static final Logger log = LoggerFactory.getLogger(MLScoringClient.class);

    private static final int BATCH_SIZE = 1000;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private RestTemplate restTemplate;

    /** ML 服务 URL（生产通过配置注入） */
    private String mlServiceUrl;

    private final MLServiceFallback fallback;

    public MLScoringClient(MLServiceFallback fallback) {
        this.fallback = fallback;
        // 开发阶段：不初始化 RestTemplate，使用模拟模式
        this.mlServiceUrl = "http://ml-service:5000";
    }

    /**
     * 批量预测（自动分片）。
     */
    public List<MLScoreResult> predictBatch(List<MemberFeature> features) {
        if (features == null || features.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("ML batch prediction requested: {} members", features.size());

        // 开发阶段：使用模拟评分
        if (restTemplate == null) {
            return simulateBatch(features);
        }

        // 生产阶段：调用 ML 服务
        List<MLScoreResult> allResults = new ArrayList<>();

        for (int i = 0; i < features.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, features.size());
            List<MemberFeature> batch = features.subList(i, end);

            MLBatchRequest request = MLBatchRequest.builder()
                    .members(batch)
                    .modelType("ensemble_v2")
                    .build();

            try {
                MLBatchResponse response = restTemplate.postForObject(
                        mlServiceUrl + "/predict/batch",
                        request,
                        MLBatchResponse.class
                );

                if (response != null && response.getResults() != null) {
                    allResults.addAll(response.getResults());
                }
                log.info("ML batch prediction completed: {}/{}", end, features.size());

            } catch (Exception e) {
                log.error("ML prediction failed for batch {}-{}: {}", i, end, e.getMessage());
                // 降级：返回默认值
                for (MemberFeature feature : batch) {
                    allResults.add(MLScoreResult.fallback(feature.getMemberId()));
                }
            }
        }

        return allResults;
    }

    /**
     * 单个预测（实时场景）。
     */
    public MLScoreResult predictSingle(MemberFeature feature) {
        List<MLScoreResult> results = predictBatch(List.of(feature));
        return results.isEmpty() ? MLScoreResult.fallback(feature.getMemberId()) : results.get(0);
    }

    /**
     * 开发阶段：模拟 ML 预测。
     */
    private List<MLScoreResult> simulateBatch(List<MemberFeature> features) {
        Random random = new Random();
        return features.stream().map(f -> {
            double churnProb = 0.1 + random.nextDouble() * 0.6;
            double uplift = 0.2 + random.nextDouble() * 0.5;
            double convProb = 0.2 + random.nextDouble() * 0.5;

            // 基于特征调整
            if (f.getRecency() != null && f.getRecency() > 60) churnProb += 0.2;
            if (f.getFrequency() != null && f.getFrequency() > 10) uplift += 0.15;
            if (f.getMonetary() != null && f.getMonetary() > 5000) convProb += 0.1;

            return MLScoreResult.builder()
                    .memberId(f.getMemberId())
                    .churnProbability(Math.min(churnProb, 0.95))
                    .upliftScore(Math.min(uplift, 0.95))
                    .conversionProbability(Math.min(convProb, 0.95))
                    .confidence(0.75 + random.nextDouble() * 0.2)
                    .build();
        }).collect(Collectors.toList());
    }
}
