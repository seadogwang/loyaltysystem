import React, { useState } from 'react';
import { Modal, Input, Tabs, Typography, message } from 'antd';
import type { EntityKind } from './types';

const { Text } = Typography;
const { TextArea } = Input;

interface NewEntityModalProps {
  open: boolean;
  kind: EntityKind;
  onClose: () => void;
  onCreate: (name: string, displayName: string, kind: EntityKind, schema?: object) => void;
}

const NewEntityModal: React.FC<NewEntityModalProps> = ({ open, kind, onClose, onCreate }) => {
  const [name, setName] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [activeTab, setActiveTab] = useState('manual');
  const [jsonText, setJsonText] = useState('');
  const [jsonError, setJsonError] = useState('');

  const kindLabel = kind === 'business' ? '业务实体' : 'API实体';
  const kindColor = kind === 'business' ? '#1677ff' : '#52c41a';
  const kindBadge = kind === 'business' ? '📦' : '🔌';

  const handleCreate = () => {
    if (activeTab === 'manual') {
      if (!name.trim()) { message.warning('请输入实体标识'); return; }
      if (!displayName.trim()) { message.warning('请输入实体名称'); return; }
      onCreate(name.trim(), displayName.trim(), kind);
      reset();
      onClose();
      return;
    }
    // JSON 导入
    if (!jsonText.trim()) { message.warning('请输入 JSON Schema'); return; }
    try {
      const schema = JSON.parse(jsonText);
      if (!schema.type && !schema.properties) {
        setJsonError('JSON Schema 需包含 type 或 properties');
        return;
      }
      const entityName = displayName.trim() || name.trim() || (schema.title || '导入实体');
      const entityId = name.trim() || sanitizeName(entityName);
      onCreate(entityId, entityName, kind, schema);
      reset();
      onClose();
    } catch {
      setJsonError('JSON 格式无效');
    }
  };

  const reset = () => {
    setName('');
    setDisplayName('');
    setJsonText('');
    setJsonError('');
    setActiveTab('manual');
  };

  const handleClose = () => { reset(); onClose(); };

  const jsonExample = JSON.stringify({
    "type": "object",
    "properties": {
      "total_price": { "type": "number", "minimum": 0, "description": "订单总额" },
      "trade_no": { "type": "string", "minLength": 1, "maxLength": 40 },
      "products": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "commodity_code": { "type": "string" },
            "price": { "type": "number" }
          }
        }
      }
    }
  }, null, 2);

  return (
    <Modal
      title={<span>{kindBadge} 新建{kindLabel}</span>}
      open={open}
      onCancel={handleClose}
      onOk={handleCreate}
      okText="创建"
      cancelText="取消"
      width={560}
    >
      {/* 基本信息 */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
        <div style={{ flex: 1 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>实体名称</Text>
          <Input size="small" value={displayName}
            onChange={e => setDisplayName(e.target.value)}
            placeholder={`例如: ${kind === 'business' ? 'OrderInfo' : 'OrderRequest'}`} />
        </div>
        <div style={{ flex: 1 }}>
          <Text type="secondary" style={{ fontSize: 11 }}>实体标识 (英文)</Text>
          <Input size="small" value={name}
            onChange={e => setName(e.target.value)}
            placeholder={`例如: ${kind === 'business' ? 'order_info' : 'order_request'}`}
            style={{ fontFamily: 'monospace' }} />
        </div>
      </div>

      {/* 导入方式 */}
      <Tabs activeKey={activeTab} onChange={setActiveTab} size="small"
        items={[
          {
            key: 'manual', label: '手动创建',
            children: (
              <div style={{ padding: '12px 0', textAlign: 'center', color: '#999', fontSize: 13 }}>
                将创建一个空实体，可在后续添加字段
              </div>
            ),
          },
          {
            key: 'json', label: '从 JSON 导入',
            children: (
              <div>
                <TextArea
                  value={jsonText}
                  onChange={e => { setJsonText(e.target.value); setJsonError(''); }}
                  placeholder={jsonExample}
                  rows={10}
                  style={{ fontFamily: 'monospace', fontSize: 11 }}
                />
                {jsonError && <Text type="danger" style={{ fontSize: 11 }}>{jsonError}</Text>}
                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 10 }}>
                    💡 支持粘贴 JSON Schema（properties/type 格式），字段定义将自动解析
                  </Text>
                </div>
              </div>
            ),
          },
        ]}
      />
    </Modal>
  );
};

function sanitizeName(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9_]/g, '_').replace(/_{2,}/g, '_');
}

export default NewEntityModal;