import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Space, Typography, Tag, message, Modal, Input, Select, Form,
  Descriptions, Divider, Drawer, List, Alert, Badge, Tooltip, Spin,
} from 'antd';
import {
  SaveOutlined, CheckCircleOutlined, ThunderboltOutlined,
  CodeOutlined, EyeOutlined, PlusOutlined,
  ArrowLeftOutlined, RobotOutlined, BranchesOutlined,
} from '@ant-design/icons';
import {
  ReactFlow, Background, Controls, MiniMap,
  useNodesState, useEdgesState, addEdge,
  ReactFlowProvider, Handle, Position,
  type Node, type Edge, type Connection, type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {
  createPlan, getPlan, saveDag, validateDag, compileToBpmn, aiGenerate, getNodeTypes,
  CampaignPlan, CanvasDag,
} from '../../api/campaign';

const { Text, Title } = Typography;
const { TextArea } = Input;

// ==================== Custom Node Component ====================

const typeColors: Record<string, string> = {
  flow: '#1890ff', channel: '#52c41a', action: '#fa8c16',
  ai: '#722ed1', governance: '#eb2f96', integration: '#13c2c2',
};

const CampaignCanvasNode: React.FC<NodeProps> = ({ data, selected }) => (
  <div style={{
    padding: '10px 16px', borderRadius: 8, border: `2px solid ${selected ? '#1890ff' : '#d9d9d9'}`,
    background: '#fff', minWidth: 160, boxShadow: selected ? '0 0 0 2px rgba(24,144,255,0.3)' : '0 1px 4px rgba(0,0,0,0.08)',
    position: 'relative',
  }}>
    <Handle type="target" position={Position.Top} style={{ background: '#555' }} />
    <div style={{ textAlign: 'center' }}>
      <Tag color={data.color || '#1890ff'} style={{ marginBottom: 4 }}>{data.label}</Tag>
      <div style={{ fontSize: 12, color: '#666' }}>{data.type}</div>
    </div>
    <Handle type="source" position={Position.Bottom} style={{ background: '#555' }} />
  </div>
);

const nodeTypes = { campaignNode: CampaignCanvasNode };

// ==================== Main Component ====================

const CampaignCanvasEditor: React.FC = () => {
  const { planId } = useParams<{ planId: string }>();
  const navigate = useNavigate();
  const [plan, setPlan] = useState<CampaignPlan | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  // React Flow state
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<any>(null);

  // UI state
  const [nodeTypesList, setNodeTypesList] = useState<any[]>([]);
  const [selectedNode, setSelectedNode] = useState<any>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [aiModalOpen, setAiModalOpen] = useState(false);
  const [bpmnModalOpen, setBpmnModalOpen] = useState(false);
  const [bpmnXml, setBpmnXml] = useState('');
  const [validationResult, setValidationResult] = useState<any>(null);
  const [aiForm] = Form.useForm();

  // Load plan & node types
  useEffect(() => {
    loadNodeTypes();
    if (planId && planId !== 'new') {
      loadPlan(planId);
    } else {
      // Create new plan
      setPlan({
        id: 'new', name: '新活动计划', status: 'DRAFT', workspaceId: 'ws_001',
      } as CampaignPlan);
    }
  }, [planId]);

  const loadNodeTypes = async () => {
    try {
      const types = await getNodeTypes();
      setNodeTypesList(types || []);
    } catch { /* ignore */ }
  };

  const loadPlan = async (id: string) => {
    setLoading(true);
    try {
      const p = await getPlan(id);
      setPlan(p);
      // Load canvas DAG if exists
      // (simplified: would need getCanvas endpoint)
    } catch (err: any) {
      message.error('加载计划失败');
    } finally {
      setLoading(false);
    }
  };

  // Handle node selection
  const onNodeClick = useCallback((_: any, node: Node) => {
    setSelectedNode(node);
    setDrawerOpen(true);
  }, []);

  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ ...params, animated: true }, eds)),
    [setEdges],
  );

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      const type = event.dataTransfer.getData('application/reactflow');
      if (!type || !reactFlowInstance) return;

      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX, y: event.clientY,
      });
      const nodeType = nodeTypesList.find((n: any) => n.type === type);
      const newId = `node_${Date.now()}`;

      const newNode: Node = {
        id: newId,
        type: 'campaignNode',
        position,
        data: {
          label: nodeType?.label || type,
          type: type,
          color: typeColors[nodeType?.category] || '#1890ff',
          config: {},
        },
      };
      setNodes((nds) => nds.concat(newNode));
    },
    [reactFlowInstance, nodeTypesList, setNodes],
  );

  // Save canvas
  const handleSave = async () => {
    if (!plan) return;
    setSaving(true);
    try {
      const dag: CanvasDag = {
        nodes: nodes.map(n => ({
          id: n.id, type: n.data.type, label: n.data.label,
          config: n.data.config || {}, x: n.position.x, y: n.position.y,
        })),
        edges: edges.map(e => ({
          id: e.id, source: e.source, target: e.target,
          condition: e.data?.condition, label: e.data?.label,
        })),
      };

      if (plan.id === 'new') {
        const created = await createPlan({ ...plan, workspaceId: 'ws_001', name: plan.name || '未命名计划' });
        await saveDag(created.id, dag);
        navigate(`/campaign/canvas/${created.id}`, { replace: true });
      } else {
        await saveDag(plan.id, dag);
      }
      message.success('保存成功');
    } catch (err: any) {
      message.error('保存失败: ' + (err?.response?.data?.message || err.message));
    } finally {
      setSaving(false);
    }
  };

  // Validate DAG
  const handleValidate = async () => {
    const dag: CanvasDag = {
      nodes: nodes.map(n => ({ id: n.id, type: n.data.type, label: n.data.label, config: n.data.config || {} })),
      edges: edges.map(e => ({ id: e.id, source: e.source, target: e.target })),
    };
    try {
      const result = await validateDag(dag);
      setValidationResult(result);
      if (result.valid) {
        message.success('✅ DAG 校验通过');
      } else {
        message.error(`校验失败: ${result.errors?.join('; ')}`);
      }
    } catch (err: any) {
      message.error('校验失败');
    }
  };

  // Compile to BPMN
  const handleCompile = async () => {
    if (!plan || plan.id === 'new') {
      message.warning('请先保存计划');
      return;
    }
    try {
      const xml = await compileToBpmn(plan.id);
      setBpmnXml(xml);
      setBpmnModalOpen(true);
    } catch (err: any) {
      message.error('编译失败');
    }
  };

  // AI Generate
  const handleAiGenerate = async (values: any) => {
    try {
      const dag = await aiGenerate({
        goal: values.goal, description: values.description,
        budget: values.budget, audience: values.audience,
        channel: values.channel, additionalInstructions: values.instructions,
      });
      if (dag?.nodes) {
        const flowNodes: Node[] = dag.nodes.map((n: any, i: number) => ({
          id: n.id, type: 'campaignNode', position: { x: n.x || 100 + i * 200, y: n.y || 200 },
          data: { label: n.label || n.type, type: n.type, config: n.config || {} },
        }));
        const flowEdges: Edge[] = (dag.edges || []).map((e: any) => ({
          id: e.id, source: e.source, target: e.target, animated: true,
          data: { condition: e.condition, label: e.label },
        }));
        setNodes(flowNodes);
        setEdges(flowEdges);
        message.success('AI 生成 DAG 完成');
      }
      setAiModalOpen(false);
      aiForm.resetFields();
    } catch (err: any) {
      message.error('AI 生成失败');
    }
  };

  const onNodeDragStart = (_: any, nodeType: any) => (event: React.DragEvent) => {
    event.dataTransfer.setData('application/reactflow', nodeType.type);
    event.dataTransfer.effectAllowed = 'move';
  };

  if (loading) return <div style={{ display: 'flex', justifyContent: 'center', padding: 100 }}><Spin size="large" /></div>;

  return (
    <div style={{ height: 'calc(100vh - 100px)', display: 'flex', flexDirection: 'column' }}>
      {/* Toolbar */}
      <Card size="small" style={{ marginBottom: 8, flexShrink: 0 }} bodyStyle={{ padding: '8px 16px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Button icon={<ArrowLeftOutlined />} size="small" onClick={() => navigate('/campaign/workspaces')}>
              返回
            </Button>
            <Title level={5} style={{ margin: 0 }}>{plan?.name || '画布编辑器'}</Title>
            {plan?.status && <Tag color={plan.status === 'APPROVED' ? 'green' : plan.status === 'DRAFT' ? 'default' : 'blue'}>{plan.status}</Tag>}
          </Space>
          <Space>
            <Button icon={<RobotOutlined />} onClick={() => setAiModalOpen(true)}>AI 生成</Button>
            <Button icon={<CheckCircleOutlined />} onClick={handleValidate}>校验</Button>
            <Button icon={<CodeOutlined />} onClick={handleCompile}>编译 BPMN</Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>保存</Button>
          </Space>
        </div>
        {validationResult && (
          <div style={{ marginTop: 8 }}>
            {validationResult.errors?.map((e: string, i: number) => <Alert key={i} type="error" message={e} banner style={{ marginBottom: 4 }} />)}
            {validationResult.warnings?.map((w: string, i: number) => <Alert key={i} type="warning" message={w} banner style={{ marginBottom: 4 }} />)}
            {validationResult.valid && !validationResult.errors?.length && <Alert type="success" message="DAG 校验通过" banner />}
          </div>
        )}
      </Card>

      <div style={{ display: 'flex', flex: 1, gap: 8, overflow: 'hidden' }}>
        {/* Left: Node Palette */}
        <Card size="small" title="节点" style={{ width: 180, flexShrink: 0, overflow: 'auto' }} bodyStyle={{ padding: 8 }}>
          {['flow', 'channel', 'action', 'ai', 'governance', 'integration'].map(category => (
            <div key={category} style={{ marginBottom: 12 }}>
              <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase' }}>{category}</Text>
              {nodeTypesList.filter((n: any) => n.category === category).map((nt: any) => (
                <div key={nt.type}
                  draggable
                  onDragStart={onNodeDragStart(null, nt)}
                  style={{
                    padding: '6px 8px', margin: '4px 0', borderRadius: 4, cursor: 'grab',
                    background: '#f5f5f5', border: '1px solid #e8e8e8', fontSize: 12,
                    display: 'flex', alignItems: 'center', gap: 6,
                  }}>
                  <Badge color={typeColors[category]} />
                  {nt.label}
                </div>
              ))}
            </div>
          ))}
        </Card>

        {/* Center: Canvas */}
        <div style={{ flex: 1 }} ref={reactFlowWrapper}>
          <ReactFlowProvider>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onInit={setReactFlowInstance}
              onDrop={onDrop}
              onDragOver={onDragOver}
              onNodeClick={onNodeClick}
              nodeTypes={nodeTypes}
              fitView
              style={{ background: '#fafafa' }}
            >
              <Background />
              <Controls />
              <MiniMap nodeStrokeWidth={3} style={{ height: 120 }} />
            </ReactFlow>
          </ReactFlowProvider>
        </div>
      </div>

      {/* Node Properties Drawer */}
      <Drawer title="节点属性" placement="right" width={360}
        open={drawerOpen} onClose={() => { setDrawerOpen(false); setSelectedNode(null); }}>
        {selectedNode && (
          <div>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="ID">{selectedNode.id}</Descriptions.Item>
              <Descriptions.Item label="类型">
                <Tag color={typeColors[selectedNode.data?.color]}>{selectedNode.data?.label}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="位置">
                ({Math.round(selectedNode.position?.x)}, {Math.round(selectedNode.position?.y)})
              </Descriptions.Item>
            </Descriptions>
            <Divider />
            <Text strong>配置</Text>
            <pre style={{ marginTop: 8, padding: 8, background: '#f5f5f5', borderRadius: 4, fontSize: 12 }}>
              {JSON.stringify(selectedNode.data?.config || {}, null, 2)}
            </pre>
          </div>
        )}
      </Drawer>

      {/* AI Generation Modal */}
      <Modal title={<><RobotOutlined /> AI 生成 DAG</>} open={aiModalOpen}
        onCancel={() => setAiModalOpen(false)} onOk={() => aiForm.submit()} okText="生成">
        <Form form={aiForm} layout="vertical" onFinish={handleAiGenerate}>
          <Form.Item name="goal" label="目标" rules={[{ required: true }]}>
            <Input placeholder="例如：提升 VIP 会员转化率" />
          </Form.Item>
          <Form.Item name="audience" label="受众">
            <Input placeholder="例如：30天未活跃用户" />
          </Form.Item>
          <Form.Item name="channel" label="渠道">
            <Select options={[
              { label: '邮件', value: 'email' }, { label: '短信', value: 'sms' },
              { label: '推送', value: 'push' }, { label: '邮件+短信', value: 'email+sms' },
            ]} />
          </Form.Item>
          <Form.Item name="budget" label="预算">
            <Input placeholder="例如：10000 USD" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={2} placeholder="更多描述信息" />
          </Form.Item>
          <Form.Item name="instructions" label="额外指示">
            <TextArea rows={2} placeholder="例如：需要人工审批节点" />
          </Form.Item>
        </Form>
      </Modal>

      {/* BPMN Output Modal */}
      <Modal title={<><CodeOutlined /> BPMN XML</>} open={bpmnModalOpen}
        onCancel={() => setBpmnModalOpen(false)} width={800} footer={
          <Button onClick={() => { navigator.clipboard.writeText(bpmnXml); message.success('已复制') }}>复制 XML</Button>
        }>
        <pre style={{ maxHeight: 500, overflow: 'auto', background: '#1e1e1e', color: '#d4d4d4', padding: 16, borderRadius: 4, fontSize: 12 }}>
          {bpmnXml || '暂无 BPMN 输出'}
        </pre>
      </Modal>
    </div>
  );
};

export default CampaignCanvasEditor;
