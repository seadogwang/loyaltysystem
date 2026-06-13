import React, { useState, useEffect, useCallback, useRef } from 'react';
import { ReactFlow,
  Background, Controls, MiniMap, MarkerType,
  type Node, type Edge, type Connection,
  useNodesState, useEdgesState, addEdge, Panel,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Button, Card, Input, Select, Space, Tag, Typography, Divider, Table, Modal, message, Tabs } from 'antd';
import { PlusOutlined, SaveOutlined, SendOutlined, DeleteOutlined, LinkOutlined, ReloadOutlined } from '@ant-design/icons';
import PageWrapper from '../components/PageWrapper';
import api from '../api';

const { Title, Text } = Typography;

// ==================== 类型 ====================

type EntityCategory = 'BUSINESS' | 'API_REQUEST' | 'API_RESPONSE' | 'SYSTEM';
type RelationType = 'FOREIGN_KEY' | 'INBOUND_MAPPING' | 'OUTBOUND_MAPPING';

interface EntityNode {
  entityType: string;
  entityCategory: EntityCategory;
  description: string;
  fieldCount: number;
  color: string;
}

interface MappingRule {
  key: string;
  source: string;
  target: string;
  type: 'PATH' | 'EXPRESSION' | 'CONSTANT';
  expression?: string;
}

interface EdgeData {
  relationType: RelationType;
  mappingRules: MappingRule[];
}

// ==================== 实体定义 ====================

const PREDEFINED_ENTITIES: EntityNode[] = [
  { entityType: 'Member',    entityCategory: 'BUSINESS',    description: '会员实体',     fieldCount: 8, color: '#1890ff' },
  { entityType: 'Order',     entityCategory: 'BUSINESS',    description: '订单实体',     fieldCount: 12, color: '#1890ff' },
  { entityType: 'OrderItem', entityCategory: 'BUSINESS',    description: '订单明细',     fieldCount: 5, color: '#1890ff' },
  { entityType: 'PointTx',   entityCategory: 'BUSINESS',    description: '积分流水',     fieldCount: 6, color: '#1890ff' },
  { entityType: 'TransactionEvent', entityCategory: 'SYSTEM', description: '交易事件',  fieldCount: 8, color: '#fa8c16' },
  { entityType: 'TmallOrderResp',   entityCategory: 'API_RESPONSE', description: '天猫订单响应', fieldCount: 6, color: '#52c41a' },
  { entityType: 'TmallOrderReq',    entityCategory: 'API_REQUEST',  description: '天猫订单请求', fieldCount: 4, color: '#52c41a' },
  { entityType: 'PointsChangeReq',  entityCategory: 'API_REQUEST',  description: '积分变动请求', fieldCount: 5, color: '#52c41a' },
  { entityType: 'PointsChangeResp', entityCategory: 'API_RESPONSE', description: '积分变动响应', fieldCount: 3, color: '#52c41a' },
];

// ==================== 自定义节点渲染 ====================

const renderNode = (data: any) => {
  const entity = data as EntityNode;
  const bgColor = entity.entityCategory === 'BUSINESS' ? '#e6f7ff' :
    entity.entityCategory === 'SYSTEM' ? '#fff7e6' :
    entity.entityCategory === 'API_REQUEST' ? '#f6ffed' : '#f9f0ff';
  const borderColor = entity.color;
  const borderStyle = entity.entityCategory.startsWith('API') ? 'dashed' : 'solid';
  const tagColor = entity.entityCategory === 'BUSINESS' ? 'blue' :
    entity.entityCategory === 'SYSTEM' ? 'orange' :
    entity.entityCategory === 'API_REQUEST' ? 'green' : 'purple';
  const tagLabel = entity.entityCategory === 'BUSINESS' ? '业务' :
    entity.entityCategory === 'SYSTEM' ? '系统' :
    entity.entityCategory === 'API_REQUEST' ? 'API请求' : 'API响应';

  return (
    <div style={{
      padding: '10px 16px', borderRadius: 8, minWidth: 160,
      background: bgColor, border: `2px ${borderStyle} ${borderColor}`,
      boxShadow: '0 2px 6px rgba(0,0,0,0.08)', textAlign: 'center',
    }}>
      <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{entity.entityType}</div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: 4, marginBottom: 2 }}>
        <Tag color={tagColor} style={{ fontSize: 10, margin: 0 }}>{tagLabel}</Tag>
        <Text type="secondary" style={{ fontSize: 10 }}>{entity.fieldCount} 字段</Text>
      </div>
      <Text type="secondary" style={{ fontSize: 10 }}>{entity.description}</Text>
    </div>
  );
};

