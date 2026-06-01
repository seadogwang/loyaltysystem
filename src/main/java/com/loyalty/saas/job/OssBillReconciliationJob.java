package com.loyalty.saas.job;

import com.loyalty.saas.common.event.EventBridge;
import com.loyalty.saas.domain.entity.EventFact;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大促飞行模式离线对账补偿任务。
 *
 * <p>定时从天猫/京东 OSS 下载 CSV 离线对账单文件，解析账单后
 * 比对本地 event_inbox 中的已处理事件。发现漏单时自动构建补偿事件
 * 推入 EventBridge 重新消费。
 *
 * <p><b>大促飞行模式说明</b>：在双11等大促期间，第三方平台（天猫/京东）
 * 会以离线 CSV 文件方式提供对账单。本任务负责下载、解析、对账、补偿。
 *
 * <p><b>租户隔离</b>：通过 {@link TenantAwareJob#forEachTenant} 保证。
 *
 * @author Loyalty SaaS Architecture Team
 * @since 1.0.0
 */
@Component
public class OssBillReconciliationJob extends TenantAwareJob {

    private final EventBridge eventBridge;

    public OssBillReconciliationJob(@Autowired(required = false) EventBridge eventBridge) {
        this.eventBridge = eventBridge;
    }

    @Override protected String getJobName() { return "OssBillReconciliationJob"; }

    /** 每小时执行一次对账 */
    @Scheduled(cron = "0 0 * * * ?")
    public void execute() {
        forEachTenant(this::reconcileTenant);
    }

    void reconcileTenant(String programCode) {
        // 1. 获取该租户下所有活跃渠道的 OSS 配置
        @SuppressWarnings("unchecked")
        List<Object[]> channels = em.createNativeQuery(
                "SELECT channel, auth_config FROM channel_adapter_config "
                        + "WHERE program_code = ? AND status = 'ACTIVE'",
                Object[].class)
                .setParameter(1, programCode)
                .getResultList();

        if (channels.isEmpty()) {
            log.debug("[OssReconciliation] 租户 [{}] 无活跃渠道，跳过", programCode);
            return;
        }

        int totalMissing = 0;

        for (Object[] channelRow : channels) {
            String channel = (String) channelRow[0];
            try {
                int missing = reconcileChannel(programCode, channel);
                totalMissing += missing;
            } catch (Exception e) {
                log.error("[OssReconciliation] 渠道 [{}] 对账异常: {}", channel, e.getMessage());
            }
        }

        if (totalMissing > 0) {
            log.warn("[OssReconciliation] 租户 [{}] 发现漏单: {} 条，已推入补偿队列", programCode, totalMissing);
        }
    }

    /**
     * 对单个渠道进行 CSV 对账比对。
     */
    int reconcileChannel(String programCode, String channel) {
        // 1. 从 OSS 下载 CSV 账单（骨架：实际需根据 channel 配置构建 OSS URL）
        List<Map<String, String>> billRecords = downloadBill(programCode, channel);
        if (billRecords.isEmpty()) return 0;

        // 2. 批量查询本地 event_inbox 中已处理的事件 ID
        List<String> localEventIds = getLocalProcessedEventIds(programCode, channel, billRecords);

        // 3. 找出漏单（CSV 有但本地没有的）
        int missingCount = 0;
        for (Map<String, String> bill : billRecords) {
            String sourceEventId = bill.get("source_event_id");
            if (!localEventIds.contains(sourceEventId)) {
                // 漏单！构建补偿事件推入 EventBridge
                compensateMissingEvent(programCode, channel, bill);
                missingCount++;
            }
        }

        if (missingCount > 0) {
            log.warn("[OssReconciliation] 渠道 [{}] 漏单: {}/{}", channel, missingCount, billRecords.size());
        }

        return missingCount;
    }

    /**
     * 从 OSS 下载 CSV 对账单。
     * 骨架实现：实际生产环境需接入阿里云 OSS / 京东 JOS SDK。
     */
    List<Map<String, String>> downloadBill(String programCode, String channel) {
        List<Map<String, String>> records = new ArrayList<>();

        try {
            // 构建 OSS URL（实际从 channel_adapter_config.rate_limit_config 读取）
            String ossUrl = String.format(
                    "https://oss.example.com/bills/%s/%s/bill_%s.csv",
                    programCode, channel, java.time.LocalDate.now().minusDays(1));

            HttpURLConnection conn = (HttpURLConnection) new URL(ossUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                // 手动解析 CSV（header line + data lines）
                String headerLine = reader.readLine();
                if (headerLine != null) {
                    String[] headers = headerLine.split(",");
                    String dataLine;
                    while ((dataLine = reader.readLine()) != null) {
                        String[] values = dataLine.split(",", -1);
                        Map<String, String> record = new LinkedHashMap<>();
                        for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                            record.put(headers[i].trim(), values[i].trim());
                        }
                        records.add(record);
                    }
                }
                reader.close();
            } else {
                log.debug("[OssReconciliation] OSS 账单文件不存在: {}", ossUrl);
            }
        } catch (Exception e) {
            log.error("[OssReconciliation] 下载账单失败: channel={}", channel, e);
        }

        return records;
    }

    /**
     * 查询本地已处理的 source_event_id 列表。
     */
    @SuppressWarnings("unchecked")
    List<String> getLocalProcessedEventIds(String programCode, String channel,
                                            List<Map<String, String>> billRecords) {
        if (billRecords.isEmpty()) return List.of();

        // 构建 IN 查询参数
        List<String> sourceIds = billRecords.stream()
                .map(r -> r.get("source_event_id"))
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        if (sourceIds.isEmpty()) return List.of();

        // 动态拼 SQL IN（简化，实际用 JPA Criteria）
        StringBuilder sql = new StringBuilder(
                "SELECT source_event_id FROM event_inbox WHERE program_code = ? AND source_channel = ? ");
        // 简化：取最近1000条对账
        return em.createNativeQuery(
                "SELECT source_event_id FROM event_inbox "
                        + "WHERE program_code = ? AND source_channel = ? "
                        + "AND status IN ('SUCCEEDED', 'PROCESSED') "
                        + "ORDER BY first_seen_at DESC LIMIT 1000",
                Object.class)
                .setParameter(1, programCode)
                .setParameter(2, channel)
                .getResultList()
                .stream()
                .map(Object::toString)
                .toList();
    }

    /**
     * 构建补偿事件推入 EventBridge。
     */
    void compensateMissingEvent(String programCode, String channel, Map<String, String> bill) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(bill);
        payload.put("_compensation", true);
        payload.put("_compensation_source", "OSS_BILL_RECONCILIATION");
        payload.put("_compensation_time", LocalDateTime.now().toString());

        // 插入 event_inbox
        em.createNativeQuery(
                "INSERT INTO event_inbox (program_code, source_channel, source_event_id, "
                        + "idempotency_key, payload_hash, payload, signature_verified, "
                        + "status, retry_count, first_seen_at) "
                        + "VALUES (?,?,?,?,?,?::jsonb,?,?,?,?)")
                .setParameter(1, programCode)
                .setParameter(2, channel)
                .setParameter(3, bill.getOrDefault("source_event_id", "UNKNOWN"))
                .setParameter(4, programCode + ":OSS_COMP:" + bill.getOrDefault("source_event_id", ""))
                .setParameter(5, "oss-compensation")
                .setParameter(6, toJsonString(payload))
                .setParameter(7, false)
                .setParameter(8, "RECEIVED")
                .setParameter(9, 0)
                .setParameter(10, LocalDateTime.now())
                .executeUpdate();

        // 推入 EventBridge
        if (eventBridge != null) {
            EventFact fact = new EventFact(programCode, "OSS_COMPENSATION",
                    bill.get("member_id"), channel, Instant.now(),
                    programCode + ":OSS:" + bill.get("source_event_id"), null, payload);
            eventBridge.publish("loyalty-events", bill.getOrDefault("member_id", "unknown"), fact);
        }

        log.info("[OssReconciliation] 漏单补偿: channel={}, sourceEventId={}",
                channel, bill.get("source_event_id"));
    }

    private String toJsonString(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}