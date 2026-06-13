import React, { useCallback, useMemo } from 'react';
import {
  ReactFlow, Background, Controls, MiniMap, MarkerType, Panel,
  type Node, type Edge, type Connection, useNodesState, useEdgesState, addEdge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Button, Space, Select, Tooltip } from 'antd';
import { SaveOutlined, SendOutlined, ExpandOutlined, AimOutlined, CompressOutlined } from '@ant-design/icons';
import { useModelingStore, PREDEFINED_ENTITIES } from '../store';
import EntityNode from './nodes/EntityNode';
import SidePanel from './sidebar/SidePanel';
import type { EntityNode as EntityNodeType, EdgeData } from '../types';

const nodeTypes = { entity: EntityNode };

function createNodes(entities: EntityNodeType[]): Node[] {
  const cols = 3;
  return entities.map((e, i) => ({
    id: e.entityType,
    type: 'entity',
    position: { x: 60 + (i % cols) * 300, y: 60 + Math.floor(i / cols) * 260 },
    data: { ...e },
    draggable: true,
  }));
}

const EntityModelingCanvas: React.FC = () => {
  const store = useModelingStore();
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(createNodes(PREDEFINED_ENTITIES));
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const filteredNodes = useMemo(() => {
    if (store.filterCategory === 'ALL') return nodes;
    return nodes.filter(n => (n.data as unknown as EntityNodeType).entityCategory === store.filterCategory);
  }, [nodes, store.filterCategory]);

  const onConnect = useCallback((connection: Connection) => {
    if (!connection.source || !connection.target) return;
    const srcNode = nodes.find(n => n.id === connection.source);
    const tgtNode = nodes.find(n => n.id === connection.target);
    if (!srcNode || !tgtNode) return;

    const srcCat = (srcNode.data as unknown as EntityNodeType).entityCategory;
    const tgtCat = (tgtNode.data as unknown as EntityNodeType).entityCategory;

    let relationType: EdgeData['relationType'];
    if (srcCat === 'API_RESPONSE' && (tgtCat === 'BUSINESS' || tgtCat === 'SYSTEM')) {
      relationType = 'INBOUND_MAPPING';
    } else if ((srcCat === 'BUSINESS' || srcCat === 'SYSTEM') && tgtCat === 'API_REQUEST') {
      relationType = 'OUTBOUND_MAPPING';
    } else {
      relationType = 'FOREIGN_KEY';
    }

    const newEdge: Edge = {
      id: `${connection.source}->${connection.target}${connection.sourceHandle ? '_' + connection.sourceHandle : ''}`,
      source: connection.source,
      target: connection.target,
      sourceHandle: connection.sourceHandle || null,
      targetHandle: connection.targetHandle || null,
      type: 'smoothstep',
      animated: false,
      style: {
        stroke: relationType === 'INBOUND_MAPPING' ? '#22c55e' : relationType === 'OUTBOUND_MAPPING' ? '#f97316' : '#3b82f6',
        strokeWidth: 2,
        strokeDasharray: relationType !== 'FOREIGN_KEY' ? '5,5' : undefined,
      },
      markerEnd: { type: MarkerType.ArrowClosed, color: relationType === 'INBOUND_MAPPING' ? '#22c55e' : relationType === 'OUTBOUND_MAPPING' ? '#f97316' : '#3b82f6', width: 16, height: 16 },
      data: { relationType, mappingRules: [] } as EdgeData,
    };

    setEdges(eds => addEdge(newEdge, eds) as Edge[]);
  }, [nodes, setEdges]);

  const onNodeClick = useCallback((_: any, node: Node) => {
    store.setSelectedNodeId(node.id);
  }, [store]);

  const onEdgeClick = useCallback((_: any, edge: Edge) => {
    store.setSelectedEdgeId(edge.id);
  }, [store]);

  const onPaneClick = useCallback(() => {
    store.setSelectedNodeId(null);
    store.setSelectedEdgeId(null);
  }, [store]);

  const handleSave = async () => {
    store.setSaving(true);
    try { await new Promise(r => setTimeout(r, 500)); } finally { store.setSaving(false); }
  };

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 140px)', border: '1px solid #e2e8f0', borderRadius: 8, overflow: 'hidden' }}>
      <div style={{ flex: 1, position: 'relative' }}>
        <ReactFlow
          nodes={filteredNodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeClick={onNodeClick}
          onEdgeClick={onEdgeClick}
          onPaneClick={onPaneClick}
          nodeTypes={nodeTypes}
          fitView
          snapToGrid={store.snapToGrid}
          snapGrid={[20, 20]}
          deleteKeyCode={['Backspace', 'Delete']}
        >
          <Background color="#cbd5e1" gap={20} />
          <Controls />
          {store.showMiniMap && <MiniMap style={{ width: 160, height: 100 }} nodeColor={(n) => (n.data as unknown as EntityNodeType).color || '#ddd'} />}

          <Panel position="top-left">
            <Space size={4}>
              <Select size="small" value={store.mode} onChange={store.setMode} style={{ width: 80 }}
                options={[{ label: '入站', value: 'inbound' }, { label: '出站', value: 'outbound' }]} />
              <Select size="small" value={store.channel} onChange={store.setChannel} style={{ width: 90 }}
                options={['TMALL','JD','DOUYIN','WECHAT'].map(c => ({ label: c, value: c }))} />
              <Tooltip title="自适应"><Button size="small" icon={<ExpandOutlined />} onClick={() => {}} /></Tooltip>
              <Tooltip title="保存"><Button size="small" icon={<SaveOutlined />} onClick={handleSave} loading={store.saving} /></Tooltip>
            </Space>
          </Panel>

          <Panel position="bottom-center">
            <div style={{ background: '#fff', borderRadius: 6, padding: '4px 12px', border: '1px solid #e2e8f0', fontSize: 11, color: '#94a3b8' }}>
              {filteredNodes.length} entities · {edges.length} relationships
            </div>
          </Panel>
        </ReactFlow>
      </div>

      {!store.sidebarCollapsed && (
        <SidePanel
          edges={edges}
          onUpdateEdge={(id, data) => setEdges(eds => eds.map(e => e.id === id ? { ...e, data } : e))}
        />
      )}
    </div>
  );
};

export default EntityModelingCanvas;