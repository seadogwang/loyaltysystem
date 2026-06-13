import React from 'react';
import { Card, Tabs, Tag, Button, Space, Typography, Input, Select, message, Empty } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined, LinkOutlined, CloseOutlined } from '@ant-design/icons';
import { useModelingStore, PREDEFINED_ENTITIES } from '../store';
import type { Edge, Node } from '@xyflow/react';
import type { EntityNode, EdgeData, MappingRule } from '../types';

const { Text } = Typography;

const ENTITY_COLORS: Record<string, { tag: string }> = {
  BUSINESS: { tag: 'blue' }, SYSTEM: { tag: 'orange' },
  API_REQUEST: { tag: 'green' }, API_RESPONSE: { tag: 'purple' },
};

interface Props {
  edges: Edge[];
  onUpdateEdge: (id: string, data: EdgeData) => void;
}

const LegendTab: React.FC = () => (
  <div style={{ padding: 12 }}>
    <Text strong style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>图例</Text>
    {[
      { color: '#3b82f6', style: 'solid', label: '业务关系 (FK)' },
      { color: '#22c55e', style: 'dashed', label: '入站映射 (API→业务)' },
      { color: '#f97316', style: 'dashed', label: '出站映射 (业务→API)' },
    ].map((l, i) => (
      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, fontSize: 12 }}>
        <div style={{ width: 30, height: 2, background: l.color, borderTop: l.style }} />
        <Text style={{ fontSize: 11 }}>{l.label}</Text>
      </div>
    ))}
    <Text strong style={{ fontSize: 12, display: 'block', marginTop: 12, marginBottom: 4 }}>实体类型</Text>
    {[
      { bg: '#eff6ff', border: '#3b82f6', label: '业务实体' },
      { bg: '#fff7ed', border: '#f97316', label: '系统实体' },
      { bg: '#f0fdf4', border: '#22c55e', label: 'API 实体' },
    ].map((e, i) => (
      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, fontSize: 12 }}>
        <div style={{ width: 20, height: 12, background: e.bg, border: `1px solid ${e.border}`, borderRadius: 2 }} />
        <Text style={{ fontSize: 11 }}>{e.label}</Text>
      </div>
    ))}
  </div>
);

