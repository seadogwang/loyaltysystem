import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow, ReactFlowProvider, Background, MiniMap, MarkerType, Panel, Handle, Position,
  type Node, type Edge, type Connection, useNodesState, useEdgesState, addEdge, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Button, Space, Typography, Tag, Select, Input, message, Modal } from 'antd';
import { PlusOutlined, DeleteOutlined, ExpandOutlined, MinusOutlined, SaveOutlined, EditOutlined } from '@ant-design/icons';
import { useReactFlow } from '@xyflow/react';

const { Text } = Typography;

// Types & Data (unchanged)
type EntityCategory = 'BUSINESS' | 'API_REQUEST' | 'API_RESPONSE' | 'SYSTEM';
type RelationType = 'FOREIGN_KEY' | 'INBOUND_MAPPING' | 'OUTBOUND_MAPPING';
interface FieldDef { name: string; type: string; primaryKey?: boolean; }
interface EntityDef { entityType: string; entityCategory: EntityCategory; description: string; color: string; fields: FieldDef[]; }
interface MappingRule { key: string; source: string; target: string; type: 'PATH' | 'EXPRESSION' | 'CONSTANT'; }
interface EdgeMeta { relationType: RelationType; mappingRules: MappingRule[]; }

const CAT_LABELS: Record<string, string> = { BUSINESS: '业务', SYSTEM: '系统', API_REQUEST: 'API请求', API_RESPONSE: 'API响应' };

const ENTITIES: EntityDef[] = [
  { entityType: 'Member', entityCategory: 'BUSINESS', description: '会员实体', color: '#3b82f6', fields: [
    { name: 'memberId', type: 'string', primaryKey: true }, { name: 'name', type: 'string' }, { name: 'email', type: 'string' }, { name: 'phone', type: 'string' }, { name: 'tierCode', type: 'string' }, { name: 'status', type: 'string' }, { name: 'createdAt', type: 'timestamp' },
  ]},
  { entityType: 'Order', entityCategory: 'BUSINESS', description: '订单实体', color: '#3b82f6', fields: [
    { name: 'orderId', type: 'string', primaryKey: true }, { name: 'memberId', type: 'string' }, { name: 'totalAmount', type: 'number' }, { name: 'status', type: 'string' }, { name: 'channel', type: 'string' }, { name: 'tradeTime', type: 'timestamp' }, { name: 'payTime', type: 'timestamp' }, { name: 'itemCount', type: 'number' }, { name: 'remark', type: 'string' },
  ]},
  { entityType: 'OrderItem', entityCategory: 'BUSINESS', description: '订单明细', color: '#3b82f6', fields: [
    { name: 'itemId', type: 'string', primaryKey: true }, { name: 'orderId', type: 'string' }, { name: 'sku', type: 'string' }, { name: 'quantity', type: 'number' }, { name: 'price', type: 'number' },
  ]},
  { entityType: 'PointTx', entityCategory: 'BUSINESS', description: '积分流水', color: '#3b82f6', fields: [
    { name: 'txId', type: 'string', primaryKey: true }, { name: 'memberId', type: 'string' }, { name: 'amount', type: 'number' }, { name: 'type', type: 'string' }, { name: 'createdAt', type: 'timestamp' },
  ]},
  { entityType: 'TransactionEvent', entityCategory: 'SYSTEM', description: '交易事件', color: '#f97316', fields: [
    { name: 'eventId', type: 'string', primaryKey: true }, { name: 'eventType', type: 'string' }, { name: 'memberId', type: 'string' }, { name: 'channel', type: 'string' }, { name: 'totalAmount', type: 'number' }, { name: 'occurredAt', type: 'timestamp' },
  ]},
  { entityType: 'TmallOrderResp', entityCategory: 'API_RESPONSE', description: '天猫订单响应', color: '#22c55e', fields: [
    { name: 'tid', type: 'string', primaryKey: true }, { name: 'payment', type: 'string' }, { name: 'payTime', type: 'string' }, { name: 'status', type: 'string' }, { name: 'buyerNick', type: 'string' },
  ]},
  { entityType: 'TmallOrderReq', entityCategory: 'API_REQUEST', description: '天猫订单请求', color: '#22c55e', fields: [
    { name: 'tid', type: 'string', primaryKey: true }, { name: 'fields', type: 'string' }, { name: 'startTime', type: 'string' },
  ]},
  { entityType: 'PointsChangeReq', entityCategory: 'API_REQUEST', description: '积分变动请求', color: '#22c55e', fields: [
    { name: 'userId', type: 'string', primaryKey: true }, { name: 'points', type: 'number' }, { name: 'eventType', type: 'string' }, { name: 'eventTime', type: 'string' }, { name: 'reason', type: 'string' },
  ]},
  { entityType: 'PointsChangeResp', entityCategory: 'API_RESPONSE', description: '积分变动响应', color: '#22c55e', fields: [
    { name: 'success', type: 'boolean', primaryKey: true }, { name: 'message', type: 'string' }, { name: 'transactionId', type: 'string' },
  ]},
];

