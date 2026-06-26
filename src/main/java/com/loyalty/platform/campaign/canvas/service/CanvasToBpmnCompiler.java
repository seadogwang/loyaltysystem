package com.loyalty.platform.campaign.canvas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loyalty.platform.campaign.canvas.dto.CanvasDag;
import com.loyalty.platform.campaign.canvas.dto.CanvasEdge;
import com.loyalty.platform.campaign.canvas.dto.CanvasNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Canvas → BPMN 编译器。
 *
 * <p>将可视化的 Canvas DAG 编译为 Zeebe 可执行的 BPMN XML。
 * 流程：Canvas DAG → 拓扑排序 → 节点 BPMN 生成 → Sequence Flow → BPMN 包装
 */
@Component
public class CanvasToBpmnCompiler {

    private static final Logger log = LoggerFactory.getLogger(CanvasToBpmnCompiler.class);

    private final DagValidatorService validator;
    private final ObjectMapper objectMapper;

    public CanvasToBpmnCompiler(DagValidatorService validator, ObjectMapper objectMapper) {
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    /**
     * 从 CanvasDAG 对象编译为 BPMN XML。
     */
    public String compile(CanvasDag dag) {
        // 拓扑排序
        List<String> sorted = validator.topologicalSort(dag);
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("DAG 包含循环依赖，无法编译");
        }

        Map<String, CanvasNode> nodeMap = dag.getNodes().stream()
                .collect(Collectors.toMap(CanvasNode::getId, n -> n));

        StringBuilder bpmn = new StringBuilder();
        bpmn.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        bpmn.append("<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        bpmn.append("  xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\"\n");
        bpmn.append("  targetNamespace=\"http://loyalty.campaign\">\n");
        bpmn.append("  <bpmn:process id=\"campaign_process\" isExecutable=\"true\">\n");

        // Start Event
        bpmn.append("    <bpmn:startEvent id=\"StartEvent_1\" name=\"Start\"/>\n");

        // 按拓扑顺序生成节点
        String previousId = "StartEvent_1";
        for (String nodeId : sorted) {
            CanvasNode node = nodeMap.get(nodeId);
            if (node == null) continue;

            String bpmnId = "Activity_" + node.getId();
            String bpmnXml = generateNodeBpmn(node, bpmnId);
            bpmn.append(bpmnXml);

            // Sequence Flow
            bpmn.append(generateSequenceFlow(previousId, bpmnId, null));
            previousId = bpmnId;
        }

        // End Event
        String endId = "EndEvent_1";
        bpmn.append("    <bpmn:endEvent id=\"").append(endId).append("\" name=\"End\"/>\n");
        bpmn.append(generateSequenceFlow(previousId, endId, null));

        // 处理条件分支
        if (dag.getEdges() != null) {
            for (CanvasEdge edge : dag.getEdges()) {
                if (edge.getCondition() != null && !edge.getCondition().isEmpty()) {
                    String sourceBpmnId = "Activity_" + edge.getSource();
                    String targetBpmnId = "Activity_" + edge.getTarget();
                    bpmn.append(generateSequenceFlow(sourceBpmnId, targetBpmnId, edge.getCondition()));
                }
            }
        }

        bpmn.append("  </bpmn:process>\n");
        bpmn.append("</bpmn:definitions>");

        String result = bpmn.toString();
        log.info("BPMN compilation completed: {} nodes compiled", sorted.size());
        return result;
    }

    /**
     * 从 JSON 字符串编译为 BPMN XML。
     */
    public String compileFromJson(String graphJson) {
        try {
            CanvasDag dag = objectMapper.readValue(graphJson, CanvasDag.class);
            return compile(dag);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 DAG JSON: " + e.getMessage(), e);
        }
    }

    private String generateNodeBpmn(CanvasNode node, String bpmnId) {
        String type = node.getType() != null ? node.getType() : "SEND_EMAIL";
        String name = node.getLabel() != null ? node.getLabel() : type;

        return switch (type) {
            case "AUDIENCE_FILTER" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-audience-filter\" />\n" +
                "      <zeebe:ioMapping>\n" +
                "        <zeebe:input source=\"$.segment\" target=\"segment\" />\n" +
                "      </zeebe:ioMapping>\n" +
                "    </bpmn:serviceTask>\n";
            case "CONDITION" ->
                "    <bpmn:exclusiveGateway id=\"" + bpmnId + "\" name=\"" + name + "\" />\n";
            case "AI_SCORE" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-ai-score\" />\n" +
                "    </bpmn:serviceTask>\n";
            case "DELAY" ->
                "    <bpmn:intermediateCatchEvent id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <bpmn:timerEventDefinition>\n" +
                "        <bpmn:timeDuration>PT1H</bpmn:timeDuration>\n" +
                "      </bpmn:timerEventDefinition>\n" +
                "    </bpmn:intermediateCatchEvent>\n";
            case "APPROVAL" ->
                "    <bpmn:userTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-approval\" />\n" +
                "      <zeebe:assignmentDefinition zeebe:candidateGroups=\"approvers\" />\n" +
                "    </bpmn:userTask>\n";
            case "WEBHOOK" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-webhook\" />\n" +
                "    </bpmn:serviceTask>\n";
            case "OFFER_POINTS" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-offer-points\" />\n" +
                "    </bpmn:serviceTask>\n";
            case "OFFER_COUPON" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-offer-coupon\" />\n" +
                "    </bpmn:serviceTask>\n";
            case "TIER_UPGRADE" ->
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-tier-upgrade\" />\n" +
                "    </bpmn:serviceTask>\n";
            default -> // SEND_EMAIL, SEND_SMS, SEND_PUSH
                "    <bpmn:serviceTask id=\"" + bpmnId + "\" name=\"" + name + "\">\n" +
                "      <zeebe:taskDefinition type=\"campaign-send-" + type.toLowerCase().replace("send_", "") + "\" />\n" +
                "    </bpmn:serviceTask>\n";
        };
    }

    private String generateSequenceFlow(String fromId, String toId, String condition) {
        String flowId = "Flow_" + fromId + "_" + toId;
        if (condition != null && !condition.isEmpty()) {
            return "    <bpmn:sequenceFlow id=\"" + flowId + "\" sourceRef=\"" + fromId +
                   "\" targetRef=\"" + toId + "\">\n" +
                   "      <bpmn:conditionExpression xsi:type=\"bpmn:tFormalExpression\">" +
                   condition + "</bpmn:conditionExpression>\n" +
                   "    </bpmn:sequenceFlow>\n";
        }
        return "    <bpmn:sequenceFlow id=\"" + flowId + "\" sourceRef=\"" + fromId +
               "\" targetRef=\"" + toId + "\"/>\n";
    }
}