const RelationshipTab: React.FC<Props> = ({ edges, onUpdateEdge }) => {
  const store = useModelingStore();
  const edge = edges.find(e => e.id === store.selectedEdgeId);
  if (!edge) return <Empty description="点击连线查看映射" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 40 }} />;

  const data = (edge.data || { relationType: 'FOREIGN_KEY', mappingRules: [] }) as EdgeData;
  const rules = data.mappingRules || [];
  const typeLabel = data.relationType === 'INBOUND_MAPPING' ? '入站映射' : data.relationType === 'OUTBOUND_MAPPING' ? '出站映射' : '业务关系';

  const addRule = () => {
    const newRules = [...rules, { key: String(Date.now()), source: '', target: '', type: 'PATH' as const }];
    onUpdateEdge(edge.id, { ...data, mappingRules: newRules });
  };
  const updRule = (idx: number, field: string, val: string) => {
    const ns = [...rules]; ns[idx] = { ...ns[idx], [field]: val };
    onUpdateEdge(edge.id, { ...data, mappingRules: ns });
  };
  const delRule = (idx: number) => {
    onUpdateEdge(edge.id, { ...data, mappingRules: rules.filter((_, i) => i !== idx) });
  };

  return (
    <div style={{ padding: 12 }}>
      <div style={{ background: '#f8fafc', borderRadius: 6, padding: 8, marginBottom: 8 }}>
        <Tag color="blue">{typeLabel}</Tag>
        <div style={{ marginTop: 6, fontSize: 12 }}>
          <Text>{edge.source}</Text>
          <Text type="secondary" style={{ margin: '0 6px' }}>→</Text>
          <Text>{edge.target}</Text>
        </div>
      </div>
      <Text strong style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>字段映射</Text>
      {rules.map((r, i) => (
        <div key={r.key} style={{ display: 'flex', gap: 4, marginBottom: 4, alignItems: 'center' }}>
          <Input size="small" style={{ width: 65, fontSize: 11 }} placeholder="源" value={r.source}
            onChange={e => updRule(i, 'source', e.target.value)} />
          <Text style={{ fontSize: 10 }}>→</Text>
          <Input size="small" style={{ width: 65, fontSize: 11 }} placeholder="目标" value={r.target}
            onChange={e => updRule(i, 'target', e.target.value)} />
          <Select size="small" style={{ width: 75, fontSize: 10 }} value={r.type} onChange={v => updRule(i, 'type', v)}
            options={[{ label: '直接', value: 'PATH' }, { label: '表达式', value: 'EXPRESSION' }, { label: '常量', value: 'CONSTANT' }]} />
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => delRule(i)} style={{ height: 22, minWidth: 22 }} />
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={addRule} block style={{ fontSize: 11, marginTop: 4 }}>添加映射</Button>
    </div>
  );
};

const EntityTab: React.FC = () => {
  const store = useModelingStore();
  const entity = PREDEFINED_ENTITIES.find(e => e.entityType === store.selectedNodeId);
  if (!entity) return <Empty description="点击实体节点查看详情" image={Empty.PRESENTED_IMAGE_SIMPLE} style={{ marginTop: 40 }} />;

  const colors = ENTITY_COLORS[entity.entityCategory] || { tag: 'blue' };

  return (
    <div style={{ padding: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
        <Text strong style={{ fontSize: 14, fontFamily: 'monospace' }}>{entity.entityType}</Text>
        <Tag color={colors.tag} style={{ fontSize: 10 }}>{entity.entityCategory}</Tag>
      </div>
      <Text type="secondary" style={{ fontSize: 12 }}>{entity.description}</Text>
      <div style={{ marginTop: 8, fontSize: 11, color: '#94a3b8' }}>{entity.fields.length} fields</div>

      <div style={{ marginTop: 10, maxHeight: 300, overflow: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
          <thead>
            <tr style={{ background: '#f8fafc', textAlign: 'left' }}>
              <th style={{ padding: '3px 6px', width: 20 }}></th>
              <th style={{ padding: '3px 6px' }}>字段</th>
              <th style={{ padding: '3px 6px', width: 70 }}>类型</th>
            </tr>
          </thead>
          <tbody>
            {entity.fields.map(f => (
              <tr key={f.name} style={{ borderTop: '1px solid #f1f5f9' }}>
                <td style={{ padding: '2px 6px' }}>
                  {f.primaryKey && <Tag color="gold" style={{ fontSize: 9, margin: 0, lineHeight: '14px' }}>PK</Tag>}
                </td>
                <td style={{ padding: '2px 6px', fontFamily: 'monospace', fontSize: 11 }}>{f.name}</td>
                <td style={{ padding: '2px 6px', color: '#94a3b8', fontSize: 10 }}>{f.type}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Space style={{ marginTop: 10 }}>
        <Button size="small" icon={<EditOutlined />} onClick={() => window.open(`/schema-editor?entity=${entity.entityType}`, '_blank')}>
          Schema
        </Button>
        <Button size="small" disabled>删除</Button>
      </Space>
    </div>
  );
};

const SidePanel: React.FC<Props> = ({ edges, onUpdateEdge }) => {
  const store = useModelingStore();
  const tabItems = [
    { key: 'entity', label: '实体', children: <EntityTab /> },
    { key: 'relationship', label: '映射', children: <RelationshipTab edges={edges} onUpdateEdge={onUpdateEdge} /> },
    { key: 'legend', label: '图例', children: <LegendTab /> },
  ];

  return (
    <Card size="small" style={{ width: 300, borderLeft: '1px solid #e2e8f0', borderRadius: 0, overflow: 'auto' }}
      extra={
        <Button size="small" type="text" icon={<CloseOutlined />} onClick={store.toggleSidebar} />
      }
    >
      <Tabs size="small" activeKey={store.sidebarTab} onChange={(k) => store.setSidebarTab(k as any)} items={tabItems} />
    </Card>
  );
};

export default SidePanel;