// ==================== 节点/边工具 ====================

function createEntityNodes(entities: EntityNode[]): Node[] {
  const cols = 3;
  return entities.map((e, i) => ({
    id: e.entityType,
    type: 'default',
    position: { x: 50 + (i % cols) * 280, y: 50 + Math.floor(i / cols) * 150 },
    data: { ...e, label: renderNode(e) },
  }));
}

// ==================== 右侧面板 ====================

const RightPanel: React.FC<{
  selectedNode: Node | null;
  selectedEdge: Edge | null;
  edges: Edge[];
  onUpdateEdge: (edgeId: string, data: any) => void;
}> = ({ selectedNode, selectedEdge, edges, onUpdateEdge }) => {
  const [activeTab, setActiveTab] = useState('entity');

  if (selectedEdge) {
    const edgeData = selectedEdge.data as unknown as EdgeData;
    const rules = edgeData.mappingRules || [];

    const addRule = () => {
      const newRules = [...rules, { key: String(Date.now()), source: '', target: '', type: 'PATH' as const }];
      onUpdateEdge(selectedEdge.id, { ...edgeData, mappingRules: newRules });
    };

    const updRule = (idx: number, field: string, val: any) => {
      const newRules = [...rules];
      newRules[idx] = { ...newRules[idx], [field]: val };
      onUpdateEdge(selectedEdge.id, { ...edgeData, mappingRules: newRules });
    };

    const delRule = (idx: number) => {
      onUpdateEdge(selectedEdge.id, { ...edgeData, mappingRules: rules.filter((_, i) => i !== idx) });
    };

    const typeLabel = edgeData.relationType === 'INBOUND_MAPPING' ? '入站映射' :
      edgeData.relationType === 'OUTBOUND_MAPPING' ? '出站映射' : '业务关系';

    return (
      <div style={{ padding: 12 }}>
        <Tag color="blue">{typeLabel}</Tag>
        <div style={{ marginTop: 8 }}>
          <Text style={{ fontSize: 11, color: '#999' }}>源实体: {selectedEdge.source}</Text>
          <br />
          <Text style={{ fontSize: 11, color: '#999' }}>目标实体: {selectedEdge.target}</Text>
        </div>
        <Divider style={{ margin: '8px 0' }} />
        <Text strong style={{ fontSize: 12 }}>字段映射</Text>
        <div style={{ marginTop: 4 }}>
          {rules.map((r, i) => (
            <div key={r.key} style={{ display: 'flex', gap: 4, marginBottom: 4, alignItems: 'center' }}>
              <Input size="small" style={{ width: 70, fontSize: 11 }} placeholder="源字段" value={r.source}
                onChange={e => updRule(i, 'source', e.target.value)} />
              <Text style={{ fontSize: 10 }}>→</Text>
              <Input size="small" style={{ width: 70, fontSize: 11 }} placeholder="目标字段" value={r.target}
                onChange={e => updRule(i, 'target', e.target.value)} />
              <Select size="small" style={{ width: 90, fontSize: 10 }} value={r.type}
                onChange={v => updRule(i, 'type', v)}
                options={[
                  { label: '直接', value: 'PATH' },
                  { label: '表达式', value: 'EXPRESSION' },
                  { label: '常量', value: 'CONSTANT' },
                ]} />
              <Button size="small" type="text" danger icon={<DeleteOutlined />}
                onClick={() => delRule(i)} style={{ height: 22 }} />
            </div>
          ))}
          <Button size="small" icon={<PlusOutlined />} onClick={addRule} block style={{ fontSize: 11, marginTop: 4 }}>
            添加映射
          </Button>
        </div>
      </div>
    );
  }

  if (selectedNode) {
    const data = selectedNode.data as unknown as EntityNode;
    return (
      <div style={{ padding: 12 }}>
        <Title level={5} style={{ marginBottom: 4 }}>{data.entityType}</Title>
        <Tag color={data.entityCategory === 'BUSINESS' ? 'blue' : data.entityCategory === 'SYSTEM' ? 'orange' : 'green'}>
          {data.entityCategory}
        </Tag>
        <div style={{ marginTop: 8 }}>
          <Text style={{ fontSize: 12 }}>{data.description}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 11 }}>字段数: {data.fieldCount}</Text>
        </div>
        <Divider style={{ margin: '8px 0' }} />
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button size="small" icon={<LinkOutlined />} block
            onClick={() => message.info('从节点 Handle 拖拽到另一节点即可创建连线')}>
            如何创建连线
          </Button>
          <Button size="small" block onClick={() => window.open('/schema-editor?entity=' + data.entityType, '_blank')}>
            编辑 Schema
          </Button>
        </Space>
      </div>
    );
  }

  return (
    <div style={{ padding: 12, textAlign: 'center', color: '#ccc', fontSize: 12 }}>
      点击节点查看属性<br />点击连线配置映射
    </div>
  );
};

