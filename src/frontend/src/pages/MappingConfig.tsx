import React, { useState, useMemo } from 'react';
import { Input, Select, Switch, Button, Typography, Space, Tag, Table, message, Collapse } from 'antd';
import { PlusOutlined, DeleteOutlined, PlayCircleOutlined, SaveOutlined, CodeOutlined, TableOutlined, LinkOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';

const { Text } = Typography;

// ==================== 渠道数据 ====================

interface Channel {
  key: string;
  name: string;
  configured: boolean;
  mappingMode: 'VISUAL' | 'SCRIPT';
}

const defaultChannels: Channel[] = [
  { key: 'TMALL', name: '天猫', configured: true, mappingMode: 'SCRIPT' },
  { key: 'JD', name: '京东', configured: true, mappingMode: 'SCRIPT' },
  { key: 'DOUYIN', name: '抖音', configured: true, mappingMode: 'VISUAL' },
  { key: 'WECHAT', name: '微信', configured: true, mappingMode: 'VISUAL' },
  { key: 'POS', name: 'POS', configured: false, mappingMode: 'VISUAL' },
];

interface FieldMapping {
  id: string;
  source: string;
  target: string;
  type: 'AUTO' | 'MANUAL';
}

const defaultMappings: Record<string, FieldMapping[]> = {
  TMALL: [
    { id: '1', source: 'tradeNo', target: 'idempotent_key', type: 'AUTO' },
    { id: '2', source: 'tradeTime', target: 'event_time', type: 'AUTO' },
    { id: '3', source: 'channelType', target: 'channel', type: 'AUTO' },
    { id: '4', source: 'totalPrice', target: 'payload.order_info.amounts.total_price', type: 'MANUAL' },
  ],
};

const defaultScripts: Record<string, string> = {
  TMALL: `function transform(source, context) {
  // 等值映射和路径映射自动生成基础结构
  const base = applyFieldMappings(source);

  // 补充特殊逻辑
  base.event_type = mapOrderType(source.orderType);

  return base;
}

function mapOrderType(type) {
  const MAP = { 1: 'ORDER', 2: 'REFUND', 3: 'PRESALE' };
  return MAP[type] || 'CUSTOM';
}`,
};

const defaultPreviewInput: Record<string, string> = {
  TMALL: JSON.stringify({
    tradeNo: 'TM20240601001',
    tradeTime: '2024-06-01T10:30:00',
    channelType: 'TMALL',
    orderType: 1,
    totalPrice: 299.00,
    tradePrice: 279.00,
    products: [{ commodity_code: 'SKU001', price: 299.00, quant: 2 }],
  }, null, 2),
};

// ==================== 主组件 ====================

const MappingConfig: React.FC = () => {
  const [channels, setChannels] = useState<Channel[]>(defaultChannels);
  const [selectedChannel, setSelectedChannel] = useState<string>('TMALL');
  const [mappingMode, setMappingMode] = useState<'VISUAL' | 'SCRIPT'>('VISUAL');
  const [mappings, setMappings] = useState<Record<string, FieldMapping[]>>(defaultMappings);
  const [scripts, setScripts] = useState<Record<string, string>>(defaultScripts);
  const [previewInput, setPreviewInput] = useState<Record<string, string>>(defaultPreviewInput);
  const [previewOutput, setPreviewOutput] = useState<string>('');
  const [showPreview, setShowPreview] = useState(false);

  const channel = channels.find(c => c.key === selectedChannel);
  const currentMappings = mappings[selectedChannel] || [];
  const currentScript = scripts[selectedChannel] || '// 编写映射脚本';
  const currentInput = previewInput[selectedChannel] || '';

  const addMapping = () => {
    const newMapping: FieldMapping = {
      id: `m_${Date.now()}`, source: '', target: '', type: 'MANUAL',
    };
    setMappings(prev => ({
      ...prev, [selectedChannel]: [...(prev[selectedChannel] || []), newMapping],
    }));
  };

  const updateMapping = (id: string, field: keyof FieldMapping, value: any) => {
    setMappings(prev => ({
      ...prev,
      [selectedChannel]: (prev[selectedChannel] || []).map(m => m.id === id ? { ...m, [field]: value } : m),
    }));
  };

  const deleteMapping = (id: string) => {
    setMappings(prev => ({
      ...prev, [selectedChannel]: (prev[selectedChannel] || []).filter(m => m.id !== id),
    }));
  };

  const handleScriptChange = (value: string | undefined) => {
    setScripts(prev => ({ ...prev, [selectedChannel]: value || '' }));
  };

  const handlePreviewInputChange = (value: string | undefined) => {
    setPreviewInput(prev => ({ ...prev, [selectedChannel]: value || '' }));
  };

  const handleTest = () => {
    try {
      const input = JSON.parse(currentInput);
      const output = simulateTransform(input, currentMappings, currentScript);
      setPreviewOutput(JSON.stringify(output, null, 2));
      setShowPreview(true);
    } catch (e: any) {
      message.error('输入 JSON 格式无效: ' + e.message);
    }
  };

  const handleSave = () => {
    message.success('映射配置已保存');
  };

  const mappingColumns = [
    {
      title: '源字段', dataIndex: 'source', width: 160,
      render: (v: string, r: FieldMapping) => (
        <Input size="small" value={v} onChange={e => updateMapping(r.id, 'source', e.target.value)}
          placeholder="例: tradeNo" style={{ fontFamily: 'monospace', fontSize: 11 }} />
      ),
    },
    {
      title: '→', width: 40, render: () => <Text style={{ color: '#1677ff' }}>→</Text>,
    },
    {
      title: '目标字段路径', dataIndex: 'target',
      render: (v: string, r: FieldMapping) => (
        <Input size="small" value={v} onChange={e => updateMapping(r.id, 'target', e.target.value)}
          placeholder="例: idempotent_key" style={{ fontFamily: 'monospace', fontSize: 11 }} />
      ),
    },
    {
      title: '方式', dataIndex: 'type', width: 70,
      render: (v: string, r: FieldMapping) => (
        <Tag color={v === 'AUTO' ? 'green' : 'blue'} style={{ fontSize: 10 }}>
          {v === 'AUTO' ? '自动' : '手动'}
        </Tag>
      ),
    },
    {
      title: '', width: 40,
      render: (_: any, r: FieldMapping) => (
        <Button size="small" type="text" danger icon={<DeleteOutlined />}
          onClick={() => deleteMapping(r.id)} style={{ height: 20 }} />
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', flex: 1, minHeight: 'calc(100vh - 120px)', background: '#fff' }}>
      {/* 左侧渠道列表 */}
      <div style={{ width: 200, borderRight: '1px solid #f0f0f0', padding: '12px 8px', flexShrink: 0 }}>
        <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 12 }}>渠道列表</Text>
        {channels.map(c => (
          <div key={c.key} onClick={() => setSelectedChannel(c.key)} style={{
            display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px', cursor: 'pointer',
            borderRadius: 6, fontSize: 12, background: selectedChannel === c.key ? '#f0f5ff' : 'transparent',
            marginBottom: 2,
          }}>
            <span style={{
              width: 8, height: 8, borderRadius: '50%',
              background: c.configured ? '#52c41a' : '#d9d9d9',
              flexShrink: 0,
            }} />
            <span style={{ flex: 1 }}>{c.name}</span>
            <Tag color={c.mappingMode === 'SCRIPT' ? 'purple' : 'blue'} style={{ fontSize: 9 }}>
              {c.mappingMode === 'SCRIPT' ? '脚本' : '可视'}
            </Tag>
          </div>
        ))}
        <Button size="small" icon={<PlusOutlined />} block style={{ marginTop: 8, fontSize: 11 }}>新增渠道</Button>
      </div>

      {/* 右侧配置区 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {/* 顶部信息 */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <Text strong style={{ fontSize: 14 }}>渠道: {channel?.name}</Text>
            <Tag color="green">系统实体: TransactionEvent</Tag>
          </Space>
          <Space>
            <Button size="small" onClick={handleSave} icon={<SaveOutlined />} style={{ fontSize: 11 }}>保存</Button>
            <Button size="small" type="primary" onClick={handleTest} icon={<PlayCircleOutlined />} style={{ fontSize: 11 }}>测试映射</Button>
          </Space>
        </div>

        {/* 映射模式切换 */}
        <div style={{ padding: '8px 16px', borderBottom: '1px solid #f0f0f0' }}>
          <Space>
            <Button size="small" type={mappingMode === 'VISUAL' ? 'primary' : 'default'}
              icon={<TableOutlined />} onClick={() => setMappingMode('VISUAL')} style={{ fontSize: 11 }}>
              路径映射
            </Button>
            <Button size="small" type={mappingMode === 'SCRIPT' ? 'primary' : 'default'}
              icon={<CodeOutlined />} onClick={() => setMappingMode('SCRIPT')} style={{ fontSize: 11 }}>
              脚本补充
            </Button>
          </Space>
        </div>

        {/* 映射内容 */}
        <div style={{ flex: 1, overflow: 'auto', padding: '16px' }}>
          {mappingMode === 'VISUAL' ? (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <Text strong style={{ fontSize: 13 }}>路径映射表</Text>
                <Button size="small" icon={<PlusOutlined />} onClick={addMapping} style={{ fontSize: 11 }}>添加映射行</Button>
              </div>
              <Table
                dataSource={currentMappings}
                columns={mappingColumns}
                rowKey="id"
                size="small"
                pagination={false}
                locale={{ emptyText: '暂无映射，点击"添加映射行"开始配置' }}
              />
            </div>
          ) : (
            <div>
              <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>脚本补充（Monaco Editor）</Text>
              <Text type="secondary" style={{ fontSize: 11, display: 'block', marginBottom: 8 }}>
                等值映射和路径映射的结果自动生成代码骨架，在此补充特殊逻辑
              </Text>
              <Editor
                height="300px"
                language="javascript"
                value={currentScript}
                onChange={handleScriptChange}
                theme="vs"
                options={{ fontSize: 12, minimap: { enabled: false }, lineNumbers: 'on', scrollBeyondLastLine: false, tabSize: 2 }}
              />
            </div>
          )}

          {/* 预览区 */}
          {showPreview && (
            <div style={{ marginTop: 16, display: 'flex', gap: 16 }}>
              <div style={{ flex: 1 }}>
                <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>输入示例 JSON</Text>
                <Editor height="200px" language="json" value={currentInput}
                  onChange={handlePreviewInputChange} theme="vs"
                  options={{ fontSize: 11, minimap: { enabled: false }, scrollBeyondLastLine: false }} />
              </div>
              <div style={{ flex: 1 }}>
                <Text strong style={{ fontSize: 13, display: 'block', marginBottom: 8 }}>预览转换结果</Text>
                <Editor height="200px" language="json" value={previewOutput}
                  theme="vs" options={{ fontSize: 11, minimap: { enabled: false }, readOnly: true, scrollBeyondLastLine: false }} />
              </div>
            </div>
          )}
        </div>

        {/* 底部系统流程提示 */}
        <div style={{ padding: '8px 16px', borderTop: '1px solid #f0f0f0', background: '#fafafa' }}>
          <Text type="secondary" style={{ fontSize: 10 }}>
            系统流程（固定，仅供参考）: API → 映射转换 → One-ID 匹配 → Drools 积分计算 → 积分发放/核销 → 等级评估 → 事件发布
          </Text>
        </div>
      </div>
    </div>
  );
};

// ==================== 辅助函数 ====================

function simulateTransform(input: any, mappings: FieldMapping[], script: string): any {
  const result: any = {
    event_id: `evt_${Date.now()}`,
    member_id: '',
    event_type: 'ORDER',
    channel: '',
    event_time: new Date().toISOString(),
    idempotent_key: '',
    payload: {},
  };

  for (const m of mappings) {
    if (m.target.startsWith('payload.')) {
      const path = m.target.replace('payload.', '').split('.');
      let obj = result.payload;
      for (let i = 0; i < path.length - 1; i++) {
        if (!obj[path[i]]) obj[path[i]] = {};
        obj = obj[path[i]];
      }
      obj[path[path.length - 1]] = input[m.source] ?? '';
    } else {
      result[m.target] = input[m.source] ?? '';
    }
  }

  return result;
}

export default MappingConfig;