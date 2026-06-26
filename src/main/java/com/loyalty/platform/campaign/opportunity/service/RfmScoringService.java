package com.loyalty.platform.campaign.opportunity.service;

import com.loyalty.platform.campaign.opportunity.entity.CampaignMemberDim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RFM 评分引擎 — 基于会员的 Recency、Frequency、Monetary 计算基础分。
 *
 * <p>评分方法：将全体会员按各维度排序后分为 5 等分（1-5 分）。
 */
@Service
public class RfmScoringService {

    private static final Logger log = LoggerFactory.getLogger(RfmScoringService.class);

    /** Recency 权重 */
    private static final double WEIGHT_RECENCY = 0.4;
    /** Frequency 权重 */
    private static final double WEIGHT_FREQUENCY = 0.3;
    /** Monetary 权重 */
    private static final double WEIGHT_MONETARY = 0.3;

    /**
     * 为会员列表计算并更新 RFM 评分。
     */
    public void scoreMembers(List<CampaignMemberDim> members) {
        if (members == null || members.isEmpty()) return;

        // 1. 按 Recency 排序（天数越少越好）
        List<CampaignMemberDim> byRecency = members.stream()
                .sorted((a, b) -> {
                    int ra = a.getRecencyDays() != null ? a.getRecencyDays() : Integer.MAX_VALUE;
                    int rb = b.getRecencyDays() != null ? b.getRecencyDays() : Integer.MAX_VALUE;
                    return Integer.compare(ra, rb);
                })
                .toList();

        // 2. 按 Frequency 排序（次数越多越好）
        List<CampaignMemberDim> byFrequency = members.stream()
                .sorted((a, b) -> {
                    int fa = a.getTotalOrderCount() != null ? a.getTotalOrderCount() : 0;
                    int fb = b.getTotalOrderCount() != null ? b.getTotalOrderCount() : 0;
                    return Integer.compare(fb, fa);
                })
                .toList();

        // 3. 按 Monetary 排序（金额越高越好）
        List<CampaignMemberDim> byMonetary = members.stream()
                .sorted((a, b) -> {
                    BigDecimal ma = a.getTotalOrderAmount() != null ? a.getTotalOrderAmount() : BigDecimal.ZERO;
                    BigDecimal mb = b.getTotalOrderAmount() != null ? b.getTotalOrderAmount() : BigDecimal.ZERO;
                    return mb.compareTo(ma);
                })
                .toList();

        int size = members.size();

        // 4. 分 5 等分赋分
        for (CampaignMemberDim member : members) {
            int recencyScore = getQuintileScore(byRecency, member, size);
            int frequencyScore = getQuintileScore(byFrequency, member, size);
            int monetaryScore = getQuintileScore(byMonetary, member, size);

            member.setRfmRecencyScore(recencyScore);
            member.setRfmFrequencyScore(frequencyScore);
            member.setRfmMonetaryScore(monetaryScore);

            // 加权总分
            double total = recencyScore * WEIGHT_RECENCY
                         + frequencyScore * WEIGHT_FREQUENCY
                         + monetaryScore * WEIGHT_MONETARY;
            member.setRfmTotalScore(BigDecimal.valueOf(total)
                    .setScale(2, RoundingMode.HALF_UP));
        }

        log.info("RFM scoring completed for {} members", size);
    }

    /**
     * 计算单个会员在某维度排序中的 5 分位得分。
     */
    private int getQuintileScore(List<CampaignMemberDim> sortedList,
                                  CampaignMemberDim target, int totalSize) {
        int index = -1;
        for (int i = 0; i < sortedList.size(); i++) {
            if (sortedList.get(i).getMemberId().equals(target.getMemberId())) {
                index = i;
                break;
            }
        }
        if (index < 0) return 3; // 默认中间分

        double percentile = (double) (index + 1) / totalSize;
        if (percentile <= 0.2) return 5;
        if (percentile <= 0.4) return 4;
        if (percentile <= 0.6) return 3;
        if (percentile <= 0.8) return 2;
        return 1;
    }
}
