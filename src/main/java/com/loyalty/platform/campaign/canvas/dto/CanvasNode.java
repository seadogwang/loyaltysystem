package com.loyalty.platform.campaign.canvas.dto;

import lombok.*;

import java.util.Map;

/**
 * Canvas DAG 节点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasNode {
    private String id;
    private String type;       // AUDIENCE_FILTER / CONDITION / SPLIT / AI_SCORE / SEND_EMAIL / SEND_SMS / WAIT / WEBHOOK / APPROVAL / OFFER_POINTS / OFFER_COUPON / TIER_UPGRADE
    private String label;
    private Map<String, Object> config;
    private Double x;
    private Double y;
}
