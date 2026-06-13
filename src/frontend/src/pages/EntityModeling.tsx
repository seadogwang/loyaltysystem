import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap, MarkerType, Panel, Handle, Position,
  type Node, type Edge, type Connection, useNodesState, useEdgesState, addEdge, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Button, Space, Typography, Tag, Select, Tabs, Card, Input, message, Empty } from 'antd';
import { SaveOutlined, SendOutlined, DeleteOutlined, PlusOutlined, EditOutlined, CloseOutlined } from '@ant-design/icons';
import PageWrapper from '../components/PageWrapper';

const { Title, Text } = Typography;

// ==================== Types ====================

type EntityCategory = 'BUSINESS' | 'API_REQUEST' | 'API_RESPONSE' | 'SYSTEM';
type RelationType = 'FOREIGN_KEY' | 'INBOUND_MAPPING' | 'OUTBOUND_MAPPING';

interface FieldDef { name: string; type: string; primaryKey?: boolean; }
interface EntityDef { entityType: string; entityCategory: EntityCategory; description: string; color: string; fields: FieldDef[]; }
interface MappingRule { key: string; source: string; target: string; type: 'PATH' | 'EXPRESSION' | 'CONSTANT'; expression?: string; }
interface EdgeMeta { relationType: RelationType; mappingRules: MappingRule[]; }

// ==================== Data ====================

const NODE_COLORS: Record<EntityCategory, { bg: string; border: string; tag: string }> = {
  BUSINESS:    { bg: '#eff6ff', border: '#3b82f6', tag: 'blue' },
  SYSTEM:      { bg: '#fff7ed', border: '#f97316', tag: 'orange' },
  API_REQUEST: { bg: '#f0fdf4', border: '#22c55e', tag: 'green' },
  API_RESPONSE:{ bg: '#faf5ff', border: '#a855f7', tag: 'purple' },
};

const CAT_LABELS: Record<string, string> = {
  BUSINESS: '业务', SYSTEM: '系统', API_REQUEST: 'API请求', API_RESPONSE: 'API响应',
};

const ENTITIES: EntityDef[] = [
  { entityType: 'Member',    entityCategory: 'BUSINESS', description: '会员实体',   color: '#3b82f6', fields: [
    { name: 'memberId', type: 'string', primaryKey: true }, { name: 'name', type: 'string' },
    { name: 'email', type: 'string' }, { name: 'phone', type: 'string' },
    { name: 'tierCode', type: 'string' }, { name: 'status', type: 'string' },
    { name: 'createdAt', type: 'timestamp' },
  ]},
  { entityType: 'Order',     entityCategory: 'BUSINESS', description: '订单实体',   color: '#3b82f6', fields: [
    { name: 'orderId', type: 'string', primaryKey: true }, { name: 'memberId', type: 'string' },
    { name: 'totalAmount', type: 'number' }, { name: 'status', type: 'string' },
    { name: 'channel', type: 'string' }, { name: 'tradeTime', type: 'timestamp' },
    { name: 'payTime', type: 'timestamp' }, { name: 'itemCount', type: 'number' },
    { name: 'remark', type: 'string' },
  ]},
  { entityType: 'OrderItem', entityCategory: 'BUSINESS', description: '订单明细',   color: '#3b82f6', fields: [
    { name: 'itemId', type: 'string', primaryKey: true }, { name: 'orderId', type: 'string' },
    { name: 'sku', type: 'string' }, { name: 'quantity', type: 'number' }, { name: 'price', type: 'number' },
  ]},
  { entityType: 'PointTx',   entityCategory: 'BUSINESS', description: '积分流水',   color: '#3b82f6', fields: [
    { name: 'txId', type: 'string', primaryKey: true }, { name: 'memberId', type: 'string' },
    { name: 'amount', type: 'number' }, { name: 'type', type: 'string' }, { name: 'createdAt', type: 'timestamp' },
  ]},
  { entityType: 'TransactionEvent', entityCategory: 'SYSTEM', description: '交易事件', color: '#f97316', fields: [
    { name: 'eventId', type: 'string', primaryKey: true }, { name: 'eventType', type: 'string' },
    { name: 'memberId', type: 'string' }, { name: 'channel', type: 'string' },
    { name: 'totalAmount', type: 'number' }, { name: 'occurredAt', type: 'timestamp' },
  ]},
  { entityType: 'TmallOrderResp',   entityCategory: 'API_RESPONSE', description: '天猫订单响应', color: '#22c55e', fields: [
    { name: 'tid', type: 'string', primaryKey: true }, { name: 'payment', type: 'string' },
    { name: 'payTime', type: 'string' }, { name: 'status', type: 'string' }, { name: 'buyerNick', type: 'string' },
  ]},
  { entityType: 'TmallOrderReq',    entityCategory: 'API_REQUEST', description: '天猫订单请求', color: '#22c55e', fields: [
    { name: 'tid', type: 'string', primaryKey: true }, { name: 'fields', type: 'string' }, { name: 'startTime', type: 'string' },
  ]},
  { entityType: 'PointsChangeReq',  entityCategory: 'API_REQUEST', description: '积分变动请求', color: '#22c55e', fields: [
    { name: 'userId', type: 'string', primaryKey: true }, { name: 'points', type: 'number' },
    { name: 'eventType', type: 'string' }, { name: 'eventTime', type: 'string' }, { name: 'reason', type: 'string' },
  ]},
  { entityType: 'PointsChangeResp', entityCategory: 'API_RESPONSE', description: '积分变动响应', color: '#22c55e', fields: [
    { name: 'success', type: 'boolean', primaryKey: true }, { name: 'message', type: 'string' }, { name: 'transactionId', type: 'string' },
  ]},
];

