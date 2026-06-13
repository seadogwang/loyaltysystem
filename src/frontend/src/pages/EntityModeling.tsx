import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap, MarkerType, Panel, Handle, Position,
  type Node, type Edge, type Connection, useNodesState, useEdgesState, addEdge, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Button, Space, Typography, Tag, Select, Input, message, Modal } from 'antd';
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

const EntityNode: React.FC<NodeProps & { onEditToggle?: (id: string) => void }> = ({ id, data, selected, onEditToggle }) => {
  const entity = data as unknown as EntityDef & { editing?: boolean; onFieldChange?: (idx: number, f: string, v: string) => void; onAddField?: () => void; onDelField?: (idx: number) => void };
  const colors = NODE_COLORS[entity.entityCategory] || NODE_COLORS.BUSINESS;
  const fields = entity.fields || [];
  const visibleFields = entity.editing ? fields : (fields.length <= MAX_VISIBLE ? fields : fields.slice(0, MAX_VISIBLE));
  const hiddenCount = fields.length - MAX_VISIBLE;

  if (entity.editing) {
    return (
      <div style={{
        background: '#fff', borderRadius: 8, minWidth: 240, maxWidth: 380,
        boxShadow: '0 0 0 2px #ec4899, 0 6px 20px rgba(0,0,0,0.15)',
        border: '1px solid #ec4899',
      }}>
        <div style={{ height: 3, background: entity.color, borderRadius: '8px 8px 0 0' }} />
        <div style={{ padding: '8px 12px 6px', background: colors.bg, display: 'flex', alignItems: 'center', gap: 6 }}>
          <input value={entity.entityType} readOnly style={{ fontWeight: 600, fontSize: 13, fontFamily: 'monospace', border: '1px solid #e2e8f0', borderRadius: 4, padding: '1px 6px', width: 140 }} />
          <Tag color={colors.tag} style={{ fontSize: 10, margin: 0 }}>{CAT_LABELS[entity.entityCategory]}</Tag>
          <Button size="small" style={{ marginLeft: 'auto', fontSize: 11, height: 22 }} onClick={() => onEditToggle?.(id)}>完成</Button>
        </div>
        {visibleFields.map((f, i) => (
          <div key={i} style={{ display: 'flex', gap: 4, padding: '2px 8px', alignItems: 'center', borderTop: '1px solid #f1f5f9' }}>
            <input value={f.name} placeholder="field"
              onChange={e => entity.onFieldChange?.(i, 'name', e.target.value)}
              style={{ flex: 1, fontFamily: 'monospace', fontSize: 11, border: '1px solid #e2e8f0', borderRadius: 3, padding: '2px 4px' }} />
            <input value={f.type} placeholder="type"
              onChange={e => entity.onFieldChange?.(i, 'type', e.target.value)}
              style={{ width: 80, fontSize: 10, border: '1px solid #e2e8f0', borderRadius: 3, padding: '2px 4px', color: '#94a3b8' }} />
            <label style={{ fontSize: 10, display: 'flex', alignItems: 'center', gap: 2, cursor: 'pointer', whiteSpace: 'nowrap' }}>
              <input type="checkbox" checked={f.primaryKey || false} onChange={e => entity.onFieldChange?.(i, 'primaryKey', String(e.target.checked))} />
              PK
            </label>
            <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => entity.onDelField?.(i)} style={{ height: 20, minWidth: 20 }} />
          </div>
        ))}
        <div style={{ padding: '4px 8px', borderTop: '1px solid #f1f5f9' }}>
          <Button size="small" type="dashed" icon={<PlusOutlined />} block onClick={() => entity.onAddField?.()} style={{ fontSize: 11 }}>
            添加字段
          </Button>
        </div>
      </div>
    );
  }

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
            style={{ position: 'absolute', left: -5, top: 13, width: 8, height: 8, background: f.primaryKey ? '#f59e0b' : '#94a3b8', border: '2px solid #fff', opacity: selected ? 1 : 0.5 }} />
          {f.primaryKey && <Text style={{ fontSize: 10, color: '#f59e0b', marginRight: 4, fontWeight: 700 }}>PK</Text>}
          <Text style={{ flex: 1, fontFamily: 'monospace', fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{f.name}</Text>
          <Text style={{ fontSize: 10, color: '#94a3b8', background: '#f8fafc', borderRadius: 3, padding: '0 4px' }}>{f.type}</Text>
          <Handle type="source" position={Position.Right} id={`${f.name}-s`}
            style={{ position: 'absolute', right: -5, top: 13, width: 8, height: 8, background: '#3b82f6', border: '2px solid #fff', opacity: selected ? 1 : 0.5 }} />
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

// ==================== Relationship Mapping Modal ====================

const MappingModal: React.FC<{
  open: boolean;
  edge: Edge | null;
  onClose: () => void;
  onUpdate: (edgeId: string, data: EdgeMeta) => void;
}> = ({ open, edge, onClose, onUpdate }) => {
  if (!edge) return null;
  const data = (edge.data as EdgeMeta) || { relationType: 'FOREIGN_KEY', mappingRules: [] };
  const rules = data.mappingRules || [];
  const typeLabel = data.relationType === 'INBOUND_MAPPING' ? '入站映射' : data.relationType === 'OUTBOUND_MAPPING' ? '出站映射' : '业务关系';
  const dirColor = data.relationType === 'INBOUND_MAPPING' ? '#22c55e' : data.relationType === 'OUTBOUND_MAPPING' ? '#f97316' : '#3b82f6';

  const srcEntity = ENTITIES.find(e => e.entityType === edge.source);
  const tgtEntity = ENTITIES.find(e => e.entityType === edge.target);
  const srcFields = srcEntity?.fields || [];
  const tgtFields = tgtEntity?.fields || [];
  const srcFieldOpts = srcFields.map(f => ({ label: f.name, value: f.name }));
  const tgtFieldOpts = tgtFields.map(f => ({ label: f.name, value: f.name }));

  const updateRules = (newRules: MappingRule[]) => onUpdate(edge.id, { ...data, mappingRules: newRules });

  return (
    <Modal title={`${typeLabel}配置`} open={open} onCancel={onClose} width={640} footer={null}>
      {/* Source/Target info with entity cards */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 16, alignItems: 'center' }}>
        <div style={{ flex: 1, background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: 8, padding: 10 }}>
          <Text type="secondary" style={{ fontSize: 10, display: 'block' }}>源</Text>
          <Text strong style={{ fontSize: 13 }}>{edge.source}</Text>
          <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }}>
            {srcEntity?.entityCategory && CAT_LABELS[srcEntity.entityCategory]} · {srcFields.length} fields
          </div>
        </div>
        <div style={{ fontSize: 20, color: dirColor, fontWeight: 700 }}>→</div>
        <div style={{ flex: 1, background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 8, padding: 10 }}>
          <Text type="secondary" style={{ fontSize: 10, display: 'block' }}>目标</Text>
          <Text strong style={{ fontSize: 13 }}>{edge.target}</Text>
          <div style={{ fontSize: 10, color: '#94a3b8', marginTop: 2 }}>
            {tgtEntity?.entityCategory && CAT_LABELS[tgtEntity.entityCategory]} · {tgtFields.length} fields
          </div>
        </div>
      </div>

      <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>字段映射规则</Text>
      {rules.length === 0 && (
        <div style={{ padding: 16, textAlign: 'center', color: '#94a3b8', fontSize: 12, background: '#f8fafc', borderRadius: 6, marginBottom: 8 }}>
          暂无映射规则，点击下方按钮添加
        </div>
      )}
      {rules.map((r, i) => (
        <div key={r.key} style={{ display: 'flex', gap: 8, marginBottom: 6, alignItems: 'center' }}>
          <Select size="small" style={{ width: 140 }} showSearch value={r.source || undefined} placeholder="选择源字段"
            options={srcFieldOpts}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], source: v }; updateRules(ns); }} />
          <Text type="secondary" style={{ fontSize: 12 }}>→</Text>
          <Select size="small" style={{ width: 140 }} showSearch value={r.target || undefined} placeholder="选择目标字段"
            options={tgtFieldOpts}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], target: v }; updateRules(ns); }} />
          <Select size="small" style={{ width: 110 }} value={r.type}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], type: v as 'PATH' | 'EXPRESSION' | 'CONSTANT' }; updateRules(ns); }}
            options={[{ label: '直接映射', value: 'PATH' }, { label: '表达式', value: 'EXPRESSION' }, { label: '常量', value: 'CONSTANT' }]} />
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => updateRules(rules.filter((_, j) => j !== i))} />
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} onClick={() => updateRules([...rules, { key: String(Date.now()), source: '', target: '', type: 'PATH' }])} style={{ marginTop: 4 }}>
        添加映射规则
      </Button>
    </Modal>
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
  const [mappingModalOpen, setMappingModalOpen] = React.useState(false);
  const [mode, setMode] = React.useState<'inbound' | 'outbound'>('inbound');
  const [channel, setChannel] = React.useState('TMALL');
  const [editingId, setEditingId] = React.useState<string | null>(null);

  const toggleEdit = React.useCallback((nodeId: string) => {
    setEditingId(prev => prev === nodeId ? null : nodeId);
  }, []);

  const handleFieldChange = React.useCallback((nodeId: string, fieldIdx: number, field: string, value: string) => {
    setNodes(nds => nds.map(n => {
      if (n.id !== nodeId) return n;
      const data = n.data as unknown as EntityDef;
      const fields = [...data.fields];
      if (field === 'primaryKey') {
        fields[fieldIdx] = { ...fields[fieldIdx], primaryKey: value === 'true' };
      } else {
        fields[fieldIdx] = { ...fields[fieldIdx], [field]: value };
      }
      return { ...n, data: { ...data, fields } };
    }));
  }, [setNodes]);

  const handleAddField = React.useCallback((nodeId: string) => {
    setNodes(nds => nds.map(n => {
      if (n.id !== nodeId) return n;
      const data = n.data as unknown as EntityDef;
      return { ...n, data: { ...data, fields: [...data.fields, { name: `field_${data.fields.length + 1}`, type: 'string' }] } };
    }));
  }, [setNodes]);

  const handleDelField = React.useCallback((nodeId: string, fieldIdx: number) => {
    setNodes(nds => nds.map(n => {
      if (n.id !== nodeId) return n;
      const data = n.data as unknown as EntityDef;
      return { ...n, data: { ...data, fields: data.fields.filter((_, i) => i !== fieldIdx) } };
    }));
  }, [setNodes]);

  // Inject editing state and handlers into node data
  const nodesWithEdit = React.useMemo(() => nodes.map(n => ({
    ...n,
    data: {
      ...n.data,
      editing: n.id === editingId,
      onFieldChange: (idx: number, f: string, v: string) => handleFieldChange(n.id, idx, f, v),
      onAddField: () => handleAddField(n.id),
      onDelField: (idx: number) => handleDelField(n.id, idx),
    },
  })), [nodes, editingId, handleFieldChange, handleAddField, handleDelField]);

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
    setMappingModalOpen(true);
  }, [nodes, setEdges]);

  return (
    <>
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>统一实体建模与映射</Title>
        <Space>
          <Select size="small" value={mode} onChange={setMode} style={{ width: 80 }}
            options={[{ label: '入站', value: 'inbound' }, { label: '出站', value: 'outbound' }]} />
          <Select size="small" value={channel} onChange={setChannel} style={{ width: 90 }}
            options={['TMALL','JD','DOUYIN','WECHAT'].map(c => ({ label: c, value: c }))} />
          <Button size="small" onClick={() => setEditingId(selNode)}>编辑: {editingId ? 'ON' : 'OFF'}</Button>
          <Button size="small" icon={<SaveOutlined />} onClick={() => message.success('已保存')}>保存</Button>
          <Button type="primary" size="small" icon={<SendOutlined />}>发布</Button>
        </Space>
      </div>

      <div style={{ display: 'flex', height: 'calc(100vh - 150px)', border: '1px solid #e2e8f0', borderRadius: 8, overflow: 'hidden' }}>
        <div style={{ flex: 1 }}>
          <ReactFlow
            nodes={nodesWithEdit} edges={edges}
            onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, n) => { setSelNode(n.id); setSelEdge(null); }}
            onNodeDoubleClick={(_, n) => { setSelNode(n.id); toggleEdit(n.id); }}
            onEdgeClick={(_, e) => { setSelEdge(e.id); setSelNode(null); setMappingModalOpen(true); }}
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

        </div>
    </PageWrapper>

      <MappingModal
        open={mappingModalOpen}
        edge={edges.find(e => e.id === selEdge) || null}
        onClose={() => { setSelEdge(null); setMappingModalOpen(false); }}
        onUpdate={(id, data) => setEdges(eds => eds.map(e => e.id === id ? { ...e, data: data as any } : e))}
      />
    </>
  );
};

export default EntityModeling;