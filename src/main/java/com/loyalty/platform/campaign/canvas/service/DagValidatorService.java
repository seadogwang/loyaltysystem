package com.loyalty.platform.campaign.canvas.service;

import com.loyalty.platform.campaign.canvas.dto.CanvasDag;
import com.loyalty.platform.campaign.canvas.dto.CanvasEdge;
import com.loyalty.platform.campaign.canvas.dto.CanvasNode;
import com.loyalty.platform.campaign.canvas.dto.GraphValidationResult;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DAG 校验器 — 检测循环依赖、孤立节点、缺失起止节点等。
 */
@Service
public class DagValidatorService {

    /** 有效节点类型转换规则 */
    private static final Map<String, Set<String>> VALID_TRANSITIONS = new LinkedHashMap<>();
    static {
        VALID_TRANSITIONS.put("START", Set.of("AUDIENCE_FILTER", "EVENT_TRIGGER", "CONDITION", "AI_SCORE"));
        VALID_TRANSITIONS.put("AUDIENCE_FILTER", Set.of("CONDITION", "AI_SCORE", "SPLIT", "SEND_EMAIL", "SEND_SMS", "SEND_PUSH", "OFFER_POINTS", "OFFER_COUPON", "TIER_UPGRADE", "DELAY", "WEBHOOK", "APPROVAL"));
        VALID_TRANSITIONS.put("CONDITION", Set.of("SEND_EMAIL", "SEND_SMS", "SEND_PUSH", "OFFER_POINTS", "OFFER_COUPON", "DELAY", "MERGE", "WEBHOOK", "APPROVAL", "END"));
        VALID_TRANSITIONS.put("SPLIT", Set.of("AUDIENCE_FILTER", "SEND_EMAIL", "SEND_SMS", "CONDITION", "AI_SCORE"));
        VALID_TRANSITIONS.put("AI_SCORE", Set.of("CONDITION", "SPLIT", "END"));
        VALID_TRANSITIONS.put("SEND_EMAIL", Set.of("CONDITION", "DELAY", "SPLIT", "END", "APPROVAL"));
        VALID_TRANSITIONS.put("SEND_SMS", Set.of("CONDITION", "DELAY", "SPLIT", "END", "APPROVAL"));
        VALID_TRANSITIONS.put("SEND_PUSH", Set.of("CONDITION", "DELAY", "END"));
        VALID_TRANSITIONS.put("OFFER_POINTS", Set.of("CONDITION", "DELAY", "SEND_EMAIL", "SEND_SMS", "END"));
        VALID_TRANSITIONS.put("OFFER_COUPON", Set.of("CONDITION", "DELAY", "SEND_EMAIL", "SEND_SMS", "END"));
        VALID_TRANSITIONS.put("TIER_UPGRADE", Set.of("SEND_EMAIL", "SEND_SMS", "END"));
        VALID_TRANSITIONS.put("DELAY", Set.of("AUDIENCE_FILTER", "CONDITION", "AI_SCORE", "SEND_EMAIL", "SEND_SMS", "SEND_PUSH", "OFFER_POINTS", "OFFER_COUPON", "WEBHOOK", "END"));
        VALID_TRANSITIONS.put("WEBHOOK", Set.of("CONDITION", "DELAY", "END"));
        VALID_TRANSITIONS.put("APPROVAL", Set.of("SEND_EMAIL", "SEND_SMS", "DELAY", "END"));
        VALID_TRANSITIONS.put("MERGE", Set.of("CONDITION", "AI_SCORE", "SEND_EMAIL", "END"));
        VALID_TRANSITIONS.put("END", Set.of());
    }

    /**
     * 校验完整的 DAG。
     */
    public GraphValidationResult validate(CanvasDag dag) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (dag.getNodes() == null || dag.getNodes().isEmpty()) {
            errors.add("DAG 不能为空");
            return buildResult(false, errors, warnings);
        }

        // 1. 检测循环依赖（DFS）
        if (hasCycle(dag)) {
            errors.add("DAG 包含循环依赖，请移除环形连接");
        }

        // 2. 检测孤立节点
        Set<String> connectedNodes = new HashSet<>();
        if (dag.getEdges() != null) {
            for (CanvasEdge e : dag.getEdges()) {
                connectedNodes.add(e.getSource());
                connectedNodes.add(e.getTarget());
            }
        }
        for (CanvasNode node : dag.getNodes()) {
            if (!connectedNodes.contains(node.getId())) {
                warnings.add("节点「" + (node.getLabel() != null ? node.getLabel() : node.getId()) + "」是孤立节点（无连接）");
            }
        }

        // 3. 检测无效的节点类型转换
        if (dag.getEdges() != null) {
            Map<String, String> nodeTypes = new HashMap<>();
            for (CanvasNode n : dag.getNodes()) {
                nodeTypes.put(n.getId(), n.getType());
            }
            for (CanvasEdge e : dag.getEdges()) {
                String sourceType = nodeTypes.get(e.getSource());
                String targetType = nodeTypes.get(e.getTarget());
                if (sourceType != null && targetType != null) {
                    Set<String> allowed = VALID_TRANSITIONS.getOrDefault(sourceType, Collections.emptySet());
                    if (!allowed.contains(targetType)) {
                        warnings.add("不推荐的节点转换: " + sourceType + " → " + targetType);
                    }
                }
            }
        }

        return buildResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * 拓扑排序（返回排序后的节点 ID 列表）。
     * 如果存在环，返回空列表。
     */
    public List<String> topologicalSort(CanvasDag dag) {
        Map<String, List<String>> adjList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (CanvasNode node : dag.getNodes()) {
            adjList.putIfAbsent(node.getId(), new ArrayList<>());
            inDegree.putIfAbsent(node.getId(), 0);
        }
        if (dag.getEdges() != null) {
            for (CanvasEdge e : dag.getEdges()) {
                adjList.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e.getTarget());
                inDegree.merge(e.getTarget(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.offer(entry.getKey());
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(node);
            for (String neighbor : adjList.getOrDefault(node, Collections.emptyList())) {
                inDegree.merge(neighbor, -1, Integer::sum);
                if (inDegree.get(neighbor) == 0) queue.offer(neighbor);
            }
        }

        if (sorted.size() != dag.getNodes().size()) {
            return Collections.emptyList(); // 存在环
        }
        return sorted;
    }

    private boolean hasCycle(CanvasDag dag) {
        return topologicalSort(dag).isEmpty();
    }

    private GraphValidationResult buildResult(boolean valid, List<String> errors, List<String> warnings) {
        return GraphValidationResult.builder()
                .valid(valid)
                .errors(errors)
                .warnings(warnings)
                .build();
    }
}