// ===== ChartDB-style Entity Node =====

const CAT_BG: Record<string, string> = {
  BUSINESS: 'bg-blue-50 border-blue-500', SYSTEM: 'bg-orange-50 border-orange-500',
  API_REQUEST: 'bg-green-50 border-green-500', API_RESPONSE: 'bg-purple-50 border-purple-500',
};

const EntityNode: React.FC<NodeProps> = ({ id, data, selected }) => {
  const entity = data as unknown as EntityDef;
  const fields = (entity.fields || []);
  const visible = fields.length <= 8 ? fields : fields.slice(0, 8);
  const more = fields.length - 8;

  return (
    <div className={`bg-white rounded-lg min-w-[200px] max-w-[340px] cursor-pointer transition-shadow duration-150 ${selected ? 'ring-2 ring-pink-500 shadow-xl' : 'shadow-sm border border-slate-200'}`}>
      {/* Color stripe */}
      <div className="h-[3px] rounded-t-lg" style={{ backgroundColor: entity.color }} />
      {/* Header */}
      <div className={`px-3 py-1.5 flex items-center gap-2 border-b border-slate-100 ${CAT_BG[entity.entityCategory]?.split(' ')[0] || 'bg-slate-50'}`}>
        <span className="text-[13px] font-semibold text-slate-900 truncate">{entity.entityType}</span>
        <Tag className="!text-[9px] !leading-[14px] !px-[3px] !ml-auto" color={entity.entityCategory === 'BUSINESS' ? 'blue' : entity.entityCategory === 'SYSTEM' ? 'orange' : 'green'}>{CAT_LABELS[entity.entityCategory]}</Tag>
      </div>
      {/* Fields */}
      {visible.map((f, i) => (
        <div key={f.name} className={`relative flex items-center px-3 h-6 text-xs ${i > 0 ? 'border-t border-slate-50' : ''}`}>
          <Handle type="target" position={Position.Left} id={`${f.name}-t`} className="!absolute !-left-1 !top-3 !w-[6px] !h-[6px] !bg-amber-500 !border-2 !border-white" style={{ opacity: selected || f.primaryKey ? 1 : 0.4 }} />
          {f.primaryKey && <span className="text-[9px] text-amber-500 mr-1 font-bold">PK</span>}
          <span className="flex-1 text-[11px] text-slate-700 truncate">{f.name}</span>
          <span className="text-[10px] text-slate-400 bg-slate-50 rounded px-1">{f.type}</span>
          <Handle type="source" position={Position.Right} id={`${f.name}-s`} className="!absolute !-right-1 !top-3 !w-[6px] !h-[6px] !bg-blue-500 !border-2 !border-white" style={{ opacity: selected ? 1 : 0.4 }} />
        </div>
      ))}
      {more > 0 && <div className="px-3 py-1 text-[11px] text-slate-400 border-t border-slate-50 text-center">+{more} more</div>}
    </div>
  );
};

const nodeTypes = { entity: EntityNode };

// ===== Mapping Modal (ChartDB enhanced) =====