// ==================== 主组件 ====================

const EntityModeling: React.FC = () => {
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>(createEntityNodes(PREDEFINED_ENTITIES));
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);
  const [mode, setMode] = useState<'inbound' | 'outbound'>('inbound');
  const [channel, setChannel] = useState('TMALL');
  const [saving, setSaving] = useState(false);

  // 处理连线
  const onConnect = useCallback((connection: Connection) => {
    if (!connection.source || !connection.target) return;
    const srcNode = nodes.find(n => n.id === connection.source);
    const tgtNode = nodes.find(n => n.id === connection.target);
    if (!srcNode || !tgtNode) return;

    const srcCat = (srcNode.data as unknown as EntityNode).entityCategory;
    const tgtCat = (tgtNode.data as unknown as EntityNode).entityCategory;

    let relationType: RelationType;
    if (srcCat === 'API_RESPONSE' && (tgtCat === 'BUSINESS' || tgtCat === 'SYSTEM')) {
      relationType = 'INBOUND_MAPPING';
    } else if ((srcCat === 'BUSINESS' || srcCat === 'SYSTEM') && tgtCat === 'API_REQUEST') {
      relationType = 'OUTBOUND_MAPPING';
    } else if ((srcCat === 'BUSINESS' || srcCat === 'SYSTEM') && (tgtCat === 'BUSINESS' || tgtCat === 'SYSTEM')) {
      relationType = 'FOREIGN_KEY';
    } else {
      message.warning('不支持该连线方向');
      return;
    }

    const newEdge: Edge = {
      id: `${connection.source}->${connection.target}`,
      source: connection.source,
      target: connection.target,
      type: 'default',
      style: {
        stroke: relationType === 'INBOUND_MAPPING' ? '#52c41a' : relationType === 'OUTBOUND_MAPPING' ? '#fa8c16' : '#1677ff',
        strokeDasharray: relationType !== 'FOREIGN_KEY' ? '5,5' : undefined,
        strokeWidth: 2,
      },
      markerEnd: { type: MarkerType.ArrowClosed, color: relationType === 'INBOUND_MAPPING' ? '#52c41a' : relationType === 'OUTBOUND_MAPPING' ? '#fa8c16' : '#1677ff' },
      data: { relationType, mappingRules: [] },
    };

    setEdges(eds => addEdge(newEdge, eds) as Edge[]);
    setSelectedEdge(newEdge);
    setSelectedNode(null);
  }, [nodes, setEdges]);

  // 保存
  const handleSave = async () => {
    setSaving(true);
    try {
      const relations = edges.map(e => ({
        sourceEntity: e.source,
        targetEntity: e.target,
        ...e.data,
      }));
      await api.post('/admin/entity-relations', { relations, channel });
      message.success('已保存');
    } catch { message.error('保存失败'); }
    setSaving(false);
  };

  const handlePublish = async () => {
    await handleSave();
    message.success('已发布');
  };

  const onNodeClick = useCallback((_: any, node: Node) => {
    setSelectedNode(node);
    setSelectedEdge(null);
  }, []);

  const onEdgeClick = useCallback((_: any, edge: Edge) => {
    setSelectedEdge(edge);
    setSelectedNode(null);
  }, []);

  const onPaneClick = useCallback(() => {
    setSelectedNode(null);
    setSelectedEdge(null);
  }, []);

  const updateEdgeData = (edgeId: string, data: any) => {
    setEdges(eds => eds.map(e => e.id === edgeId ? { ...e, data } : e));
    setSelectedEdge(prev => prev?.id === edgeId ? { ...prev, data } : prev);
  };

  return (
    <PageWrapper>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 12 }}>
        <Title level={4} style={{ margin: 0 }}>统一实体建模与映射</Title>
        <Space>
          <Select size="small" value={mode} onChange={setMode} style={{ width: 100 }}
            options={[{ label: '入站', value: 'inbound' }, { label: '出站', value: 'outbound' }]} />
          <Select size="small" value={channel} onChange={setChannel} style={{ width: 100 }}
            options={['TMALL', 'JD', 'DOUYIN', 'WECHAT_MINI', 'WEBHOOK'].map(c => ({ label: c, value: c }))} />
          <Button icon={<SaveOutlined />} size="small" onClick={handleSave} loading={saving}>保存</Button>
          <Button type="primary" icon={<SendOutlined />} size="small" onClick={handlePublish}>发布</Button>
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 0, height: 'calc(100vh - 160px)', border: '1px solid #d9d9d9', borderRadius: 8, overflow: 'hidden' }}>
        {/* 画布 */}
        <div style={{ flex: 3 }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
            onEdgeClick={onEdgeClick}
            onPaneClick={onPaneClick}
            fitView
          >
            <Background />
            <Controls />
            <MiniMap />
            <Panel position="top-left">
              <div style={{ background: '#fff', borderRadius: 6, padding: '8px 12px', border: '1px solid #e8e8e8', fontSize: 11 }}>
                <div>连线颜色: <span style={{ color: '#1677ff', fontWeight: 500 }}>蓝色</span>=业务关系</div>
                <div style={{ marginTop: 2 }}>连线颜色: <span style={{ color: '#52c41a', fontWeight: 500 }}>绿色</span>=入站映射</div>
                <div style={{ marginTop: 2 }}>连线颜色: <span style={{ color: '#fa8c16', fontWeight: 500 }}>橙色</span>=出站映射</div>
              </div>
            </Panel>
          </ReactFlow>
        </div>

        {/* 右侧面板 */}
        <Card size="small" style={{ width: 300, borderLeft: '1px solid #d9d9d9', borderRadius: 0, overflow: 'auto' }}
          title={selectedEdge ? '映射配置' : selectedNode ? '实体属性' : '属性面板'}>
          <RightPanel
            selectedNode={selectedNode}
            selectedEdge={selectedEdge}
            edges={edges}
            onUpdateEdge={updateEdgeData}
          />
        </Card>
      </div>
    </PageWrapper>
  );
};

export default EntityModeling;