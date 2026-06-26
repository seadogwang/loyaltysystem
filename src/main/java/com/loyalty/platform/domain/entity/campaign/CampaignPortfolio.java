package com.loyalty.platform.domain.entity.campaign;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营销组合 — 跨 Initiative 的全局资源优化层。
 */
@Entity
@Table(name = "campaign_portfolio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPortfolio {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** DRAFT / OPTIMIZED / LOCKED / EXECUTING / COMPLETED */
    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "DRAFT";

    /** ROI_MAXIMIZATION / REVENUE_MAXIMIZATION / BALANCED */
    @Column(name = "optimization_mode", length = 32)
    @Builder.Default
    private String optimizationMode = "ROI_MAXIMIZATION";

    @Column(name = "total_budget", precision = 18, scale = 4)
    private BigDecimal totalBudget;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
