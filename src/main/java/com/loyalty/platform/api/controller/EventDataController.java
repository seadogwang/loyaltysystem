package com.loyalty.platform.api.controller;

import com.loyalty.platform.api.service.EventService;
import com.loyalty.platform.common.context.TenantContext;
import com.loyalty.platform.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 事件数据查询 API —— 按 schema 类型提供对应的事件查询接口。
 */
@Tag(name = "Event Data", description = "事件数据查询 — 按 schema 类型（ORDER/BEHAVIOR/TRANSACTION/MEMBER/OrderItem）查询事件数据")
@RestController
@RequestMapping("/api/event-data")
public class EventDataController {

    private final EventService eventService;

    public EventDataController(EventService eventService) {
        this.eventService = eventService;
    }

    // ==================== 订单事件 (ORDER schema) ====================

    @Operation(summary = "订单事件列表", description = "查询订单事件（ORDER schema），支持按会员、渠道、交易状态筛选")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listOrders(
            @Parameter(description = "会员 ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "渠道（TMALL/JD/DOUYIN/WECHAT_MINI）") @RequestParam(required = false) String channel,
            @Parameter(description = "交易状态") @RequestParam(required = false) String tradeStatus,
            @Parameter(description = "页码，默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        String pc = TenantContext.getRequired();
        var result = eventService.queryEvents(pc, "ORDER", memberId, channel, tradeStatus, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "订单详情", description = "查询单个订单详情，含 OrderItem 明细行")
    @GetMapping("/orders/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderDetail(
            @Parameter(description = "事件 ID") @PathVariable String eventId) {
        String pc = TenantContext.getRequired();
        var result = eventService.getOrderDetail(pc, eventId);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.error("ERR_NOT_FOUND", "订单不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 行为事件 (BEHAVIOR schema) ====================

    @Operation(summary = "行为事件列表", description = "查询行为事件（签到、分享、注册、登录等）")
    @GetMapping("/behaviors")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listBehaviors(
            @Parameter(description = "会员 ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "事件类型（CHECK_IN/SHARE/REGISTER/SIGN_IN）") @RequestParam(required = false) String eventType,
            @Parameter(description = "渠道") @RequestParam(required = false) String channel,
            @Parameter(description = "页码，默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        String pc = TenantContext.getRequired();
        var result = eventService.queryBehaviorEvents(pc, memberId, eventType, channel, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 全部交易事件 (TRANSACTION schema) ====================

    @Operation(summary = "全部交易事件", description = "查询所有类型的交易事件，支持时间范围过滤")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listTransactions(
            @Parameter(description = "会员 ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "事件类型") @RequestParam(required = false) String eventType,
            @Parameter(description = "渠道") @RequestParam(required = false) String channel,
            @Parameter(description = "起始时间（ISO 8601）") @RequestParam(required = false) String dateFrom,
            @Parameter(description = "截止时间（ISO 8601）") @RequestParam(required = false) String dateTo,
            @Parameter(description = "页码，默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        String pc = TenantContext.getRequired();
        var result = eventService.queryAllTransactions(pc, memberId, eventType, channel, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ==================== 通用查询 ====================

    @Operation(summary = "通用事件查询", description = "按 schema 类型动态查询（ORDER/BEHAVIOR/MEMBER/TRANSACTION/OrderItem）")
    @GetMapping("/{schemaType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryBySchema(
            @Parameter(description = "Schema 类型（ORDER/BEHAVIOR/MEMBER/TRANSACTION/OrderItem）") @PathVariable String schemaType,
            @Parameter(description = "会员 ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "页码，默认 0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数，默认 20") @RequestParam(defaultValue = "20") int size) {
        String pc = TenantContext.getRequired();
        var result = eventService.queryBySchemaType(pc, schemaType.toUpperCase(), memberId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
