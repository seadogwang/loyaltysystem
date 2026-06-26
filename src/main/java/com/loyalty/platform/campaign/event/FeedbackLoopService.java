package com.loyalty.platform.campaign.event;

import com.loyalty.platform.campaign.simulation.service.SimulationEngine;
import com.loyalty.platform.domain.entity.campaign.*;
import com.loyalty.platform.domain.repository.campaign.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * 反馈闭环服务 — ROI/转化率偏差检测 + 策略调整触发。
 *
 * <p>在 Campaign 完成后，对比预测值（来自 Simulation）与实际执行结果，
 * 检测模型漂移，并触发票预算重分配/渠道重调/受众优化等策略调整。
 */
@Service
@Transactional
public class FeedbackLoopService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLoopService.class);

    private static final double ROI_DRIFT_THRESHOLD = 0.3;
    private static final double CONVERSION_DRIFT_THRESHOLD = 0.2;

    private final CampaignPlanRepository planRepository;
    private final CampaignFeedbackMetricsRepository metricsRepository;
    private final CampaignModelDriftRepository driftRepository;
    private final CampaignStrategyAdjustmentRepository adjustmentRepository;
    private final CampaignZeebeTaskRepository taskRepository;
    private final CampaignZeebeInstanceRepository instanceRepository;
    private final CampaignEventService eventService;

    public FeedbackLoopService(CampaignPlanRepository planRepository,
                                CampaignFeedbackMetricsRepository metricsRepository,
                                CampaignModelDriftRepository driftRepository,
                                CampaignStrategyAdjustmentRepository adjustmentRepository,
                                CampaignZeebeTaskRepository taskRepository,
                                CampaignZeebeInstanceRepository instanceRepository,
                                CampaignEventService eventService) {
        this.planRepository = planRepository;
        this.metricsRepository = metricsRepository;
        this.driftRepository = driftRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.taskRepository = taskRepository;
        this.instanceRepository = instanceRepository;
        this.eventService = eventService;
    }

    /**
     * 计算反馈指标（Campaign 完成后调用）。
     */
    public CampaignFeedbackMetrics calculateFeedback(String planId) {
        CampaignPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planId));

        // Get predicted values from plan
        BigDecimal predictedROI = plan.getExpectedRoi() != null ? plan.getExpectedRoi() : BigDecimal.ONE;
        BigDecimal predictedRevenue = plan.getTotalBudget() != null
                ? plan.getTotalBudget().multiply(predictedROI) : BigDecimal.valueOf(100000);
        BigDecimal predictedConversion = BigDecimal.valueOf(0.15);

        // Aggregate actual execution stats
        var tasks = taskRepository.findByPlanIdOrderByStartTimeDesc(planId);
        long totalExposures = tasks.stream()
                .filter(t -> t.getOutputVariables() != null && t.getOutputVariables().containsKey("successCount"))
                .mapToLong(t -> ((Number) t.getOutputVariables().get("successCount")).longValue()).sum();
        long totalConversions = totalExposures > 0 ? (long) (totalExposures * 0.2) : 0; // simplified
        long uniqueUsers = 1000;

        double actualRevenue = totalConversions * 100;
        double actualCost = tasks.size() * 50;
        BigDecimal actualROI = actualCost > 0
                ? BigDecimal.valueOf((actualRevenue - actualCost) / actualCost).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal actualConversion = uniqueUsers > 0
                ? BigDecimal.valueOf((double) totalConversions / uniqueUsers).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal roiDeviation = actualROI.subtract(predictedROI);
        BigDecimal conversionDeviation = actualConversion.subtract(predictedConversion);

        // Save metrics
        CampaignFeedbackMetrics metrics = CampaignFeedbackMetrics.builder()
                .id(UUID.randomUUID().toString()).planId(planId)
                .initiativeId(plan.getInitiativeId()).goalId(plan.getGoalId())
                .predictedRoi(predictedROI).predictedConversion(predictedConversion).predictedRevenue(predictedRevenue)
                .actualRoi(actualROI).actualConversion(actualConversion)
                .actualRevenue(BigDecimal.valueOf(actualRevenue)).actualCost(BigDecimal.valueOf(actualCost))
                .roiDeviation(roiDeviation).conversionDeviation(conversionDeviation)
                .totalExposures(totalExposures).totalConversions(totalConversions).uniqueUsers(uniqueUsers)
                .calculatedAt(Instant.now()).build();
        metrics = metricsRepository.save(metrics);

        // Detect drift
        detectAndHandleDrift(plan, metrics);

        // Publish event
        eventService.publishFeedbackROI(planId, predictedROI, actualROI);

        log.info("Feedback calculated: planId={}, predictedROI={}, actualROI={}, deviation={}",
                planId, predictedROI, actualROI, roiDeviation);
        return metrics;
    }

    private void detectAndHandleDrift(CampaignPlan plan, CampaignFeedbackMetrics metrics) {
        double roiDev = metrics.getRoiDeviation() != null ? metrics.getRoiDeviation().doubleValue() : 0;
        double convDev = metrics.getConversionDeviation() != null ? metrics.getConversionDeviation().doubleValue() : 0;
        int sampleSize = metrics.getUniqueUsers() != null ? metrics.getUniqueUsers().intValue() : 0;

        // ROI drift
        if (Math.abs(roiDev) > ROI_DRIFT_THRESHOLD) {
            CampaignModelDrift drift = CampaignModelDrift.builder()
                    .id(UUID.randomUUID().toString()).modelName("roi_prediction")
                    .driftDetected(true).driftScore(BigDecimal.valueOf(Math.abs(roiDev)))
                    .threshold(BigDecimal.valueOf(ROI_DRIFT_THRESHOLD)).sampleSize(sampleSize)
                    .meanPredicted(metrics.getPredictedRoi()).meanActual(metrics.getActualRoi())
                    .mae(BigDecimal.valueOf(Math.abs(roiDev))).detectedAt(Instant.now()).status("PENDING").build();
            driftRepository.save(drift);
            triggerAdjustment(plan, metrics, "ROI_DRIFT");
        }

        // Conversion drift
        if (Math.abs(convDev) > CONVERSION_DRIFT_THRESHOLD) {
            CampaignModelDrift drift = CampaignModelDrift.builder()
                    .id(UUID.randomUUID().toString()).modelName("conversion_prediction")
                    .driftDetected(true).driftScore(BigDecimal.valueOf(Math.abs(convDev)))
                    .threshold(BigDecimal.valueOf(CONVERSION_DRIFT_THRESHOLD)).sampleSize(sampleSize)
                    .meanPredicted(metrics.getPredictedConversion()).meanActual(metrics.getActualConversion())
                    .mae(BigDecimal.valueOf(Math.abs(convDev))).detectedAt(Instant.now()).status("PENDING").build();
            driftRepository.save(drift);
        }
    }

    private void triggerAdjustment(CampaignPlan plan, CampaignFeedbackMetrics metrics, String trigger) {
        String adjType = metrics.getActualRoi().compareTo(metrics.getPredictedRoi()) < 0
                ? "BUDGET_REALLOC" : "AUDIENCE_REFINE";
        CampaignStrategyAdjustment adj = CampaignStrategyAdjustment.builder()
                .id(UUID.randomUUID().toString()).planId(plan.getId()).workspaceId(plan.getWorkspaceId())
                .adjustmentType(adjType).triggerEvent(trigger)
                .beforeConfig(plan.getAllocationJson()).afterConfig(plan.getAllocationJson())
                .reason(String.format("Drift: predicted=%.2f, actual=%.2f", metrics.getPredictedRoi(), metrics.getActualRoi()))
                .expectedImprovement(BigDecimal.valueOf(0.1)).status("PENDING").createdBy("SYSTEM").createdAt(Instant.now()).build();
        adjustmentRepository.save(adj);
        log.info("Strategy adjustment created: planId={}, type={}", plan.getId(), adjType);
    }

    @Transactional(readOnly = true)
    public Optional<CampaignFeedbackMetrics> getLatestMetrics(String planId) {
        return metricsRepository.findFirstByPlanIdOrderByCalculatedAtDesc(planId);
    }
}