const MappingModal: React.FC<{ open: boolean; edge: Edge | null; onClose: () => void; onUpdate: (edgeId: string, data: EdgeMeta) => void; }> = ({ open, edge, onClose, onUpdate }) => {
  if (!edge) return null;
  const data = (edge.data as EdgeMeta) || { relationType: 'FOREIGN_KEY', mappingRules: [] };
  const rules = data.mappingRules || [];
  const typeLabel = data.relationType === 'INBOUND_MAPPING' ? '入站映射' : data.relationType === 'OUTBOUND_MAPPING' ? '出站映射' : '业务关系';
  const dirColor = data.relationType === 'INBOUND_MAPPING' ? '#22c55e' : data.relationType === 'OUTBOUND_MAPPING' ? '#f97316' : '#3b82f6';
  const srcEntity = ENTITIES.find(e => e.entityType === edge.source);
  const tgtEntity = ENTITIES.find(e => e.entityType === edge.target);
  const srcFields = srcEntity?.fields.map(f => ({ label: f.name, value: f.name })) || [];
  const tgtFields = tgtEntity?.fields.map(f => ({ label: f.name, value: f.name })) || [];

  return (
    <Modal title={`${typeLabel}配置`} open={open} onCancel={onClose} width={640} footer={null}>
      <div className="flex gap-3 mb-4 items-center">
        <div className="flex-1 bg-blue-50 border border-blue-200 rounded-lg p-3">
          <div className="text-[10px] text-slate-500">源</div>
          <div className="text-[13px] font-semibold">{edge.source}</div>
          <div className="text-[10px] text-slate-400 mt-0.5">{srcEntity?.entityCategory && CAT_LABELS[srcEntity.entityCategory]} · {srcFields.length} fields</div>
        </div>
        <div className="text-xl font-bold" style={{ color: dirColor }}>→</div>
        <div className="flex-1 bg-green-50 border border-green-200 rounded-lg p-3">
          <div className="text-[10px] text-slate-500">目标</div>
          <div className="text-[13px] font-semibold">{edge.target}</div>
          <div className="text-[10px] text-slate-400 mt-0.5">{tgtEntity?.entityCategory && CAT_LABELS[tgtEntity.entityCategory]} · {tgtFields.length} fields</div>
        </div>
      </div>
      <div className="text-[13px] font-semibold mb-2">字段映射规则</div>
      {rules.length === 0 && <div className="p-4 text-center text-slate-400 text-xs bg-slate-50 rounded-lg mb-2">暂无映射规则，点击下方按钮添加</div>}
      {rules.map((r, i) => (
        <div key={r.key} className="flex gap-2 mb-1.5 items-center">
          <Select size="small" className="!w-[140px]" showSearch value={r.source || undefined} placeholder="选择源字段" options={srcFields}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], source: v }; onUpdate(edge.id, { ...data, mappingRules: ns }); }} />
          <span className="text-slate-400 text-xs">→</span>
          <Select size="small" className="!w-[140px]" showSearch value={r.target || undefined} placeholder="选择目标字段" options={tgtFields}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], target: v }; onUpdate(edge.id, { ...data, mappingRules: ns }); }} />
          <Select size="small" className="!w-[110px]" value={r.type} options={[{ label: '直接映射', value: 'PATH' }, { label: '表达式', value: 'EXPRESSION' }, { label: '常量', value: 'CONSTANT' }]}
            onChange={v => { const ns = [...rules]; ns[i] = { ...ns[i], type: v as 'PATH' | 'EXPRESSION' | 'CONSTANT' }; onUpdate(edge.id, { ...data, mappingRules: ns }); }} />
          <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => onUpdate(edge.id, { ...data, mappingRules: rules.filter((_, j) => j !== i) })} />
        </div>
      ))}
      <Button size="small" icon={<PlusOutlined />} className="mt-1" onClick={() => onUpdate(edge.id, { ...data, mappingRules: [...rules, { key: String(Date.now()), source: '', target: '', type: 'PATH' }] })}>添加映射规则</Button>
    </Modal>
  );
};

// ===== Field Edit Modal =====

const FieldEditModal: React.FC<{ open: boolean; entity: EntityDef | null; onClose: () => void; onSave: (fields: FieldDef[]) => void; }> = ({ open, entity, onClose, onSave }) => {
  const [fields, setFields] = React.useState<FieldDef[]>([]);
  React.useEffect(() => { if (entity) setFields([...entity.fields]); }, [entity, open]);
  if (!entity) return null;
  return (
    <Modal title={`编辑 ${entity.entityType}`} open={open} onCancel={onClose} width={600} onOk={() => { onSave(fields); onClose(); }} okText="保存">
      <table className="w-full border-collapse">
        <thead><tr className="bg-slate-50 text-left"><th className="p-1 text-[11px] font-semibold text-slate-500 w-[30px]">PK</th><th className="p-1 text-[11px] font-semibold text-slate-500">字段名</th><th className="p-1 text-[11px] font-semibold text-slate-500 w-[90px]">类型</th><th className="w-[40px]"></th></tr></thead>
        <tbody>{fields.map((f, i) => (
          <tr key={i} className="border-t border-slate-100">
            <td className="p-1 text-center"><input type="checkbox" checked={f.primaryKey || false} onChange={e => { const n = [...fields]; n[i] = { ...n[i], primaryKey: e.target.checked }; setFields(n); }} /></td>
            <td className="p-1"><input value={f.name} onChange={e => { const n = [...fields]; n[i] = { ...n[i], name: e.target.value }; setFields(n); }} className="w-full border border-slate-200 rounded p-0.5 text-xs" /></td>
            <td className="p-1"><input value={f.type} onChange={e => { const n = [...fields]; n[i] = { ...n[i], type: e.target.value }; setFields(n); }} className="w-full border border-slate-200 rounded p-0.5 text-xs text-slate-400" /></td>
            <td className="p-1"><Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => setFields(fields.filter((_, j) => j !== i))} /></td>
          </tr>
        ))}</tbody>
      </table>
      <Button size="small" icon={<PlusOutlined />} className="mt-2" onClick={() => setFields([...fields, { name: '', type: 'string' }])}>添加字段</Button>
    </Modal>
  );
};

