-- V1_10: channel_adapter_config 表扩展 — 增加入站/出站映射规则 JSONB 列
-- 存储 ChartDB MappingEditor 配置的字段映射规则，以 operationCode 为键

ALTER TABLE channel_adapter_config
    ADD COLUMN IF NOT EXISTS inbound_mappings JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS outbound_mappings JSONB DEFAULT '{}';

COMMENT ON COLUMN channel_adapter_config.inbound_mappings IS '入站映射规则 — Map<operationCode, List<MappingRule>>。API响应 → 业务实体';
COMMENT ON COLUMN channel_adapter_config.outbound_mappings IS '出站映射规则 — Map<operationCode, List<MappingRule>>。业务实体 → API请求';
