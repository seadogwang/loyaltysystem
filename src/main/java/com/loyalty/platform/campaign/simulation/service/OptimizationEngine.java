package com.loyalty.platform.campaign.simulation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.campaign.planning.service.InitiativeService;
import com.loyalty.platform.campaign.planning.service.PortfolioService;
import com.loyalty.platform.campaign.simulation.SimulationErrorCode;
import com.loyalty.platform.campaign.simulation.dto.OptimizationRequest;
import com.loyalty.platform.campaign.simulation.dto.OptimizationResultResponse;
import com.loyalty.platform.campaign.simulation.dto.SimulationRequest;
import com.loyalty.platform.common.exception.BusinessException;
import com.loyalty.platform.domain.entity.campaign.*;
import com.loyalty.platform.domain.repository.campaign.CampaignOptimizationResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 优化引擎 — 贪心算法 + 遗传算法。
 *
 * <p>Phase 1: 贪心优化（ROI 降序分配）
 * <p>Phase 2: 遗传算法（种群进化搜索最优解）
 */
@Service
@Transactional
public class OptimizationEngine {

    private static final Logger log = LoggerFactory.getLogger(OptimizationEngine.class);
    private static final int DEFAULT_GENERATIONS = 50;
    private static final int DEFAULT_POPULATION_SIZE = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulationEngine simulationEngine;
    private final CampaignOptimizationResultRepository optimizationResultRepository;
    private final PortfolioService portfolioService;
    private final InitiativeService initiativeService;

    public OptimizationEngine(SimulationEngine simulationEngine,
                               CampaignOptimizationResultRepository optimizationResultRepository,
                               PortfolioService portfolioService,
                               InitiativeService initiativeService) {
        this.simulationEngine = simulationEngine;
        this.optimizationResultRepository = optimizationResultRepository;
        this.portfolioService = portfolioService;
        this.initiativeService = initiativeService;
    }

    // ========================================================================
    // 贪心优化
    // ========================================================================

    public OptimizationResultResponse optimizeGreedy(OptimizationRequest request) {
        log.info("Starting greedy optimization: portfolio={}", request.getPortfolioId());

        CampaignPortfolio portfolio = portfolioService.getPortfolio(request.getPortfolioId());
        List<CampaignInitiative> initiatives = initiativeService.getActiveInitiatives(portfolio.getWorkspaceId());

        if (initiatives.isEmpty()) {
            throw new BusinessException(SimulationErrorCode.OPTIMIZATION_FAILED.getMessage());
        }

        // 每个 Initiative 模拟一次获取 ROI
        List<OptCandidate> candidates = new ArrayList<>();
        for (CampaignInitiative init : initiatives) {
            SimulationRequest simReq = SimulationRequest.builder()
                    .workspaceId(portfolio.getWorkspaceId()).goalId(init.getGoalId())
                    .segmentCode(getSegment(init)).channel("EMAIL").offerStrength(0.6).build();

            CampaignSimulationResult simResult = simulationEngine.simulate(simReq);
            double roi = simResult.getPredictedRoi() != null ? simResult.getPredictedRoi().doubleValue() : 1.0;

            candidates.add(new OptCandidate(init.getId(), init.getName(), roi, init.getPriority() != null ? init.getPriority() : 100));
        }

        // 按 ROI 降序
        candidates.sort((a, b) -> Double.compare(b.roi, a.roi));

        BigDecimal remaining = portfolio.getTotalBudget();
        Map<String, BigDecimal> allocations = new LinkedHashMap<>();

        for (OptCandidate c : candidates) {
            BigDecimal budget = BigDecimal.valueOf(c.roi * 50000).min(remaining).max(BigDecimal.valueOf(50000));
            if (budget.compareTo(remaining) <= 0 && budget.compareTo(BigDecimal.ZERO) > 0) {
                allocations.put(c.initiativeId, budget);
                remaining = remaining.subtract(budget);
            }
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
        }

        double avgROI = calculateAvgROI(allocations, candidates);

        // 构建响应
        List<OptimizationResultResponse.AllocationDetail> details = new ArrayList<>();
        BigDecimal total = allocations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (var entry : allocations.entrySet()) {
            OptCandidate c = candidates.stream().filter(x -> x.initiativeId.equals(entry.getKey())).findFirst().orElse(null);
            details.add(OptimizationResultResponse.AllocationDetail.builder()
                    .initiativeId(entry.getKey()).initiativeName(c != null ? c.initiativeName : entry.getKey())
                    .allocatedBudget(entry.getValue()).expectedRoi(BigDecimal.valueOf(c != null ? c.roi : 1.0))
                    .percentage(total.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0)
                    .build());
        }

        // 持久化
        String id = UUID.randomUUID().toString();
        CampaignOptimizationResult entity = CampaignOptimizationResult.builder()
                .id(id).workspaceId(portfolio.getWorkspaceId()).portfolioId(request.getPortfolioId())
                .optimizationType("GREEDY").constraints(toJson(request.getConstraints()))
                .optimizedAllocations(toJson(allocations)).expectedRoi(BigDecimal.valueOf(avgROI))
                .expectedRevenue(BigDecimal.valueOf(avgROI * total.doubleValue()))
                .iterationCount(candidates.size()).convergenceTimeMs(0L)
                .baselineRoi(BigDecimal.valueOf(1.0)).improvementPct(BigDecimal.valueOf(avgROI - 1.0))
                .status("DRAFT").createdBy("system").createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        optimizationResultRepository.save(entity);

        log.info("Greedy optimization completed: id={}, avgROI={}", id, avgROI);

        return OptimizationResultResponse.builder()
                .optimizationId(id).optimizationType("GREEDY").status("DRAFT")
                .expectedRoi(BigDecimal.valueOf(avgROI)).iterationCount(candidates.size()).convergenceTimeMs(0L)
                .baselineRoi(BigDecimal.valueOf(1.0)).improvementPct(BigDecimal.valueOf(Math.max(avgROI - 1.0, 0)))
                .allocationDetails(details).createdAt(Instant.now())
                .build();
    }

