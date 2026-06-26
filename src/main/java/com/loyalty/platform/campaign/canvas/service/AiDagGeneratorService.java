package com.loyalty.platform.campaign.canvas.service;

import com.loyalty.platform.campaign.canvas.dto.AIRequest;
import com.loyalty.platform.campaign.canvas.dto.CanvasDag;
import com.loyalty.platform.campaign.canvas.dto.CanvasEdge;
import com.loyalty.platform.campaign.canvas.dto.CanvasNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI → DAG 生成器 — 根据自然语言描述生成营销流程 DAG。
 *
 * <p>开发阶段：基于规则的模板生成器。
 * 生产阶段：接入 LLM (如 GPT-4/Claude)，通过强约束 Prompt 生成合法 DAG。
 */
@Service
public class AiDagGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AiDagGeneratorService.class);

    /**
     * 根据业务输入生成 DAG。
     *
     * <p>System Prompt（生产阶段）：
     * <pre>
     * You are a Workflow DAG Generator. Output MUST be valid JSON with nodes and edges.
     * Available node types: AUDIENCE_FILTER, CONDITION, SPLIT, AI_SCORE,
     *   SEND_EMAIL, SEND_SMS, SEND_PUSH, WAIT, WEBHOOK, APPROVAL,
     *   OFFER_POINTS, OFFER_COUPON, TIER_UPGRADE.
     * Constraints: DAG, max depth 10, max nodes 50.
     * Output format: { "nodes": [...], "edges": [...] }
     * </pre>
     */
    public CanvasDag generate(AIRequest request) {
        log.info("AI DAG generation requested: goal={}, audience={}, channel={}",
                request.getGoal(), request.getAudience(), request.getChannel());

        // 开发阶段：基于规则的模板生成
        CanvasDag dag = generateTemplate(request);

        log.info("AI DAG generated: {} nodes, {} edges",
                dag.getNodes().size(),
                dag.getEdges() != null ? dag.getEdges().size() : 0);
        return dag;
    }

    /**
     * 基于规则的模板生成（开发阶段）。
     *
     * <p>根据目标类型和渠道生成标准流程模板。
     */
    private CanvasDag generateTemplate(AIRequest request) {
        List<CanvasNode> nodes = new ArrayList<>();
        List<CanvasEdge> edges = new ArrayList<>();

        String goalType = request.getGoal() != null ? request.getGoal().toUpperCase() : "";
        String channel = request.getChannel() != null ? request.getChannel().toLowerCase() : "email";

        nodes.add(new CanvasNode("N1", "AUDIENCE_FILTER", "人群筛选",
                Map.of("segment", "target_segment"), 100.0, 100.0));

        nodes.add(new CanvasNode("N2", "AI_SCORE", "AI 评分",
                Map.of("model", "conversion_v2"), 300.0, 100.0));

        nodes.add(new CanvasNode("N3", "CONDITION", "评分判断",
                Map.of("field", "score", "operator", ">", "value", 0.7), 500.0, 100.0));

        if (channel.contains("email") || channel.contains("邮件")) {
            nodes.add(new CanvasNode("N4", "SEND_EMAIL", "发送邮件",
                    Map.of("asset_id", "ASSET_EMAIL_001"), 700.0, 50.0));
        }
        if (channel.contains("sms") || channel.contains("短信")) {
            nodes.add(new CanvasNode("N5", "SEND_SMS", "发送短信",
                    Map.of("asset_id", "ASSET_SMS_001"), 700.0, 200.0));
        }
        if (channel.contains("push") || channel.contains("推送")) {
            nodes.add(new CanvasNode("N6", "SEND_PUSH", "发送推送",
                    Map.of("asset_id", "ASSET_PUSH_001"), 900.0, 125.0));
        }

        // 如果是留存/召回目标，增加积分/优惠券
        if (goalType.contains("RETENTION") || goalType.contains("召回") || goalType.contains("留存")) {
            nodes.add(new CanvasNode("N7", "OFFER_COUPON", "发送优惠券",
                    Map.of("coupon_id", "COUPON_RETENTION_001"), 900.0, 50.0));
        }
        if (goalType.contains("GROWTH") || goalType.contains("增长") || goalType.contains("升级")) {
            nodes.add(new CanvasNode("N8", "TIER_UPGRADE", "等级直升",
                    Map.of("target_tier", "SILVER"), 900.0, 200.0));
        }

        // 连接边
        edges.add(new CanvasEdge("E1", "N1", "N2", null, null));
        edges.add(new CanvasEdge("E2", "N2", "N3", null, null));
        edges.add(new CanvasEdge("E3", "N3", "N4", "score >= 0.7", "高评分"));
        edges.add(new CanvasEdge("E4", "N3", "N5", "score < 0.7", "低评分"));

        return new CanvasDag(nodes, edges);
    }
}