// ==================== Custom Node (ChartDB-style) ====================

const MAX_VISIBLE = 8;

const EntityNode: React.FC<NodeProps> = ({ data, selected }) => {
  const entity = data as unknown as EntityDef;
  const colors = NODE_COLORS[entity.entityCategory] || NODE_COLORS.BUSINESS;
  const fields = entity.fields || [];
  const visibleFields = fields.length <= MAX_VISIBLE ? fields : fields.slice(0, MAX_VISIBLE);
  const hiddenCount = fields.length - MAX_VISIBLE;

  return (
    <div style={{
      background: '#fff', borderRadius: 8, minWidth: 210, maxWidth: 320,
      boxShadow: selected ? '0 0 0 2px #ec4899, 0 6px 20px rgba(0,0,0,0.12)' : '0 2px 10px rgba(0,0,0,0.08)',
      border: `1px solid ${selected ? '#ec4899' : '#e2e8f0'}`,
      transition: 'box-shadow 0.15s, border 0.15s',
      cursor: 'pointer',
    }}>
      <div style={{ height: 3, background: entity.color, borderRadius: '8px 8px 0 0' }} />
      <div style={{ padding: '8px 12px 6px', background: colors.bg, display: 'flex', alignItems: 'center', gap: 6 }}>
        <Text strong style={{ fontSize: 13, fontFamily: 'monospace', color: '#0f172a' }}>{entity.entityType}</Text>
        <Tag color={colors.tag} style={{ fontSize: 10, margin: 0, lineHeight: '16px', padding: '0 4px' }}>{CAT_LABELS[entity.entityCategory]}</Tag>
        <Text style={{ fontSize: 10, color: '#94a3b8', marginLeft: 'auto' }}>{fields.length}</Text>
      </div>
      {visibleFields.map((f, i) => (
        <div key={f.name} style={{ position: 'relative', display: 'flex', alignItems: 'center', padding: '3px 12px', height: 26, borderTop: i > 0 ? '1px solid #f1f5f9' : 'none', fontSize: 12 }}>
          <Handle type="target" position={Position.Left} id={`${f.name}-t`}
            style={{ position: 'absolute', left: -5, top: 13, width: 8, height: 8, background: f.primaryKey ? '#f59e0b' : '#94a3b8', border: '2px solid #fff', opacity: selected ? 1 : 0 }} />
          {f.primaryKey && <Text style={{ fontSize: 10, color: '#f59e0b', marginRight: 4, fontWeight: 700 }}>PK</Text>}
          <Text style={{ flex: 1, fontFamily: 'monospace', fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.name}</Text>
          <Text style={{ fontSize: 10, color: '#94a3b8', background: '#f8fafc', borderRadius: 3, padding: '0 4px' }}>{f.type}</Text>
          <Handle type="source" position={Position.Right} id={`${f.name}-s`}
            style={{ position: 'absolute', right: -5, top: 13, width: 8, height: 8, background: '#3b82f6', border: '2px solid #fff', opacity: selected ? 1 : 0 }} />
        </div>
      ))}
      {fields.length > MAX_VISIBLE && (
        <div style={{ padding: '4px 12px', textAlign: 'center', fontSize: 11, color: '#94a3b8', borderTop: '1px solid #f1f5f9' }}>
          + {hiddenCount} more fields
        </div>
      )}
    </div>
  );
};

const nodeTypes = { entity: EntityNode };

// ==================== Sidebar ====================

const SidePanel: React.FC<{
  selectedNodeId: string | null; selectedEdgeId: string | null;
  edges: Edge[]; onUpdateEdge: (id: string, data: EdgeMeta) => void;
  onClose: () => void;
}> = ({ selectedNodeId, selectedEdgeId, edges, onUpdateEdge, onClose }) => {

  // Legend tab
  if (selectedEdgeId) {
    const e = edges.find(x => x.id === selectedEdgeId);
    if (!e) return null;
    const data = (e.data || { relationType: 'FOREIGN_KEY', mappingRules: [] }) as EdgeMeta;
    const rules = data.mappingRules || [];
    const typeLabel = data.relationType === 'INBOUND_MAPPING' ? '入站映射' : data.relationType === 'OUTBOUND_MAPPING' ? '出站映射' : '业务关系';

    return (
      <Card size="small" style={{ width: 300, borderLeft: '1px solid #e2e8f0', borderRadius: 0, overflow: 'auto' }}
        title="连线映射" extra={<Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} />}>
        <div style={{ background: '#f8fafc', borderRadius: 6, padding: 8, marginBottom: 8 }}>
          <Tag color="blue">{typeLabel}</Tag>
          <div style={{ marginTop: 4, fontSize: 12 }}>{e.source} → {e.target}</div>
        </div>
        <Text strong style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>字段映射</Text>
        {rules.map((r, i) => (
          <div key={r.key} style={{ display: 'flex', gap: 4, marginBottom: 4, alignItems: 'center' }}>
            <Input size="small" style={{ width: 65, fontSize: 11 }} value={r.source} onChange={ev => { const ns = [...rules]; ns[i] = { ...ns[i], source: ev.target.value }; onUpdateEdge(e.id, { ...data, mappingRules: ns }); }} />
            <Text style={{ fontSize: 10 }}>→</Text>
            <Input size="small" style={{ width: 65, fontSize: 11 }} value={r.target} onChange={ev => { const ns = [...rules]; ns[i] = { ...ns[i], target: ev.target.value }; onUpdateEdge(e.id, { ...data, mappingRules: ns }); }} />
            <Select size="small" style={{ width: 75 }} value={r.type} options={[{ label: '直接', value: 'PATH' }, { label: '表达式', value: 'EXPRESSION' }, { label: '常量', value: 'CONSTANT' }]} onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], type: v }; onUpdateEdge(e.id, { ...data, mappingRules: ns }); }} />
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => onUpdateEdge(e.id, { ...data, mappingRules: rules.filter((_, j) => j !== i) })} />
          </div>
        ))}
        <Button size="small" icon={<PlusOutlined />} block onClick={() => onUpdateEdge(e.id, { ...data, mappingRules: [...rules, { key: String(Date.now()), source: '', target: '', type: 'PATH' }] })} style={{ fontSize: 11 }}>添加映射</Button>
      </Card>
    );
  }

  if (selectedNodeId) {
    const entity = ENTITIES.find(x => x.entityType === selectedNodeId);
    if (!entity) return null;
    const c = NODE_COLORS[entity.entityCategory] || NODE_COLORS.BUSINESS;
    return (
      <Card size="small" style={{ width: 300, borderLeft: '1px solid #e2e8f0', borderRadius: 0, overflow: 'auto' }}
        title="实体属性" extra={<Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} />}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <Text strong style={{ fontSize: 14, fontFamily: 'monospace' }}>{entity.entityType}</Text>
          <Tag color={c.tag} style={{ fontSize: 10 }}>{entity.entityCategory}</Tag>
        </div>
        <Text type="secondary" style={{ fontSize: 12 }}>{entity.description}</Text>
        <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>{entity.fields.length} fields</div>
        <div style={{ marginTop: 10, maxHeight: 300, overflow: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 11 }}>
            <thead>
              <tr style={{ background: '#f8fafc', textAlign: 'left' }}>
                <th style={{ padding: '3px 6px', width: 22 }}></th>
                <th style={{ padding: '3px 6px' }}>字段</th>
                <th style={{ padding: '3px 6px', width: 70 }}>类型</th>
              </tr>
            </thead>
            <tbody>
              {entity.fields.map(f => (
                <tr key={f.name} style={{ borderTop: '1px solid #f1f5f9' }}>
                  <td style={{ padding: '2px 6px' }}>{f.primaryKey && <Tag color="gold" style={{ fontSize: 9, margin: 0 }}>PK</Tag>}</td>
                  <td style={{ padding: '2px 6px', fontFamily: 'monospace', fontSize: 11 }}>{f.name}</td>
                  <td style={{ padding: '2px 6px', color: '#94a3b8', fontSize: 10 }}>{f.type}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Space style={{ marginTop: 10 }}>
          <Button size="small" icon={<EditOutlined />} onClick={() => window.open('/schema-editor?entity=' + entity.entityType, '_blank')}>Schema</Button>
          <Button size="small" disabled>删除</Button>
        </Space>
      </Card>
    );
  }

  // Default: legend
  return (
    <Card size="small" style={{ width: 300, borderLeft: '1px solid #e2e8f0', borderRadius: 0, overflow: 'auto' }}
      title="图例" extra={<Button size="small" type="text" icon={<CloseOutlined />} onClick={onClose} />}>
      <Text strong style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>连线类型</Text>
      {[
        { color: '#3b82f6', dash: undefined, label: '业务关系 (FK)' },
        { color: '#22c55e', dash: '5,5', label: '入站映射 (API→业务)' },
        { color: '#f97316', dash: '5,5', label: '出站映射 (业务→API)' },
      ].map((l, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, fontSize: 12 }}>
          <div style={{ width: 30, height: 2, background: l.color, borderTop: l.dash ? `2px dashed ${l.color}` : undefined }} />
          <Text style={{ fontSize: 11 }}>{l.label}</Text>
        </div>
      ))}
      <Text strong style={{ fontSize: 12, display: 'block', marginTop: 10, marginBottom: 4 }}>实体类型</Text>
      {([
        { bg: '#eff6ff', border: '#3b82f6', label: '业务实体' },
        { bg: '#fff7ed', border: '#f97316', label: '系统实体' },
        { bg: '#f0fdf4', border: '#22c55e', label: 'API 实体' },
      ]).map((l, i) => (
        <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3, fontSize: 12 }}>
          <div style={{ width: 20, height: 12, background: l.bg, border: `1px solid ${l.border}`, borderRadius: 2 }} />
          <Text style={{ fontSize: 11 }}>{l.label}</Text>
        </div>
      ))}
    </Card>
  );
};