// ===== Main =====

const EntityModeling: React.FC = () => {
  const initNodes: Node[] = ENTITIES.map((e, i) => ({ id: e.entityType, type: 'entity', position: { x: 80 + (i % 3) * 320, y: 80 + Math.floor(i / 3) * 280 }, data: { ...e } }));
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(initNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selNode, setSelNode] = React.useState<string | null>(null);
  const [selEdge, setSelEdge] = React.useState<string | null>(null);
  const [mappingOpen, setMappingOpen] = React.useState(false);
  const [fieldEditOpen, setFieldEditOpen] = React.useState(false);
  const [editingId, setEditingId] = React.useState<string | null>(null);
  const { zoomIn, zoomOut, fitView } = useReactFlow();

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
    const newEdge: Edge = { id: `${conn.source}->${conn.target}`, source: conn.source, target: conn.target, type: 'smoothstep', style: { stroke: color, strokeWidth: 2, strokeDasharray: rt !== 'FOREIGN_KEY' ? '5,5' : undefined }, markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 }, data: { relationType: rt, mappingRules: [] } as EdgeMeta };
    setEdges(eds => addEdge(newEdge, eds) as Edge[]);
    setSelEdge(newEdge.id); setMappingOpen(true);
  }, [nodes, setEdges]);

  const selectedEntity = selNode ? ENTITIES.find(e => e.entityType === selNode) || null : null;

  return (
    <div className="w-screen h-screen flex flex-col overflow-hidden bg-white">
      {/* Top navbar */}
      <div className="h-11 flex items-center justify-between px-4 border-b border-slate-200 shrink-0">
        <div className="flex items-center gap-3">
          <input value="默认实体模型" readOnly className="border-none text-sm font-semibold text-slate-900 bg-transparent outline-none w-40" />
          <span className="text-[11px] text-slate-400">已保存</span>
        </div>
        <Space size={6}>
          <Button size="small" type="text" icon={<SaveOutlined />} onClick={() => message.success('已保存')}>保存</Button>
        </Space>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Left sidebar */}
        <div className="w-[260px] shrink-0 border-r border-slate-200 bg-white flex flex-col overflow-hidden">
          <div className="px-4 py-2.5 border-b border-slate-100 flex justify-between items-center">
            <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">实体</span>
            <span className="text-[10px] text-slate-300">{ENTITIES.length}</span>
          </div>
          <div className="flex-1 overflow-auto">
            {(['BUSINESS', 'SYSTEM', 'API_REQUEST', 'API_RESPONSE'] as EntityCategory[]).map(cat => {
              const ents = ENTITIES.filter(e => e.entityCategory === cat);
              if (ents.length === 0) return null;
              return (
                <div key={cat}>
                  <div className="px-4 py-1.5 text-[10px] text-slate-400 font-semibold">{CAT_LABELS[cat]}</div>
                  {ents.map(e => (
                    <div key={e.entityType} onClick={() => { setSelNode(e.entityType); setSelEdge(null); const n = nodes.find(x => x.id === e.entityType); if (n) fitView({ nodes: [n], duration: 300, padding: 0.5 }); }}
                      className={`flex items-center gap-2 px-4 py-1 cursor-pointer text-xs transition-colors ${selNode === e.entityType ? 'bg-slate-50' : 'hover:bg-slate-50'}`}
                      style={{ borderLeft: selNode === e.entityType ? `2px solid ${e.color}` : '2px solid transparent' }}>
                      <div className="w-1.5 h-1.5 rounded-sm shrink-0" style={{ backgroundColor: e.color }} />
                      <span className={`flex-1 truncate ${selNode === e.entityType ? 'text-slate-900' : 'text-slate-600'}`}>{e.entityType}</span>
                      <span className="text-[9px] text-slate-400">{e.fields.length}</span>
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
          <div className="p-3 border-t border-slate-100">
            <Button size="small" type="dashed" icon={<PlusOutlined />} block className="!text-[11px]" onClick={() => {
              const id = `Entity_${Date.now()}`;
              setNodes(nds => [...nds, { id, type: 'entity', position: { x: 300 + Math.random() * 200, y: 300 + Math.random() * 200 }, data: { entityType: id, entityCategory: 'BUSINESS' as EntityCategory, description: '', color: '#3b82f6', fields: [{ name: 'id', type: 'string', primaryKey: true }] } as EntityDef }]);
              setSelNode(id);
            }}>新增</Button>
          </div>
        </div>

        {/* Canvas */}
        <div className="flex-1 relative">
          <ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, n) => { setSelNode(n.id); setSelEdge(null); }}
            onNodeDoubleClick={(_, n) => { setSelNode(n.id); setFieldEditOpen(true); }}
            onEdgeClick={(_, e) => { setSelEdge(e.id); setSelNode(null); setMappingOpen(true); }}
            onPaneClick={() => { setSelNode(null); setSelEdge(null); }}
            nodeTypes={nodeTypes as any} fitView deleteKeyCode={['Backspace', 'Delete']} snapToGrid snapGrid={[20, 20]}>
            <Background color="#e2e8f0" gap={24} size={1.5} />
            <MiniMap className="!w-[160px] !h-[110px] !bottom-3 !right-3" nodeColor={(n) => (n.data as any)?.color || '#ddd'} maskColor="rgba(0,0,0,0.05)" />
          </ReactFlow>

          {/* Bottom toolbar */}
          <div className="absolute bottom-4 left-1/2 -translate-x-1/2 bg-white rounded-lg border border-slate-200 shadow-sm flex items-center px-2 py-1 gap-1 z-10">
            <Button size="small" type="text" icon={<MinusOutlined />} onClick={() => zoomOut({ duration: 200 })} />
            <Button size="small" type="text" icon={<PlusOutlined />} onClick={() => zoomIn({ duration: 200 })} />
            <div className="w-px h-4 bg-slate-200 mx-1" />
            <Button size="small" type="text" icon={<ExpandOutlined />} onClick={() => fitView({ duration: 300 })} />
          </div>

          {/* Entity info panel on selection */}
          {selectedEntity && (
            <div className="absolute top-3 right-3 bg-white rounded-lg border border-slate-200 shadow-lg w-[220px] p-3 z-10">
              <div className="flex items-center gap-2 mb-1.5">
                <div className="w-2 h-2 rounded-sm" style={{ backgroundColor: selectedEntity.color }} />
                <span className="text-[13px] font-semibold">{selectedEntity.entityType}</span>
                <Tag className="!text-[9px] !m-0" color={selectedEntity.entityCategory === 'BUSINESS' ? 'blue' : selectedEntity.entityCategory === 'SYSTEM' ? 'orange' : 'green'}>{CAT_LABELS[selectedEntity.entityCategory]}</Tag>
              </div>
              <div className="text-[11px] text-slate-500">{selectedEntity.description}</div>
              <div className="text-[10px] text-slate-400 mt-1">{selectedEntity.fields.length} fields</div>
              <div className="mt-2 flex gap-1">
                <Button size="small" className="!text-[11px]" onClick={() => setFieldEditOpen(true)}>编辑字段</Button>
              </div>
            </div>
          )}
        </div>
      </div>

      <MappingModal open={mappingOpen} edge={edges.find(e => e.id === selEdge) || null}
        onClose={() => { setSelEdge(null); setMappingOpen(false); }}
        onUpdate={(id, data) => setEdges(eds => eds.map(e => e.id === id ? { ...e, data: data as any } : e))} />

      <FieldEditModal open={fieldEditOpen} entity={selectedEntity}
        onClose={() => setFieldEditOpen(false)}
        onSave={(fields) => { if (selNode) setNodes(nds => nds.map(n => n.id === selNode ? { ...n, data: { ...(n.data as unknown as EntityDef), fields } } : n)); }} />
    </div>
  );
};

const Wrapped: React.FC = () => <ReactFlowProvider><EntityModeling /></ReactFlowProvider>;
export default Wrapped;