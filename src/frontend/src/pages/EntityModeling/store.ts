import { create } from 'zustand';
import type { EntityNode, EntityCategory, ModelingState } from './types';

const PREDEFINED_FIELDS: Record<string, { name: string; type: string; primaryKey?: boolean }[]> = {
  Member: [
    { name: 'memberId', type: 'string', primaryKey: true },
    { name: 'name', type: 'string' }, { name: 'email', type: 'string' },
    { name: 'phone', type: 'string' }, { name: 'tierCode', type: 'string' },
    { name: 'status', type: 'string' }, { name: 'createdAt', type: 'timestamp' },
  ],
  Order: [
    { name: 'orderId', type: 'string', primaryKey: true },
    { name: 'memberId', type: 'string' }, { name: 'totalAmount', type: 'number' },
    { name: 'status', type: 'string' }, { name: 'channel', type: 'string' },
    { name: 'tradeTime', type: 'timestamp' }, { name: 'payTime', type: 'timestamp' },
    { name: 'itemCount', type: 'number' }, { name: 'remark', type: 'string' },
  ],
  OrderItem: [
    { name: 'itemId', type: 'string', primaryKey: true },
    { name: 'orderId', type: 'string' }, { name: 'sku', type: 'string' },
    { name: 'quantity', type: 'number' }, { name: 'price', type: 'number' },
  ],
  PointTx: [
    { name: 'txId', type: 'string', primaryKey: true },
    { name: 'memberId', type: 'string' }, { name: 'amount', type: 'number' },
    { name: 'type', type: 'string' }, { name: 'createdAt', type: 'timestamp' },
  ],
  TransactionEvent: [
    { name: 'eventId', type: 'string', primaryKey: true },
    { name: 'eventType', type: 'string' }, { name: 'memberId', type: 'string' },
    { name: 'channel', type: 'string' }, { name: 'totalAmount', type: 'number' },
    { name: 'occurredAt', type: 'timestamp' },
  ],
  TmallOrderResp: [
    { name: 'tid', type: 'string', primaryKey: true },
    { name: 'payment', type: 'string' }, { name: 'payTime', type: 'string' },
    { name: 'status', type: 'string' }, { name: 'buyerNick', type: 'string' },
  ],
  TmallOrderReq: [
    { name: 'tid', type: 'string', primaryKey: true },
    { name: 'fields', type: 'string' }, { name: 'startTime', type: 'string' },
  ],
  PointsChangeReq: [
    { name: 'userId', type: 'string', primaryKey: true },
    { name: 'points', type: 'number' }, { name: 'eventType', type: 'string' },
    { name: 'eventTime', type: 'string' }, { name: 'reason', type: 'string' },
  ],
  PointsChangeResp: [
    { name: 'success', type: 'boolean', primaryKey: true },
    { name: 'message', type: 'string' }, { name: 'transactionId', type: 'string' },
  ],
};

export const PREDEFINED_ENTITIES: EntityNode[] = [
  { entityType: 'Member',    entityCategory: 'BUSINESS',    description: '会员实体',     color: '#3b82f6', fields: PREDEFINED_FIELDS['Member'] || [] },
  { entityType: 'Order',     entityCategory: 'BUSINESS',    description: '订单实体',     color: '#3b82f6', fields: PREDEFINED_FIELDS['Order'] || [] },
  { entityType: 'OrderItem', entityCategory: 'BUSINESS',    description: '订单明细',     color: '#3b82f6', fields: PREDEFINED_FIELDS['OrderItem'] || [] },
  { entityType: 'PointTx',   entityCategory: 'BUSINESS',    description: '积分流水',     color: '#3b82f6', fields: PREDEFINED_FIELDS['PointTx'] || [] },
  { entityType: 'TransactionEvent', entityCategory: 'SYSTEM', description: '交易事件',  color: '#f97316', fields: PREDEFINED_FIELDS['TransactionEvent'] || [] },
  { entityType: 'TmallOrderResp',   entityCategory: 'API_RESPONSE', description: '天猫订单响应', color: '#22c55e', fields: PREDEFINED_FIELDS['TmallOrderResp'] || [] },
  { entityType: 'TmallOrderReq',    entityCategory: 'API_REQUEST',  description: '天猫订单请求', color: '#22c55e', fields: PREDEFINED_FIELDS['TmallOrderReq'] || [] },
  { entityType: 'PointsChangeReq',  entityCategory: 'API_REQUEST',  description: '积分变动请求', color: '#22c55e', fields: PREDEFINED_FIELDS['PointsChangeReq'] || [] },
  { entityType: 'PointsChangeResp', entityCategory: 'API_RESPONSE', description: '积分变动响应', color: '#22c55e', fields: PREDEFINED_FIELDS['PointsChangeResp'] || [] },
];

export const useModelingStore = create<ModelingState>((set) => ({
  entities: PREDEFINED_ENTITIES,
  selectedNodeId: null,
  selectedEdgeId: null,
  mode: 'inbound',
  channel: 'TMALL',
  snapToGrid: true,
  showMiniMap: true,
  sidebarCollapsed: false,
  sidebarTab: 'entity',
  filterCategory: 'ALL',
  saving: false,

  setEntities: (entities) => set({ entities }),
  setSelectedNodeId: (id) => set((s) => ({ selectedNodeId: id, selectedEdgeId: id ? null : s.selectedEdgeId, sidebarTab: id ? 'entity' : s.sidebarTab })),
  setSelectedEdgeId: (id) => set((s) => ({ selectedEdgeId: id, selectedNodeId: id ? null : s.selectedNodeId, sidebarTab: id ? 'relationship' : s.sidebarTab })),
  setMode: (mode) => set({ mode }),
  setChannel: (channel) => set({ channel }),
  toggleSnap: () => set((s) => ({ snapToGrid: !s.snapToGrid })),
  toggleMiniMap: () => set((s) => ({ showMiniMap: !s.showMiniMap })),
  toggleSidebar: () => set((s) => ({ sidebarCollapsed: !s.sidebarCollapsed })),
  setSidebarTab: (tab) => set({ sidebarTab: tab }),
  setFilterCategory: (cat) => set({ filterCategory: cat }),
  setSaving: (saving) => set({ saving }),
}));