// ==================== Main ====================

const EntityModeling: React.FC = () => {
  const initNodes: Node[] = ENTITIES.map((e, i) => ({
    id: e.entityType,
    type: 'entity',
    position: { x: 60 + (i % 3) * 330, y: 60 + Math.floor(i / 3) * 280 },
    data: { ...e },
  }));

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(initNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selNode, setSelNode] = React.useState<string | null>(null);
  const [selEdge, setSelEdge] = React.useState<string | null>(null);
  const [mode, setMode] = React.useState<'inbound' | 'outbound'>('inbound');
  const [channel, setChannel] = React.useState('TMALL');
  const [sidebar, setSidebar] = React.useState(true);

  const onConnect = useCallback((conn: Connection) => {
    if (!conn.source || !conn.target) return;
    const src = nodes.find(n => n.id === conn.source)!;
    const tgt = nodes.find(n => n.id === conn.target)!;
    const sc = (src.data as unknown as EntityDef).entityCategory;
    const tc = (tgt.data as unknown as EntityDef).entityCategory;

    let rt: RelationType = 'FOREIGN_KEY';
    if (sc === 'API_RESPONSE' && (tc === 'BUSINESS' || tc === 'SYSTEM')) rt = 'INBOUND_MAPPING';
    else if ((sc === 'BUSINESS' || sc === 'SYSTEM') && tc === 'API_REQUEST') rt = 'OUTBOUND_MAPPING';

    const color = rt === 'INBOUND_MAPPING' ? '#22c55e' : rt === 'OUTBOUND_MAPPING' ? '#f97316' : '#3b82f6';
    const newEdge: Edge = {
      id: `${conn.source}->${conn.target}`,
      source: conn.source, target: conn.target,
      sourceHandle: conn.sourceHandle || null, targetHandle: conn.targetHandle || null,
      type: 'smoothstep',
      style: { stroke: color, strokeWidth: 2, strokeDasharray: rt !== 'FOREIGN_KEY' ? '5,5' : undefined },
      markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
      data: { relationType: rt, mappingRules: [] } as EdgeMeta,
    };
    setEdges(eds => addEdge(newEdge, eds) as Edge[]);
    setSelEdge(newEdge.id);
    setSelNode(null);
    setSidebar(true);
  }, [nodes, setEdges]);

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>统一实体建模与映射</Title>
        <Space>
          <Select size="small" value={mode} onChange={setMode} style={{ width: 80 }}
            options={[{ label: '入站', value: 'inbound' }, { label: '出站', value: 'outbound' }]} />
          <Select size="small" value={channel} onChange={setChannel} style={{ width: 90 }}
            options={['TMALL','JD','DOUYIN','WECHAT'].map(c => ({ label: c, value: c }))} />
          <Button size="small" onClick={() => setSidebar(!sidebar)}>面板: {sidebar ? 'ON' : 'OFF'}</Button>
          <Button size="small" icon={<SaveOutlined />} onClick={() => message.success('已保存')}>保存</Button>
          <Button type="primary" size="small" icon={<SendOutlined />}>发布</Button>
        </Space>
      </div>

      <div style={{ display: 'flex', height: 'calc(100vh - 150px)', border: '1px solid #e2e8f0', borderRadius: 8, overflow: 'hidden' }}>
        <div style={{ flex: 1 }}>
          <ReactFlow
            nodes={nodes} edges={edges}
            onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, n) => { setSelNode(n.id); setSelEdge(null); setSidebar(true); }}
            onEdgeClick={(_, e) => { setSelEdge(e.id); setSelNode(null); setSidebar(true); }}
            onPaneClick={() => { setSelNode(null); setSelEdge(null); }}
            nodeTypes={nodeTypes as any} fitView
            deleteKeyCode={['Backspace', 'Delete']}
            snapToGrid snapGrid={[20, 20]}
          >
            <Background color="#cbd5e1" gap={20} />
            <Controls />
            <MiniMap style={{ width: 160, height: 100 }} nodeColor={(n) => (n.data as any)?.color || '#ddd'} />
            <Panel position="bottom-center">
              <div style={{ background: '#fff', borderRadius: 6, padding: '4px 12px', border: '1px solid #e2e8f0', fontSize: 11, color: '#94a3b8' }}>
                {nodes.length} entities · {edges.length} relationships
              </div>
            </Panel>
          </ReactFlow>
        </div>

        {sidebar && (
          <SidePanel
            selectedNodeId={selNode} selectedEdgeId={selEdge} edges={edges}
            onUpdateEdge={(id, data) => setEdges(eds => eds.map(e => e.id === id ? { ...e, data } : e))}
            onClose={() => { setSelNode(null); setSelEdge(null); setSidebar(false); }}
          />
        )}
      </div>
    </PageWrapper>
  );
};

export default EntityModeling;