    // ========================================================================
    // 遗传算法优化
    // ========================================================================

    public OptimizationResultResponse optimizeGenetic(OptimizationRequest request) {
        log.info("Starting genetic optimization: portfolio={}", request.getPortfolioId());
        long startTime = System.currentTimeMillis();

        CampaignPortfolio portfolio = portfolioService.getPortfolio(request.getPortfolioId());
        List<CampaignInitiative> initiatives = initiativeService.getActiveInitiatives(portfolio.getWorkspaceId());

        if (initiatives.isEmpty()) {
            throw new BusinessException(SimulationErrorCode.OPTIMIZATION_FAILED.getMessage());
        }

        int maxGen = DEFAULT_GENERATIONS, popSize = DEFAULT_POPULATION_SIZE;
        if (request.getConstraints() != null) {
            if (request.getConstraints().getMaxGenerations() > 0) maxGen = request.getConstraints().getMaxGenerations();
            if (request.getConstraints().getPopulationSize() > 0) popSize = request.getConstraints().getPopulationSize();
        }

        Random rng = new Random();
        BigDecimal totalBudget = portfolio.getTotalBudget();
        List<Map<String, BigDecimal>> population = initPopulation(initiatives, totalBudget, popSize, rng);

        Map<String, BigDecimal> bestAllocation = null;
        double bestFitness = Double.NEGATIVE_INFINITY;

        for (int gen = 0; gen < maxGen; gen++) {
            // 评估适应度
            List<Double> fitnesses = new ArrayList<>();
            for (Map<String, BigDecimal> ind : population) {
                double fit = evaluateFitness(ind, initiatives, portfolio, totalBudget);
                fitnesses.add(fit);
                if (fit > bestFitness) { bestFitness = fit; bestAllocation = new HashMap<>(ind); }
            }

            // 锦标赛选择
            List<Map<String, BigDecimal>> selected = new ArrayList<>();
            for (int i = 0; i < popSize / 2; i++) {
                int t1 = rng.nextInt(popSize), t2 = rng.nextInt(popSize), t3 = rng.nextInt(popSize);
                int winner = t1;
                if (fitnesses.get(t2) > fitnesses.get(winner)) winner = t2;
                if (fitnesses.get(t3) > fitnesses.get(winner)) winner = t3;
                selected.add(new HashMap<>(population.get(winner)));
            }

            // 交叉
            List<Map<String, BigDecimal>> offspring = new ArrayList<>();
            for (int i = 0; i < selected.size() - 1; i += 2) {
                Map<String, BigDecimal> child = new HashMap<>();
                int crossPoint = rng.nextInt(initiatives.size());
                for (int j = 0; j < initiatives.size(); j++) {
                    String id = initiatives.get(j).getId();
                    child.put(id, j < crossPoint
                            ? selected.get(i).getOrDefault(id, BigDecimal.ZERO)
                            : selected.get(i + 1).getOrDefault(id, BigDecimal.ZERO));
                }
                normalize(child, totalBudget);
                offspring.add(child);
            }

            // 变异
            for (Map<String, BigDecimal> ind : offspring) {
                if (rng.nextDouble() < 0.1) {
                    int idx = rng.nextInt(initiatives.size());
                    String id = initiatives.get(idx).getId();
                    BigDecimal cur = ind.getOrDefault(id, BigDecimal.ZERO);
                    ind.put(id, cur.multiply(BigDecimal.valueOf(0.8 + rng.nextDouble() * 0.4)));
                }
            }

            population = new ArrayList<>(selected);
            population.addAll(offspring);
            if (bestAllocation != null) population.set(0, new HashMap<>(bestAllocation));

            if (gen % 10 == 0) log.info("Generation {}: best fitness = {}", gen, bestFitness);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double avgROI = calculateAvgROI(bestAllocation != null ? bestAllocation : Map.of(), initiatives, totalBudget);

        // Persist
        String id = UUID.randomUUID().toString();
        CampaignOptimizationResult entity = CampaignOptimizationResult.builder()
                .id(id).workspaceId(portfolio.getWorkspaceId()).portfolioId(request.getPortfolioId())
                .optimizationType("GENETIC").constraints(toJson(request.getConstraints()))
                .optimizedAllocations(toJson(bestAllocation)).expectedRoi(BigDecimal.valueOf(avgROI))
                .expectedRevenue(BigDecimal.valueOf(avgROI * totalBudget.doubleValue()))
                .iterationCount(maxGen).convergenceTimeMs(elapsed)
                .baselineRoi(BigDecimal.valueOf(1.0)).improvementPct(BigDecimal.valueOf(Math.max(avgROI - 1.0, 0)))
                .status("DRAFT").createdBy("system").createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        optimizationResultRepository.save(entity);

        // Response
        List<OptimizationResultResponse.AllocationDetail> details = new ArrayList<>();
        if (bestAllocation != null) {
            for (var entry : bestAllocation.entrySet()) {
                CampaignInitiative init = initiatives.stream().filter(x -> x.getId().equals(entry.getKey())).findFirst().orElse(null);
                details.add(OptimizationResultResponse.AllocationDetail.builder()
                        .initiativeId(entry.getKey()).initiativeName(init != null ? init.getName() : entry.getKey())
                        .allocatedBudget(entry.getValue()).expectedRoi(BigDecimal.valueOf(avgROI))
                        .percentage(totalBudget.compareTo(BigDecimal.ZERO) > 0
                                ? entry.getValue().divide(totalBudget, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0)
                        .build());
            }
        }

        log.info("Genetic optimization completed: id={}, avgROI={}, elapsed={}ms", id, avgROI, elapsed);

        return OptimizationResultResponse.builder()
                .optimizationId(id).optimizationType("GENETIC").status("DRAFT")
                .expectedRoi(BigDecimal.valueOf(avgROI)).iterationCount(maxGen).convergenceTimeMs(elapsed)
                .baselineRoi(BigDecimal.valueOf(1.0)).improvementPct(BigDecimal.valueOf(Math.max(avgROI - 1.0, 0)))
                .allocationDetails(details).createdAt(Instant.now())
                .build();
    }

    // ========================================================================
    // 遗传算法辅助
    // ========================================================================

    private List<Map<String, BigDecimal>> initPopulation(List<CampaignInitiative> initiatives, BigDecimal total, int size, Random rng) {
        List<Map<String, BigDecimal>> pop = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Map<String, BigDecimal> ind = new HashMap<>();
            BigDecimal rem = total;
            for (CampaignInitiative init : initiatives) {
                BigDecimal budget = rem.multiply(BigDecimal.valueOf(0.2 + rng.nextDouble() * 0.6)).setScale(2, RoundingMode.HALF_UP);
                if (budget.compareTo(rem) > 0) budget = rem;
                ind.put(init.getId(), budget);
                rem = rem.subtract(budget);
            }
            if (rem.compareTo(BigDecimal.ZERO) > 0) {
                String lastId = initiatives.get(initiatives.size() - 1).getId();
                ind.put(lastId, ind.get(lastId).add(rem));
            }
            pop.add(ind);
        }
        return pop;
    }

    private double evaluateFitness(Map<String, BigDecimal> allocation, List<CampaignInitiative> initiatives,
                                    CampaignPortfolio portfolio, BigDecimal totalBudget) {
        return calculateAvgROI(allocation, initiatives, totalBudget);
    }

    private void normalize(Map<String, BigDecimal> allocation, BigDecimal total) {
        BigDecimal sum = allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal factor = total.divide(sum, 6, RoundingMode.HALF_UP);
            allocation.replaceAll((k, v) -> v.multiply(factor).setScale(2, RoundingMode.HALF_UP));
        }
    }

