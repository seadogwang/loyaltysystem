package com.loyalty.platform.campaign.simulation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.campaign.decision.dto.CampaignCandidate;
import com.loyalty.platform.campaign.decision.service.AttentionBudgetService;
import com.loyalty.platform.campaign.opportunity.dto.MLScoreResult;
import com.loyalty.platform.campaign.opportunity.dto.MemberFeature;
import com.loyalty.platform.campaign.opportunity.entity.CampaignMemberDim;
import com.loyalty.platform.campaign.opportunity.entity.CampaignOrderFact;
import com.loyalty.platform.campaign.opportunity.repository.CampaignMemberDimRepository;
import com.loyalty.platform.campaign.opportunity.repository.CampaignOrderFactRepository;
import com.loyalty.platform.campaign.opportunity.service.MLScoringClient;
import com.loyalty.platform.campaign.simulation.SimulationErrorCode;
import com.loyalty.platform.campaign.simulation.dto.BaselineResult;
import com.loyalty.platform.campaign.simulation.dto.SegmentSimulation;
import com.loyalty.platform.campaign.simulation.dto.SimulationRequest;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.domain.entity.campaign.CampaignSimulationResult;
import com.loyalty.platform.domain.repository.campaign.CampaignSimulationResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模拟引擎 — 三层预测模型（Exposure → Behavior → Conversion）。
 *
 * <p>核心流程：
 * <ol>
 *   <li>计算基线转化率（基于历史订单数据）</li>
 *   <li>构建用户特征并调用 ML 预测</li>
 *   <li>三层模型逐层计算概率</li>
 *   <li>聚合结果并持久化</li>
 * </ol>
 */
