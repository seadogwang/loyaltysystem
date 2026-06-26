package com.loyalty.platform.campaign.canvas.dto;

import lombok.*;

import java.util.List;

/**
 * 完整 Canvas DAG。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasDag {
    private List<CanvasNode> nodes;
    private List<CanvasEdge> edges;
}
