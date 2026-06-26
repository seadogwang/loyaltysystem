package com.loyalty.platform.campaign.canvas.dto;

import lombok.*;

/**
 * Canvas DAG 边。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasEdge {
    private String id;
    private String source;
    private String target;
    private String condition;  // 条件分支表达式（CONDITION 节点使用）
    private String label;
}
