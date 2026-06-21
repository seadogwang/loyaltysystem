import React, { useState, useCallback } from 'react';
import { ApiOutlined, ThunderboltOutlined, CodeOutlined, SendOutlined, PlayCircleOutlined, DeleteOutlined, PoweroffOutlined, EllipsisOutlined } from '@ant-design/icons';
import {
  ReactFlow, ReactFlowProvider, Background, Controls, Handle, Position, MarkerType,
  useNodesState, useEdgesState, addEdge, type Node, type Edge, type Connection, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

// ==================== Node Types ====================

const TRIGGER_NODES = [
  { type: 'webhook', label: 'Webhook', desc: '接收 HTTP 请求触发', color: '#ec4899', icon: <ApiOutlined /> },
  { type: 'schedule', label: '定时任务', desc: '按计划周期执行', color: '#f59e0b', icon: <ThunderboltOutlined /> },
  { type: 'manual', label: '手动触发', desc: '点击按钮手动执行', color: '#3b82f6', icon: <SendOutlined /> },
];

const ACTION_NODES = [
  { type: 'http', label: 'HTTP 请求', desc: '调用外部 API', color: '#22c55e', icon: <ApiOutlined /> },
  { type: 'script', label: '脚本', desc: '执行自定义脚本', color: '#a855f7', icon: <CodeOutlined /> },
  { type: 'transform', label: '数据转换', desc: '转换数据格式', color: '#f97316', icon: <CodeOutlined /> },
];

// ==================== n8n-style Custom Node ====================

const WorkflowNode: React.FC<NodeProps> = ({ id, data, selected }) => {
  const nodeData = data as any;
  const isTrigger = nodeData.role === 'trigger';
  const [hovered, setHovered] = useState(false);

  return (
    <div
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{ position: 'relative' }}
    >
      {/* Toolbar */}
      {hovered && (
        <div style={{
          position: 'absolute', top: -32, left: 0, right: 0,
          display: 'flex', justifyContent: 'center', gap: 2, zIndex: 10,
        }}>
          <button style={toolBtnStyle} title="执行"><PlayCircleOutlined style={{ fontSize: 12 }} /></button>
          <button style={toolBtnStyle} title="禁用"><PoweroffOutlined style={{ fontSize: 12 }} /></button>
          <button style={toolBtnStyle} title="删除"><DeleteOutlined style={{ fontSize: 12 }} /></button>
          <button style={toolBtnStyle} title="更多"><EllipsisOutlined style={{ fontSize: 12 }} /></button>
        </div>
      )}

      {/* Trigger badge */}
      {isTrigger && (
        <div style={{
          position: 'absolute', top: -12, left: '50%', transform: 'translateX(-50%)',
          background: '#f59e0b', color: '#fff', padding: '2px 8px', borderRadius: 4,
          fontSize: 10, fontWeight: 600, zIndex: 5, whiteSpace: 'nowrap',
        }}>
          <ThunderboltOutlined style={{ fontSize: 10, marginRight: 3 }} />
          TRIGGER
        </div>
      )}

      {/* Handle - Input */}
      {!isTrigger && (
        <Handle type="target" position={Position.Top} id="input"
          style={{ background: '#6b7280', width: 10, height: 10, border: '2px solid #fff', top: -5 }} />
      )}

      {/* Node body */}
      <div style={{
        width: 120, background: '#fff',
        border: selected ? `2px solid ${nodeData.color}` : '1px solid #e5e7eb',
        borderRadius: 12, overflow: 'hidden',
        boxShadow: selected ? '0 4px 16px rgba(0,0,0,0.1)' : '0 1px 3px rgba(0,0,0,0.05)',
        transition: 'box-shadow 0.15s',
      }}>
        {/* Icon area */}
        <div style={{
          padding: '16px 0 8px', display: 'flex', justifyContent: 'center',
          background: `${nodeData.color}08`,
        }}>
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: `${nodeData.color}15`, color: nodeData.color,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 24,
          }}>
            {nodeData.icon}
          </div>
        </div>
        {/* Label */}
        <div style={{ padding: '6px 12px 10px', textAlign: 'center' }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: '#1f2937' }}>{nodeData.label}</div>
        </div>
      </div>

      {/* Handle - Output with "+" */}
      <Handle type="source" position={Position.Bottom} id="output"
        style={{ background: 'transparent', width: 24, height: 24, border: 'none', bottom: -12 }}>
        <div style={{
          width: 24, height: 24, borderRadius: 6, background: '#e5e7eb',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: '#6b7280', fontSize: 16, fontWeight: 300,
          transition: 'all 0.15s',
        }}
          onMouseEnter={e => { e.currentTarget.style.background = nodeData.color; e.currentTarget.style.color = '#fff'; }}
          onMouseLeave={e => { e.currentTarget.style.background = '#e5e7eb'; e.currentTarget.style.color = '#6b7280'; }}
        >
          +
        </div>
      </Handle>
    </div>
  );
};

