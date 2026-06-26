package com.loyalty.platform.campaign.planning.controller;

import com.loyalty.platform.campaign.planning.dto.CreatePortfolioRequest;
import com.loyalty.platform.campaign.planning.dto.PortfolioContext;
import com.loyalty.platform.campaign.planning.service.PortfolioService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignPortfolio;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 营销组合 REST API。
 */
@RestController
@RequestMapping("/api/campaign/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    /**
     * 创建组合。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignPortfolio>> createPortfolio(
            @RequestBody CreatePortfolioRequest request) {
        CampaignPortfolio portfolio = portfolioService.createPortfolio(request);
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    /**
     * 获取组合详情。
     */
    @GetMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<CampaignPortfolio>> getPortfolio(
            @PathVariable String portfolioId) {
        CampaignPortfolio portfolio = portfolioService.getPortfolio(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    /**
     * 获取 Workspace 下所有组合。
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<CampaignPortfolio>>> getByWorkspace(
            @PathVariable String workspaceId) {
        List<CampaignPortfolio> portfolios = portfolioService.getPortfoliosByWorkspace(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(portfolios));
    }

    /**
     * 运行优化。
     */
    @PostMapping("/{portfolioId}/optimize")
    public ResponseEntity<ApiResponse<CampaignPortfolio>> optimize(
            @PathVariable String portfolioId) {
        CampaignPortfolio portfolio = portfolioService.optimizePortfolio(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    /**
     * 锁定组合。
     */
    @PostMapping("/{portfolioId}/lock")
    public ResponseEntity<ApiResponse<CampaignPortfolio>> lock(
            @PathVariable String portfolioId) {
        CampaignPortfolio portfolio = portfolioService.lockPortfolio(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(portfolio));
    }

    /**
     * 获取组合上下文。
     */
    @GetMapping("/{portfolioId}/context")
    public ResponseEntity<ApiResponse<PortfolioContext>> loadContext(
            @PathVariable String portfolioId) {
        PortfolioContext context = portfolioService.loadContext(portfolioId);
        return ResponseEntity.ok(ApiResponse.success(context));
    }
}