@Service
@Transactional
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private static final int BASELINE_DAYS = 30;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final CampaignMemberDimRepository memberDimRepository;
    private final CampaignOrderFactRepository orderFactRepository;
    private final CampaignSimulationResultRepository simulationResultRepository;
    private final MLScoringClient mlScoringClient;
    private final AttentionBudgetService attentionBudgetService;
    private final ConfidenceCalculator confidenceCalculator;

    public SimulationEngine(CampaignMemberDimRepository memberDimRepository,
                             CampaignOrderFactRepository orderFactRepository,
                             CampaignSimulationResultRepository simulationResultRepository,
                             MLScoringClient mlScoringClient,
                             AttentionBudgetService attentionBudgetService,
                             ConfidenceCalculator confidenceCalculator) {
        this.memberDimRepository = memberDimRepository;
        this.orderFactRepository = orderFactRepository;
        this.simulationResultRepository = simulationResultRepository;
        this.mlScoringClient = mlScoringClient;
        this.attentionBudgetService = attentionBudgetService;
        this.confidenceCalculator = confidenceCalculator;
    }

    // ========================================================================
    // 基线计算
    // ========================================================================

    /**
     * 计算基线转化率 — 基于 Loyalty 历史订单数据。
     *
     * <p>查询过去 N 天的订单，计算自然转化率（未受 Campaign 影响的用户）。
     */
    public BaselineResult calculateBaseline(String goalId, String segmentCode) {
        log.info("Calculating baseline for goal={}, segment={}", goalId, segmentCode);

        List<CampaignMemberDim> members = memberDimRepository.findBySegmentCode(segmentCode);
        if (members.isEmpty()) {
            log.warn("No members found for segment: {}", segmentCode);
            throw new BusinessException(SimulationErrorCode.NO_MEMBERS_FOUND.getMessage());
        }

        Set<String> memberIds = members.stream()
                .map(CampaignMemberDim::getMemberId)
                .collect(Collectors.toSet());

        LocalDateTime startDate = LocalDateTime.now().minusDays(BASELINE_DAYS);
        List<CampaignOrderFact> orders = orderFactRepository.findByMemberIdsAndDateAfter(memberIds, startDate);

        Set<String> convertedMemberIds = orders.stream()
                .map(CampaignOrderFact::getMemberId)
                .collect(Collectors.toSet());

        double conversionRate = memberIds.isEmpty() ? 0 :
                (double) convertedMemberIds.size() / memberIds.size();

        double avgOrderValue = orders.stream()
                .mapToDouble(o -> o.getOrderAmount() != null ? o.getOrderAmount().doubleValue() : 0)
                .average()
                .orElse(0);

        Map<String, Double> segmentBreakdown = calculateSegmentBreakdown(members, orders);

        return BaselineResult.builder()
                .segmentCode(segmentCode)
                .totalMembers(memberIds.size())
                .convertedMembers(convertedMemberIds.size())
                .conversionRate(conversionRate)
                .avgOrderValue(avgOrderValue)
                .estimatedRevenue(conversionRate * memberIds.size() * avgOrderValue)
                .segmentBreakdown(segmentBreakdown)
                .periodDays(BASELINE_DAYS)
                .calculatedAt(Instant.now())
                .build();
    }

    // ========================================================================
    // 完整模拟
    // ========================================================================

    /**
     * 执行完整模拟 — 三层模型预测。
     */
    public CampaignSimulationResult simulate(SimulationRequest request) {
        log.info("Starting simulation: name={}, segment={}, channel={}",
                request.getName(), request.getSegmentCode(), request.getChannel());

        // 1. 计算基线
        BaselineResult baseline = calculateBaseline(request.getGoalId(), request.getSegmentCode());

        // 2. 获取目标用户
        List<CampaignMemberDim> members = memberDimRepository.findBySegmentCode(request.getSegmentCode());
        if (members.isEmpty()) {
            throw new BusinessException(SimulationErrorCode.NO_MEMBERS_FOUND.getMessage());
        }

        // 3. 构建用户特征并调用 ML 预测
        List<MemberFeature> features = members.stream()
                .map(this::buildMemberFeature)
                .collect(Collectors.toList());
        List<MLScoreResult> mlResults = mlScoringClient.predictBatch(features);

        // 4. 三层模拟
        long totalExposure = 0, totalBehavior = 0, totalConversion = 0;
        double totalRevenue = 0;
        Map<String, SegmentSimulation> segmentResults = new HashMap<>();

        for (int i = 0; i < members.size(); i++) {
            CampaignMemberDim member = members.get(i);
            MLScoreResult mlResult = mlResults.get(i);

            double exposureProb = calculateExposureProbability(member, request.getChannel());
            double behaviorProb = calculateBehaviorProbability(member, mlResult, request.getOfferStrength());
            double conversionProb = calculateConversionProbability(member, mlResult, request.getOfferMatch());

            double totalProb = exposureProb * behaviorProb * conversionProb;

            if (Math.random() < totalProb) {
                totalConversion++;
                totalRevenue += member.getAvgOrderValue() != null
                        ? member.getAvgOrderValue().doubleValue() : 100;
            }
            if (Math.random() < exposureProb) totalExposure++;
            if (Math.random() < behaviorProb) totalBehavior++;

            String segKey = member.getSegmentCode() != null ? member.getSegmentCode() : "UNKNOWN";
            segmentResults.computeIfAbsent(segKey, k -> new SegmentSimulation()).addMember(totalProb);
        }

        // 5. 计算预测结果
        double predictedRevenue = totalRevenue;
        double campaignCost = calculateCampaignCost(members.size(), request);
        double predictedROI = campaignCost > 0 ? (predictedRevenue - campaignCost) / campaignCost : 0;
        double predictedConv = members.isEmpty() ? 0 : (double) totalConversion / members.size();
        double uplift = baseline.getConversionRate() > 0
                ? (predictedConv - baseline.getConversionRate()) / baseline.getConversionRate() : 0;

        // 6. 构建并保存结果
        String id = UUID.randomUUID().toString();
        CampaignSimulationResult result = CampaignSimulationResult.builder()
                .id(id)
                .workspaceId(request.getWorkspaceId())
                .goalId(request.getGoalId())
                .simulationType("SCENARIO")
                .name(request.getName())
                .inputSnapshot(toJson(request))
                .baselineConversion(BigDecimal.valueOf(baseline.getConversionRate()))
                .predictedConversion(BigDecimal.valueOf(predictedConv))
                .predictedRevenue(BigDecimal.valueOf(predictedRevenue))
                .predictedRoi(BigDecimal.valueOf(predictedROI))
                .upliftPct(BigDecimal.valueOf(uplift))
                .confidence(BigDecimal.valueOf(confidenceCalculator.calculateConfidence(members.size(), 0.85)))
                .exposureCount(totalExposure)
                .behaviorCount(totalBehavior)
                .conversionCount(totalConversion)
                .segmentBreakdown(toJson(segmentResults))
                .channelBreakdown(toJson(buildChannelBreakdown(request)))
                .status("COMPLETED")
                .executedBy("system")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        result = simulationResultRepository.save(result);
        log.info("Simulation completed: id={}, roi={}, conversion={}", id, predictedROI, predictedConv);
        return result;
    }

    // ========================================================================
    // 三层模型
    // ========================================================================

    private double calculateExposureProbability(CampaignMemberDim member, String channel) {
        int remaining = attentionBudgetService.getRemainingQuota(member.getMemberId(), channel);
        if (remaining <= 0) return 0;

        double historicalEngagement = 0.4 + Math.random() * 0.3; // 简化：随机 0.4-0.7
        double baseProb = 0.7;
        double factor = 0.3 * 0.8 + 0.3 * Math.min(remaining / 3.0, 1.0) + 0.4 * historicalEngagement; // channelAvailability ≈ 0.8
        return Math.min(baseProb * factor, 1.0);
    }

    private double calculateBehaviorProbability(CampaignMemberDim member, MLScoreResult mlResult, Double offerStrength) {
        double offerFactor = offerStrength != null ? offerStrength : 0.5;
        double interestScore = mlResult.getUpliftScore() != null ? mlResult.getUpliftScore() : 0.5;
        double fatigueScore = calculateFatigueScore(member.getMemberId());
        return sigmoid(0.4 * offerFactor + 0.4 * interestScore - 0.2 * fatigueScore);
    }

    private double calculateConversionProbability(CampaignMemberDim member, MLScoreResult mlResult, Double offerMatch) {
        double mlConversion = mlResult.getConversionProbability() != null ? mlResult.getConversionProbability() : 0.3;
        double matchFactor = offerMatch != null ? offerMatch : 0.5;
        double valueFactor = Math.min(
                (member.getTotalOrderAmount() != null ? member.getTotalOrderAmount().doubleValue() : 0) / 10000, 1.0);
        return 0.5 * mlConversion + 0.3 * matchFactor + 0.2 * valueFactor;
    }

    private double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private double calculateFatigueScore(String userId) {
        // 简化：基于注意力预算使用情况估算疲劳度
        int remaining = attentionBudgetService.getRemainingQuota(userId, "EMAIL");
        return Math.min((double) Math.max(0, 10 - remaining - 3) / 10, 1.0);
    }

    private double calculateCampaignCost(int memberCount, SimulationRequest request) {
        double costPerUser = 0.5;
        if ("SMS".equals(request.getChannel())) costPerUser = 0.8;
        else if ("PUSH".equals(request.getChannel())) costPerUser = 0.3;
        return memberCount * costPerUser;
    }

    private Map<String, Double> calculateSegmentBreakdown(List<CampaignMemberDim> members, List<CampaignOrderFact> orders) {
        Map<String, Set<String>> segmentMembers = new HashMap<>();
        for (CampaignMemberDim m : members) {
            segmentMembers.computeIfAbsent(
                    m.getSegmentCode() != null ? m.getSegmentCode() : "UNKNOWN", k -> new HashSet<>()
            ).add(m.getMemberId());
        }
        Set<String> convertedIds = orders.stream().map(CampaignOrderFact::getMemberId).collect(Collectors.toSet());

        Map<String, Double> breakdown = new HashMap<>();
        for (var entry : segmentMembers.entrySet()) {
            long convCount = entry.getValue().stream().filter(convertedIds::contains).count();
            breakdown.put(entry.getKey(), entry.getValue().isEmpty() ? 0 : (double) convCount / entry.getValue().size());
        }
        return breakdown;
    }

    private Map<String, Object> buildChannelBreakdown(SimulationRequest request) {
        return Map.of("channel", request.getChannel() != null ? request.getChannel() : "EMAIL",
                "estimatedCapacity", 50000, "costPerUser", request.getChannel() != null && "SMS".equals(request.getChannel()) ? 0.8 : 0.5);
    }

    private MemberFeature buildMemberFeature(CampaignMemberDim member) {
        int tierLevel = 0;
        if (member.getTierCode() != null) {
            try {
                tierLevel = Integer.parseInt(member.getTierCode().split("_")[member.getTierCode().split("_").length - 1]);
            } catch (NumberFormatException ignored) {}
        }
        int daysSinceRegister = 0;
        if (member.getRegisteredAt() != null) {
            daysSinceRegister = (int) ChronoUnit.DAYS.between(member.getRegisteredAt().toLocalDate(), LocalDate.now());
        }
        return MemberFeature.builder()
                .memberId(member.getMemberId())
                .recency(member.getRecencyDays() != null ? member.getRecencyDays() : 90)
                .frequency(member.getTotalOrderCount() != null ? member.getTotalOrderCount() : 0)
                .monetary(member.getTotalOrderAmount() != null ? member.getTotalOrderAmount().doubleValue() : 0)
                .avgOrderValue(member.getAvgOrderValue() != null ? member.getAvgOrderValue().doubleValue() : 0)
                .tierLevel(tierLevel).totalLoginDays(0).continuousLoginDays(0).daysSinceRegister(daysSinceRegister)
                .build();
    }

    // ========================================================================
    // 查询
    // ========================================================================

    @Transactional(readOnly = true)
    public Optional<CampaignSimulationResult> getResult(String id) {
        return simulationResultRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CampaignSimulationResult> getHistory(String workspaceId, int page, int size) {
        return simulationResultRepository.findByWorkspaceIdOrderByCreatedAtDesc(
                workspaceId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    // ========================================================================
    // v1 兼容 API（保持原有 DecisionEngine 调用不中断）
    // ========================================================================

    /** 兼容 v1 的单候选模拟 */
    public com.loyalty.platform.campaign.decision.dto.SimulationResult simulate(
            CampaignCandidate candidate, long audienceSize) {
        double exposureRate = 0.7 * Math.min(audienceSize / 100000.0, 1.0);
        double behaviorRate = 0.5 * (candidate.getOpportunityScore() + candidate.getRecencyBoost()) / 2;
        double conversionRate = 0.3 * (candidate.getExpectedROI().doubleValue() / 5.0);
        double roi = exposureRate * behaviorRate * conversionRate * candidate.getExpectedROI().doubleValue();

        return com.loyalty.platform.campaign.decision.dto.SimulationResult.builder()
                .candidateId(candidate.getId())
                .exposureRate(Math.min(exposureRate * 100, 100))
                .behaviorRate(Math.min(behaviorRate * 100, 100))
                .conversionRate(Math.min(conversionRate * 100, 100))
                .expectedRevenue(BigDecimal.valueOf(audienceSize * conversionRate * 100))
                .expectedROI(BigDecimal.valueOf(Math.max(roi, 0.1)))
                .estimatedReach((long) (audienceSize * exposureRate))
                .estimatedConversions((long) (audienceSize * conversionRate))
                .modelDetails(Map.of("exposureModel", "v1", "behaviorModel", "v1", "conversionModel", "v1"))
                .build();
    }

    public List<com.loyalty.platform.campaign.decision.dto.SimulationResult> simulateBatch(
            List<CampaignCandidate> candidates, long audienceSize) {
        return candidates.stream().map(c -> simulate(c, audienceSize)).collect(Collectors.toList());
    }

    public com.loyalty.platform.campaign.decision.dto.SimulationCompareResult comparePlans(
            String planAId, String planAName, List<CampaignCandidate> candidatesA,
            String planBId, String planBName, List<CampaignCandidate> candidatesB, long audienceSize) {
        var resultsA = simulateBatch(candidatesA, audienceSize);
        var resultsB = simulateBatch(candidatesB, audienceSize);
        double roiA = resultsA.stream().mapToDouble(r -> r.getExpectedROI().doubleValue()).average().orElse(0);
        double roiB = resultsB.stream().mapToDouble(r -> r.getExpectedROI().doubleValue()).average().orElse(0);
        return com.loyalty.platform.campaign.decision.dto.SimulationCompareResult.builder()
                .planAId(planAId).planBId(planBId).planAName(planAName).planBName(planBName)
                .planAResults(resultsA).planBResults(resultsB)
                .comparison(Map.of("roiDiff", roiB - roiA, "winner", roiB > roiA ? "B" : "A"))
                .build();
    }

    /** 兼容 PortfolioService 的 ROI 预测 */
    public com.loyalty.platform.campaign.decision.dto.SimulationResult predictInitiativeROI(
            String initiativeId, BigDecimal budget) {
        var candidate = CampaignCandidate.builder()
                .id(initiativeId).initiativeId(initiativeId).name("initiative")
                .expectedROI(BigDecimal.valueOf(1.5)).opportunityScore(0.6).recencyBoost(0.5).channel("EMAIL")
                .recommendedBudget(budget).minBudget(BigDecimal.ZERO).maxBudget(budget).strategicWeight(0.5).segment("all")
                .build();
        return simulate(candidate, 50000);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