const toolBtnStyle: React.CSSProperties = {
  width: 28, height: 24, borderRadius: 4, border: '1px solid #e5e7eb',
  background: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center',
  color: '#6b7280', padding: 0,
};

const nodeTypes = { workflow: WorkflowNode };

// ==================== Main Component ====================

const ApiFlowDesignerInner: React.FC = () => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [nodeCreatorOpen, setNodeCreatorOpen] = useState(false);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [creatorTab, setCreatorTab] = useState<'trigger' | 'action'>('trigger');
  const [lastNodeId, setLastNodeId] = useState<string | null>(null);

  const onConnect = useCallback(
    (conn: Connection) => setEdges(eds => addEdge({
      ...conn, type: 'smoothstep',
      style: { stroke: '#9ca3af', strokeWidth: 2 },
      markerEnd: { type: MarkerType.ArrowClosed, color: '#9ca3af', width: 16, height: 16 },
    }, eds) as Edge[]),
    [setEdges]
  );

  const addNode = useCallback((nodeDef: any, role?: string) => {
    const id = `${nodeDef.type}_${Date.now()}`;
    const newNode: Node = {
      id, type: 'workflow',
      position: { x: 300 + Math.random() * 100, y: 200 + Math.random() * 100 },
      data: { ...nodeDef, role },
    };
    setNodes(nds => {
      const updated = [...nds, newNode];
      // Auto-connect from last node
      if (lastNodeId && role !== 'trigger') {
        setEdges(eds => [...eds, {
          id: `${lastNodeId}->${id}`,
          source: lastNodeId, target: id,
          type: 'smoothstep',
          style: { stroke: '#9ca3af', strokeWidth: 2 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#9ca3af', width: 16, height: 16 },
        }]);
      }
      return updated;
    });
    setLastNodeId(id);
    setNodeCreatorOpen(false);
  }, [lastNodeId, setNodes, setEdges]);

  const handleNodeClick = useCallback((_: any, node: Node) => {
    setLastNodeId(node.id);
  }, []);

  const onDragOver = useCallback((e: React.DragEvent) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; }, []);
  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const nodeType = e.dataTransfer.getData('nodeType');
    const nodeLabel = e.dataTransfer.getData('nodeLabel');
    const nodeColor = e.dataTransfer.getData('nodeColor');
    const nodeDesc = e.dataTransfer.getData('nodeDesc');
    const nodeRole = e.dataTransfer.getData('nodeRole');
    if (nodeType) {
      const id = `${nodeType}_${Date.now()}`;
      const bounds = (e.target as HTMLElement).closest('.react-flow')?.getBoundingClientRect();
      const pos = bounds ? { x: e.clientX - bounds.left - 60, y: e.clientY - bounds.top - 40 } : { x: 300, y: 200 };
      const newNode: Node = { id, type: 'workflow', position: pos, data: { type: nodeType, label: nodeLabel, color: nodeColor, desc: nodeDesc, role: nodeRole } };
      setNodes(nds => {
        const updated = [...nds, newNode];
        if (lastNodeId && nodeRole !== 'trigger') {
          setEdges(eds => [...eds, {
            id: `${lastNodeId}->${id}`,
            source: lastNodeId, target: id,
            type: 'smoothstep',
            style: { stroke: '#9ca3af', strokeWidth: 2 },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#9ca3af', width: 16, height: 16 },
          }]);
        }
        return updated;
      });
      setLastNodeId(id);
    }
  }, [lastNodeId, setNodes, setEdges]);

  const hasNodes = nodes.length > 0;

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 56px - 32px - 48px)', background: '#fff' }}>
      {/* 左侧图标栏 */}
      <div style={{ width: 40, minWidth: 40, borderRight: '1px solid #e5e7eb', background: '#fafafa', display: 'flex', flexDirection: 'column', alignItems: 'center', paddingTop: 8 }}>
        <div onClick={() => setMenuOpen(!menuOpen)} title="API 流程"
          style={{ width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: 4, cursor: 'pointer', fontSize: 14, color: menuOpen ? '#3b82f6' : '#6b7280', background: menuOpen ? '#eff6ff' : 'transparent' }}>
          <ApiOutlined />
        </div>
      </div>

      {/* 流程列表 */}
      {menuOpen && (
        <div style={{ width: 150, minWidth: 150, borderRight: '1px solid #e5e7eb', background: '#fff', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '10px 12px', borderBottom: '1px solid #e5e7eb', fontSize: 12, fontWeight: 600, color: '#6b7280' }}>API 流程</div>
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9ca3af', fontSize: 12 }}>空</div>
        </div>
      )}

      {/* 画布 */}
      <div style={{ flex: 1, position: 'relative', background: '#f9fafb' }} onDragOver={onDragOver} onDrop={onDrop}>
        {!hasNodes ? (
          <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
              <button onClick={() => setNodeCreatorOpen(true)}
                style={{ width: 80, height: 80, borderRadius: '50%', border: '2px dashed #d1d5db', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', transition: 'all 0.2s' }}
                onMouseEnter={e => { e.currentTarget.style.borderColor = '#3b82f6'; e.currentTarget.style.background = '#eff6ff'; }}
                onMouseLeave={e => { e.currentTarget.style.borderColor = '#d1d5db'; e.currentTarget.style.background = '#fff'; }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#9ca3af" strokeWidth="2" strokeLinecap="round"><path d="M5 12h14M12 5v14" /></svg>
              </button>
              <span style={{ color: '#9ca3af', fontSize: 14 }}>第一步</span>
            </div>
          </div>
        ) : (
          <ReactFlow
            nodes={nodes} edges={edges}
            onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
            onConnect={onConnect} onNodeClick={handleNodeClick}
            nodeTypes={nodeTypes as any} fitView deleteKeyCode={['Backspace', 'Delete']}
            defaultEdgeOptions={{ type: 'smoothstep', style: { stroke: '#9ca3af', strokeWidth: 2 } }}
          >
            <Background color="#e5e7eb" gap={20} />
            <Controls />
          </ReactFlow>
        )}

        {/* Node Creator Panel */}
        {nodeCreatorOpen && (
          <div style={{ position: 'absolute', top: 0, right: 0, bottom: 0, width: 280, background: '#fff', borderLeft: '1px solid #e5e7eb', boxShadow: '-4px 0 12px rgba(0,0,0,0.05)', display: 'flex', flexDirection: 'column', zIndex: 10 }}>
            <div style={{ padding: '16px', borderBottom: '1px solid #e5e7eb' }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#1f2937', marginBottom: 4 }}>
                {creatorTab === 'trigger' ? 'What triggers this workflow?' : 'Add an action'}
              </div>
              <div style={{ fontSize: 11, color: '#9ca3af' }}>
                {creatorTab === 'trigger' ? 'A trigger is a step that starts your workflow' : 'Actions perform operations on your data'}
              </div>
            </div>
            <div style={{ display: 'flex', borderBottom: '1px solid #e5e7eb' }}>
              <button onClick={() => setCreatorTab('trigger')} style={{ flex: 1, padding: '8px 0', border: 'none', background: creatorTab === 'trigger' ? '#fff' : '#f9fafb', color: creatorTab === 'trigger' ? '#3b82f6' : '#6b7280', fontSize: 12, fontWeight: 600, cursor: 'pointer', borderBottom: creatorTab === 'trigger' ? '2px solid #3b82f6' : '2px solid transparent' }}>触发</button>
              <button onClick={() => setCreatorTab('action')} style={{ flex: 1, padding: '8px 0', border: 'none', background: creatorTab === 'action' ? '#fff' : '#f9fafb', color: creatorTab === 'action' ? '#3b82f6' : '#6b7280', fontSize: 12, fontWeight: 600, cursor: 'pointer', borderBottom: creatorTab === 'action' ? '2px solid #3b82f6' : '2px solid transparent' }}>动作</button>
            </div>
            <div style={{ flex: 1, overflow: 'auto', padding: 8 }}>
              {(creatorTab === 'trigger' ? TRIGGER_NODES : ACTION_NODES).map(n => (
                <div key={n.type} draggable
                  onDragStart={e => {
                    e.dataTransfer.setData('nodeType', n.type);
                    e.dataTransfer.setData('nodeLabel', n.label);
                    e.dataTransfer.setData('nodeColor', n.color);
                    e.dataTransfer.setData('nodeDesc', n.desc);
                    e.dataTransfer.setData('nodeRole', creatorTab === 'trigger' ? 'trigger' : 'action');
                  }}
                  onClick={() => addNode(n, creatorTab === 'trigger' ? 'trigger' : 'action')}
                  style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px', borderRadius: 6, cursor: 'grab', marginBottom: 4, transition: 'background 0.15s' }}
                  onMouseEnter={e => { e.currentTarget.style.background = '#f9fafb'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}>
                  <div style={{ width: 32, height: 32, borderRadius: 6, flexShrink: 0, background: `${n.color}15`, color: n.color, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16 }}>{n.icon}</div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 12, fontWeight: 500, color: '#1f2937' }}>{n.label}</div>
                    <div style={{ fontSize: 10, color: '#9ca3af', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{n.desc}</div>
                  </div>
                </div>
              ))}
            </div>
            <button onClick={() => setNodeCreatorOpen(false)} style={{ padding: '10px', border: 'none', borderTop: '1px solid #e5e7eb', background: '#fafafa', cursor: 'pointer', fontSize: 12, color: '#6b7280' }}>关闭</button>
          </div>
        )}
      </div>
    </div>
  );
};

const ApiFlowDesigner: React.FC = () => (
  <ReactFlowProvider><ApiFlowDesignerInner /></ReactFlowProvider>
);

export default ApiFlowDesigner;