import type { EntityNode, EntityRelation, EntityKind, EntityFieldExt } from './types';

// ==================== 字段类型 & 组件 ====================

export const FIELD_TYPES = ['String', 'Integer', 'Long', 'Boolean', 'Double', 'BigDecimal', 'Date', 'DateTime', 'JSONB', 'Text', 'Enum', 'Object', 'Array'];
export const FORMLY_COMPONENTS = ['Input', 'NumberPicker', 'Select', 'Switch', 'DatePicker', 'ImageUploader', 'CascadingAddress'];

// ==================== 颜色 & 徽章 ====================

export const COLORS: Record<EntityKind, string> = { system: '#8c8c8c', business: '#1677ff', api: '#52c41a' };
export const BG_COLORS: Record<EntityKind, string> = { system: '#f5f5f5', business: '#e6f7ff', api: '#f6ffed' };
export const BADGES: Record<EntityKind, string> = { system: '🔒', business: '📦', api: '🔌' };

// ==================== 系统实体预定义 ====================

export const SYSTEM_ENTITIES: EntityNode[] = [
  {
    id: 'sys_member', name: 'Member', displayName: '会员', kind: 'system', x: 60, y: 40, fields: [
      { key: 'member_id', name: '会员ID', type: 'String', primaryKey: true, required: true, locked: true },
      { key: 'program_code', name: '租户代码', type: 'String', required: true, locked: true },
      { key: 'status', name: '状态', type: 'Enum', locked: true },
      { key: 'tier_code', name: '等级', type: 'String' },
      { key: 'schema_version', name: 'Schema版本', type: 'String' },
      { key: 'created_at', name: '创建时间', type: 'DateTime', locked: true },
      { key: 'ext_attributes', name: '扩展属性', type: 'JSONB' },
    ],
  },
  {
    id: 'sys_tx_event', name: 'TransactionEvent', displayName: '交易事件', kind: 'system', x: 400, y: 40, fields: [
      { key: 'event_id', name: '事件ID', type: 'String', primaryKey: true, required: true, locked: true },
      { key: 'member_id', name: '会员ID', type: 'String', required: true, locked: true },
      { key: 'event_type', name: '事件类型', type: 'Enum', locked: true },
      { key: 'channel', name: '渠道', type: 'String', locked: true },
      { key: 'event_time', name: '事件时间', type: 'DateTime', locked: true },
      { key: 'idempotent_key', name: '幂等键', type: 'String', locked: true },
      { key: 'payload', name: '事件数据', type: 'JSONB' },
    ],
  },
  {
    id: 'sys_account', name: 'MemberAccount', displayName: '积分账户', kind: 'system', x: 60, y: 320, fields: [
      { key: 'account_id', name: '账户ID', type: 'Long', primaryKey: true, locked: true },
      { key: 'member_id', name: '会员ID', type: 'Long', required: true, locked: true },
      { key: 'account_type', name: '账户类型', type: 'String', required: true, locked: true },
      { key: 'credit_limit', name: '授信额度', type: 'BigDecimal' },
      { key: 'credit_used', name: '已用授信', type: 'BigDecimal' },
    ],
  },
  {
    id: 'sys_unique', name: 'MemberUniqueKey', displayName: 'One-ID', kind: 'system', x: 400, y: 320, fields: [
      { key: 'id', name: 'ID', type: 'Long', primaryKey: true, locked: true },
      { key: 'member_id', name: '会员ID', type: 'String', locked: true },
      { key: 'channel', name: '渠道', type: 'String', locked: true },
      { key: 'open_id', name: 'OpenID', type: 'String', locked: true },
    ],
  },
  {
    id: 'sys_program', name: 'Program', displayName: '租户计划', kind: 'system', x: 750, y: 40, fields: [
      { key: 'code', name: '代码', type: 'String', primaryKey: true, locked: true },
      { key: 'name', name: '名称', type: 'String', locked: true },
      { key: 'status', name: '状态', type: 'Enum', locked: true },
      { key: 'config_json', name: '配置', type: 'JSONB' },
    ],
  },
];

export const SYSTEM_RELATIONS: EntityRelation[] = [
  { id: 'rel_1', from: 'sys_member', to: 'sys_unique', fromField: 'member_id', toField: 'member_id', type: '1:N', label: '', locked: true },
  { id: 'rel_2', from: 'sys_member', to: 'sys_account', fromField: 'member_id', toField: 'member_id', type: '1:1', label: '', locked: true },
  { id: 'rel_3', from: 'sys_tx_event', to: 'sys_member', fromField: 'member_id', toField: 'member_id', type: 'N:1', label: '', locked: true },
];