    // ========================================================================
    // 查询
    // ========================================================================

    @Transactional(readOnly = true)
    public Optional<CampaignOptimizationResult> getResult(String id) {
        return optimizationResultRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CampaignOptimizationResult> getHistory(String portfolioId, int page, int size) {
        return optimizationResultRepository.findByPortfolioIdOrderByCreatedAtDesc(
                portfolioId, org.springframework.data.domain.PageRequest.of(page, size));
    }

    // ========================================================================
    // 辅助
    // ========================================================================

    private String getSegment(CampaignInitiative init) {
        if (init.getRuleConfig() != null && init.getRuleConfig().get("segment") != null) {
            return init.getRuleConfig().get("segment").toString();
        }
        return "ALL";
    }

    private double calculateAvgROI(Map<String, BigDecimal> allocations, List<OptCandidate> candidates) {
        double weightedSum = 0; double totalW = 0;
        for (var entry : allocations.entrySet()) {
            OptCandidate c = candidates.stream().filter(x -> x.initiativeId.equals(entry.getKey())).findFirst().orElse(null);
            double w = entry.getValue().doubleValue();
            weightedSum += (c != null ? c.roi : 1.0) * w;
            totalW += w;
        }
        return totalW > 0 ? weightedSum / totalW : 0;
    }

    private double calculateAvgROI(Map<String, BigDecimal> allocations, List<CampaignInitiative> initiatives, BigDecimal totalBudget) {
        // 简化：基于 initiative priority 估算 ROI
        double total = 0; int count = 0;
        for (var entry : allocations.entrySet()) {
            CampaignInitiative init = initiatives.stream().filter(x -> x.getId().equals(entry.getKey())).findFirst().orElse(null);
            double roi = init != null && init.getPriority() != null ? (200.0 - init.getPriority()) / 50.0 : 1.5;
            total += roi * entry.getValue().doubleValue();
            count++;
        }
        return count > 0 ? total / totalBudget.doubleValue() : 0;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return "{}"; }
    }

    // Internal candidate holder
    private static class OptCandidate {
        final String initiativeId;
        final String initiativeName;
        final double roi;
        final int priority;
        OptCandidate(String id, String name, double roi, int priority) {
            this.initiativeId = id; this.initiativeName = name; this.roi = roi; this.priority = priority;
        }
    }
}
