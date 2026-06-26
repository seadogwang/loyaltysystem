package com.loyalty.platform.campaign.planning.controller;

import com.loyalty.platform.campaign.planning.dto.CreateWorkspaceRequest;
import com.loyalty.platform.campaign.planning.dto.WorkspaceContext;
import com.loyalty.platform.campaign.planning.service.WorkspaceService;
import com.loyalty.platform.common.dto.ApiResponse;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspace;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspaceMember;
import com.loyalty.platform.domain.entity.campaign.CampaignWorkspaceSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 营销工作区 REST API。
 */
@RestController
@RequestMapping("/api/campaign/workspace")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    /**
     * 创建工作区。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignWorkspace>> createWorkspace(
            @RequestBody CreateWorkspaceRequest request) {
        CampaignWorkspace workspace = workspaceService.createWorkspace(request);
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    /**
     * 获取工作区详情。
     */
    @GetMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<CampaignWorkspace>> getWorkspace(
            @PathVariable String workspaceId) {
        CampaignWorkspace workspace = workspaceService.getWorkspace(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    /**
     * 更新工作区。
     */
    @PutMapping("/{workspaceId}")
    public ResponseEntity<ApiResponse<CampaignWorkspace>> updateWorkspace(
            @PathVariable String workspaceId,
            @RequestBody Map<String, Object> body) {
        CampaignWorkspace workspace = workspaceService.updateWorkspace(
                workspaceId,
                (String) body.get("name"),
                (String) body.get("description"),
                (Map<String, Object>) body.get("config"));
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    /**
     * 归档工作区。
     */
    @PostMapping("/{workspaceId}/archive")
    public ResponseEntity<ApiResponse<CampaignWorkspace>> archiveWorkspace(
            @PathVariable String workspaceId) {
        CampaignWorkspace workspace = workspaceService.archiveWorkspace(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    /**
     * 加载工作区上下文。
     */
    @GetMapping("/{workspaceId}/context")
    public ResponseEntity<ApiResponse<WorkspaceContext>> loadContext(
            @PathVariable String workspaceId) {
        WorkspaceContext context = workspaceService.loadContext(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(context));
    }

    /**
     * 获取工作区快照列表。
     */
    @GetMapping("/{workspaceId}/snapshots")
    public ResponseEntity<ApiResponse<List<CampaignWorkspaceSnapshot>>> getSnapshots(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String type) {
        List<CampaignWorkspaceSnapshot> snapshots = workspaceService.getSnapshots(workspaceId, type);
        return ResponseEntity.ok(ApiResponse.success(snapshots));
    }

    /**
     * 添加工作区成员。
     */
    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<ApiResponse<CampaignWorkspaceMember>> addMember(
            @PathVariable String workspaceId,
            @RequestBody Map<String, String> body) {
        CampaignWorkspaceMember member = workspaceService.addMember(
                workspaceId, body.get("userId"), body.get("role"));
        return ResponseEntity.ok(ApiResponse.success(member));
    }

    /**
     * 移除工作区成员。
     */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String workspaceId,
            @PathVariable String userId) {
        workspaceService.removeMember(workspaceId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 按 Program 查询工作区列表。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CampaignWorkspace>>> listWorkspaces(
            @RequestParam(required = false) String programCode,
            @RequestParam(required = false) String userId) {
        List<CampaignWorkspace> workspaces;
        if (programCode != null) {
            workspaces = workspaceService.getWorkspacesByProgram(programCode);
        } else if (userId != null) {
            workspaces = workspaceService.getUserWorkspaces(userId);
        } else {
            workspaces = workspaceService.getUserWorkspaces("system");
        }
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }
}
