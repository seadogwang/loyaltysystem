import React, { useState, useMemo } from 'react';
import { Input, Typography, Badge, Tooltip } from 'antd';
import { SearchOutlined, LockOutlined, TableOutlined, ApiOutlined, EyeOutlined } from '@ant-design/icons';
import { COLORS, BADGES } from './constants';
import type { EntityFlowNode, EntityKind } from './types';

const { Text } = Typography;

interface EntityPanelProps {
  nodes: EntityFlowNode[];
  onAddEntity: (kind: EntityKind) => void;
  onEditEntity: (node: EntityFlowNode) => void;
  onFocusNode: (nodeId: string) => void;
}

const CATEGORIES: { key: EntityKind; label: string; icon: React.ReactNode }[] = [
  { key: 'system', label: '系统实体', icon: <LockOutlined style={{ fontSize: 10 }} /> },
  { key: 'business', label: '业务实体', icon: <TableOutlined style={{ fontSize: 10 }} /> },
  { key: 'api', label: 'API实体', icon: <ApiOutlined style={{ fontSize: 10 }} /> },
];

const EntityPanel: React.FC<EntityPanelProps> = ({ nodes, onAddEntity, onEditEntity, onFocusNode }) => {
  const [search, setSearch] = useState('');
  const [expanded, setExpanded] = useState<Set<EntityKind>>(new Set(['system', 'business', 'api']));

  const filtered = useMemo(() => {
    const q = search.toLowerCase();
    return nodes.filter(n => {
      const name = (n.data?.displayName || n.data?.name || '').toLowerCase();
      return !q || name.includes(q);
    });
  }, [nodes, search]);

  const grouped = useMemo(() => {
    const g: Record<EntityKind, EntityFlowNode[]> = { system: [], business: [], api: [] };
    filtered.forEach(n => {
      const kind = (n.data?.kind || 'business') as EntityKind;
      g[kind]?.push(n);
    });
    return g;
  }, [filtered]);

  const toggle = (k: EntityKind) => {
    setExpanded(prev => {
      const next = new Set(prev);
      next.has(k) ? next.delete(k) : next.add(k);
      return next;
    });
  };

  const focusNode = (nodeId: string) => onFocusNode(nodeId);

  return (
    <div style={{ width: 220, background: '#fff', borderLeft: '1px solid #f0f0f0', display: 'flex', flexDirection: 'column', height: '100%', flexShrink: 0 }}>
      {/* 搜索 */}
      <div style={{ padding: '8px 10px', borderBottom: '1px solid #f0f0f0' }}>
        <Input size="small" prefix={<SearchOutlined />} placeholder="搜索实体..."
          value={search} onChange={e => setSearch(e.target.value)}
          style={{ fontSize: 12 }} allowClear />
      </div>

      {/* 实体列表（可滚动） */}
      <div style={{ flex: 1, overflow: 'auto', padding: '4px 0' }}>
        {CATEGORIES.map(cat => {
          const items = grouped[cat.key];
          const isOpen = expanded.has(cat.key);
          return (
            <div key={cat.key}>
              {/* 分类标题 */}
              <div onClick={() => toggle(cat.key)} style={{
                display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px',
                cursor: 'pointer', fontSize: 11, color: '#666', fontWeight: 600,
                borderBottom: isOpen ? '1px solid #f5f5f5' : 'none',
              }}>
                <span style={{ transform: isOpen ? 'rotate(90deg)' : 'none', transition: 'transform 0.2s', fontSize: 8 }}>▶</span>
                <span style={{ color: COLORS[cat.key] }}>{cat.icon}</span>
                <span>{cat.label}</span>
                <Badge count={items.length} size="small" style={{ marginLeft: 'auto', fontSize: 10 }}
                  styles={{ indicator: { backgroundColor: COLORS[cat.key], fontSize: 10, height: 16, minWidth: 16 } }} />
              </div>
              {/* 实体项 */}
              {isOpen && items.map(node => {
                const nodeData = node.data;
                const isSystem = nodeData?.kind === 'system';
                return (
                  <div key={node.id} style={{
                    display: 'flex', alignItems: 'center', gap: 6, padding: '4px 10px 4px 24px',
                    cursor: 'pointer', fontSize: 11, color: '#333',
                    borderLeft: `3px solid ${COLORS[nodeData?.kind || 'business']}`,
                    marginLeft: 8, borderRadius: '0 4px 4px 0',
                    background: 'transparent',
                  }}
                    onMouseEnter={e => e.currentTarget.style.background = '#fafafa'}
                    onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                    onClick={() => onEditEntity(node)}
                    onDoubleClick={() => focusNode(node.id)}
                  >
                    <span style={{ fontSize: 11 }}>{BADGES[nodeData?.kind || 'business']}</span>
                    <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {nodeData?.displayName || nodeData?.name}
                    </span>
                    <Tooltip title="定位到画布">
                      <EyeOutlined style={{ fontSize: 10, color: '#bbb' }}
                        onClick={e => { e.stopPropagation(); focusNode(node.id); }} />
                    </Tooltip>
                  </div>
                );
              })}
              {items.length === 0 && isOpen && (
                <div style={{ padding: '4px 10px 4px 24px', fontSize: 10, color: '#ccc' }}>暂无</div>
              )}
            </div>
          );
        })}
      </div>

      {/* 底部操作 */}
      <div style={{ padding: 8, borderTop: '1px solid #f0f0f0', display: 'flex', gap: 4 }}>
        <button onClick={() => onAddEntity('business')} style={{
          flex: 1, height: 28, border: `1px solid ${COLORS.business}`, borderRadius: 6,
          background: '#fff', color: COLORS.business, fontSize: 11, cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 3,
          fontFamily: 'inherit',
        }}
          onMouseEnter={e => e.currentTarget.style.background = '#e6f7ff'}
          onMouseLeave={e => e.currentTarget.style.background = '#fff'}
        >📦 业务</button>
        <button onClick={() => onAddEntity('api')} style={{
          flex: 1, height: 28, border: `1px solid ${COLORS.api}`, borderRadius: 6,
          background: '#fff', color: COLORS.api, fontSize: 11, cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 3,
          fontFamily: 'inherit',
        }}
          onMouseEnter={e => e.currentTarget.style.background = '#f6ffed'}
          onMouseLeave={e => e.currentTarget.style.background = '#fff'}
        >🔌 API</button>
      </div>
    </div>
  );
};

export default EntityPanel;