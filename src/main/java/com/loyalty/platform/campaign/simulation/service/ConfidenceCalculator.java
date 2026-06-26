package com.loyalty.platform.campaign.simulation.service;

import org.springframework.stereotype.Component;

/**
 * 置信度计算器 — 基于样本量和模型稳定性计算模拟结果的置信度。
 */
@Component
public class ConfidenceCalculator {

    /**
     * 计算置信度。
     *
     * @param sampleSize      样本量
     * @param modelStability  模型稳定性（0~1，来自 ML 服务）
     * @return 置信度（0~1）
     */
    public double calculateConfidence(int sampleSize, double modelStability) {
        double sampleFactor = Math.min(sampleSize / 1000.0, 1.0);
        double stabilityFactor = Math.min(modelStability, 1.0);
        return 0.6 * sampleFactor + 0.4 * stabilityFactor;
    }
}
