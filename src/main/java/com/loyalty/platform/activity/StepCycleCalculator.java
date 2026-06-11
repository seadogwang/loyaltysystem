package com.loyalty.platform.activity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Step/cycle calculator for promo reward computation.
 *
 * Supports: fixed-step, stepped, THRESHOLD_LOOP cycle mode,
 * excess control (STOP/RATIO/TRUNCATE_AND_DOWNGRADE).
 */
public class StepCycleCalculator {

    public record Step(BigDecimal lower, BigDecimal upper, BigDecimal multiplier, boolean isCycleThreshold,
                       boolean lowerInclusive, boolean upperInclusive) {}

    public record RewardSegment(BigDecimal amount, BigDecimal multiplier, BigDecimal points) {}

    public record RewardResult(List<RewardSegment> segments, BigDecimal totalPoints, BigDecimal truncatedAmount) {}

    /**
     * Calculate theoretical segments (before cap).
     */
    public static List<RewardSegment> calculateTheoreticalSegments(
            BigDecimal amount, List<Step> steps, String cycleMode, List<BigDecimal> cycleThresholds) {

        List<RewardSegment> segments = new ArrayList<>();
        BigDecimal remaining = amount;

        if ("THRESHOLD_LOOP".equals(cycleMode) && cycleThresholds != null && !cycleThresholds.isEmpty()) {
            BigDecimal highest = cycleThresholds.get(0);
            while (remaining.compareTo(highest) >= 0) {
                BigDecimal multiplier = getMultiplierForAmount(highest, steps);
                segments.add(new RewardSegment(highest, multiplier, highest.multiply(multiplier).setScale(4, RoundingMode.HALF_UP)));
                remaining = remaining.subtract(highest);
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal multiplier = getMultiplierForAmount(remaining, steps);
            segments.add(new RewardSegment(remaining, multiplier, remaining.multiply(multiplier).setScale(4, RoundingMode.HALF_UP)));
        }

        return segments;
    }

    private static BigDecimal getMultiplierForAmount(BigDecimal amount, List<Step> steps) {
        for (Step step : steps) {
            boolean lowerMatch = step.lowerInclusive()
                    ? amount.compareTo(step.lower()) >= 0
                    : amount.compareTo(step.lower()) > 0;
            if (step.upper() == null) {
                if (lowerMatch) return step.multiplier();
            } else {
                boolean upperMatch = step.upperInclusive()
                        ? amount.compareTo(step.upper()) <= 0
                        : amount.compareTo(step.upper()) < 0;
                if (lowerMatch && upperMatch) return step.multiplier();
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Apply excess control based on remaining capacity.
     */
    public static RewardResult applyExcessControl(
            List<RewardSegment> theoreticalSegments,
            BigDecimal remainingCapacity,
            BigDecimal downgradeMultiplier,
            boolean continueCycle,
            String excessStrategy) {

        BigDecimal total = BigDecimal.ZERO;
        List<RewardSegment> finalSegments = new ArrayList<>();
        BigDecimal truncated = BigDecimal.ZERO;

        for (RewardSegment seg : theoreticalSegments) {
            total = total.add(seg.points());
            finalSegments.add(seg);
        }

        if (remainingCapacity == null || total.compareTo(remainingCapacity) <= 0) {
            return new RewardResult(finalSegments, total, BigDecimal.ZERO);
        }

        switch (excessStrategy != null ? excessStrategy : "STOP") {
            case "STOP" -> {
                // Cap total at remainingCapacity
                BigDecimal capped = BigDecimal.ZERO;
                List<RewardSegment> cappedSegments = new ArrayList<>();
                for (RewardSegment seg : theoreticalSegments) {
                    if (capped.add(seg.points()).compareTo(remainingCapacity) > 0) {
                        BigDecimal remaining = remainingCapacity.subtract(capped.subtract(seg.points()));
                        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal adjAmount = remaining.divide(seg.multiplier(), 4, RoundingMode.HALF_UP);
                            cappedSegments.add(new RewardSegment(adjAmount, seg.multiplier(), remaining));
                        }
                        truncated = seg.amount().subtract(remaining.divide(seg.multiplier(), 4, RoundingMode.HALF_UP));
                        break;
                    }
                    cappedSegments.add(seg);
                    capped = capped.add(seg.points());
                }
                return new RewardResult(cappedSegments, capped, truncated);
            }
            case "RATIO" -> {
                BigDecimal ratio = remainingCapacity.divide(total, 4, RoundingMode.HALF_UP);
                List<RewardSegment> scaled = theoreticalSegments.stream()
                        .map(s -> new RewardSegment(s.amount(), s.multiplier(), s.points().multiply(ratio).setScale(4, RoundingMode.HALF_UP)))
                        .toList();
                BigDecimal scaledTotal = scaled.stream().map(RewardSegment::points).reduce(BigDecimal.ZERO, BigDecimal::add);
                return new RewardResult(scaled, scaledTotal, BigDecimal.ZERO);
            }
            case "TRUNCATE_AND_DOWNGRADE" -> {
                // Apply cap, downgrade remaining
                List<RewardSegment> downgraded = new ArrayList<>();
                BigDecimal acc = BigDecimal.ZERO;
                BigDecimal downgradeMul = downgradeMultiplier != null ? downgradeMultiplier : BigDecimal.ONE;

                for (RewardSegment seg : theoreticalSegments) {
                    if (acc.add(seg.points()).compareTo(remainingCapacity) > 0) {
                        BigDecimal capRemaining = remainingCapacity.subtract(acc);
                        if (capRemaining.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal adjAmount = capRemaining.divide(seg.multiplier(), 4, RoundingMode.HALF_UP);
                            downgraded.add(new RewardSegment(adjAmount, seg.multiplier(), capRemaining));
                            acc = acc.add(capRemaining);
                        }
                        truncated = seg.amount();
                        break;
                    }
                    downgraded.add(seg);
                    acc = acc.add(seg.points());
                }
                return new RewardResult(downgraded, acc, truncated);
            }
            default -> {
                return new RewardResult(finalSegments, total, BigDecimal.ZERO);
            }
        }
    }

    /**
     * Preview: calculate total points for a given amount.
     */
    public static PreviewResult preview(BigDecimal orderAmount, BigDecimal alreadyRewarded,
                                         List<Step> steps, String cycleMode, List<BigDecimal> cycleThresholds,
                                         BigDecimal perOrderLimit, BigDecimal accumulativeLimit,
                                         String excessStrategy, BigDecimal downgradeMultiplier) {
        List<RewardSegment> theoretical = calculateTheoreticalSegments(orderAmount, steps, cycleMode, cycleThresholds);
        BigDecimal theoreticalTotal = theoretical.stream().map(RewardSegment::points).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Per-order limit
        BigDecimal capped = theoreticalTotal;
        if (perOrderLimit != null && capped.compareTo(perOrderLimit) > 0) {
            capped = perOrderLimit;
        }

        // Accumulative limit
        BigDecimal remainingCap = null;
        if (accumulativeLimit != null) {
            remainingCap = accumulativeLimit.subtract(alreadyRewarded);
            if (remainingCap.compareTo(BigDecimal.ZERO) <= 0) remainingCap = BigDecimal.ZERO;
        }

        RewardResult result = applyExcessControl(theoretical, remainingCap, downgradeMultiplier, false, excessStrategy);

        return new PreviewResult(theoretical, result.totalPoints(), capped, alreadyRewarded.add(result.totalPoints()),
                remainingCap != null ? remainingCap : accumulativeLimit, theoreticalTotal);
    }

    public record PreviewResult(
            List<RewardSegment> theoreticalSegments,
            BigDecimal finalPoints,
            BigDecimal afterPerOrderCap,
            BigDecimal newTotal,
            BigDecimal remainingCap,
            BigDecimal theoreticalTotal
    ) {}
}