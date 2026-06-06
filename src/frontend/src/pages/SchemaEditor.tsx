import React, { useState } from 'react';
import { Input, Select, Switch, Button, Typography, Space, Tag, message } from 'antd';
import { PlusOutlined, DeleteOutlined, KeyOutlined, SaveOutlined, SendOutlined } from '@ant-design/icons';

const { Text } = Typography;

// ==================== 类型 ====================

interface SchemaField {
  key: string;
  type: string;
  title: string;
  required?: boolean;
  xComponent?: string;
  xReactions?: string;
  deprecated?: boolean;
  children?: SchemaField[];
  arrayItem?: SchemaField;
  indent: number;
}

const FIELD_TYPES = ['string', 'number', 'integer', 'boolean', 'object', 'array'];
const COMPONENTS = ['Input', 'NumberPicker', 'Select', 'Switch', 'DatePicker', 'ImageUploader', 'CascadingAddress'];

function parseSchemaToFields(schema: any, indent: number = 0): SchemaField[] {
  if (!schema?.properties) return [];
  const result: SchemaField[] = [];
  for (const [key, val] of Object.entries(schema.properties) as [string, any][]) {
    const field: SchemaField = {
      key, type: val.type || 'string', title: val.title || val.description || '',
      required: schema.required?.includes(key), xComponent: val['x-component'],
      xReactions: val['x-reactions'], deprecated: val.deprecated, indent,
    };
    if (val.type === 'object' && val.properties) {
      field.children = parseSchemaToFields(val, indent + 1);
    }
    if (val.type === 'array' && val.items?.properties) {
      field.arrayItem = { key: 'item', type: 'object', title: '', indent: indent + 1, children: parseSchemaToFields(val.items, indent + 2) };
    }
    result.push(field);
  }
  return result;
}

function fieldsToSchema(fields: SchemaField[]): any {
  const schema: any = { type: 'object', properties: {} };
  const required: string[] = [];
  for (const f of fields) {
    const prop: any = { type: f.type, title: f.title };
    if (f.xComponent) prop['x-component'] = f.xComponent;
    if (f.xReactions) prop['x-reactions'] = f.xReactions;
    if (f.deprecated) prop.deprecated = true;
    if (f.required) required.push(f.key);
    if (f.type === 'object' && f.children) {
      const child = fieldsToSchema(f.children);
      prop.properties = child.properties;
    }
    if (f.type === 'array' && f.arrayItem) {
      prop.items = { type: 'object', properties: fieldsToSchema(f.arrayItem.children || []).properties };
    }
    schema.properties[f.key] = prop;
  }
  if (required.length) schema.required = required;
  return schema;
}

// ==================== FieldRow ====================

const FieldRow: React.FC<{
  field: SchemaField;
  onUpdate: (f: SchemaField) => void;
  onDelete: (key: string) => void;
  onSelect: (f: SchemaField) => void;
  selected: boolean;
  level?: number;
}> = ({ field, onUpdate, onDelete, onSelect, selected, level = 0 }) => {
  const isContainer = field.type === 'object' || field.type === 'array';
  return (
    <>
      <div onClick={() => onSelect(field)} style={{
        display: 'flex', alignItems: 'center', gap: 6, padding: '4px 8px 4px ' + (16 + level * 20) + 'px',
        cursor: 'pointer', background: selected ? '#f0f5ff' : 'transparent',
        borderBottom: '1px solid #f5f5f5', fontSize: 12,
      }}>
        <span style={{ fontFamily: 'monospace', color: '#1a1a1a', fontWeight: 500, minWidth: 60 }}>{field.key}</span>
        <Tag color="blue" style={{ fontSize: 10 }}>{field.type}</Tag>
        {field.title && <Text type="secondary" style={{ fontSize: 11, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{field.title}</Text>}
        {field.required && <Text type="danger" style={{ fontSize: 10 }}>*</Text>}
        {field.deprecated && <Tag color="default" style={{ fontSize: 9 }}>废</Tag>}
        <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={e => { e.stopPropagation(); onDelete(field.key); }} style={{ height: 20, fontSize: 10 }} />
      </div>
      {field.children?.map(c => (
        <FieldRow key={c.key} field={c} onUpdate={onUpdate} onDelete={onDelete} onSelect={onSelect} selected={selected} level={level + 1} />
      ))}
      {field.arrayItem && (
        <FieldRow field={field.arrayItem} onUpdate={(f) => { field.arrayItem = f; onUpdate(field); }} onDelete={() => {}} onSelect={onSelect} selected={false} level={level + 1} />
      )}
    </>
  );
};

// ==================== PropertyPanel ====================

const SchemaPropPanel: React.FC<{
  field: SchemaField | null;
  onUpdate: (f: SchemaField) => void;
  onClose: () => void;
}> = ({ field, onUpdate, onClose }) => {
  if (!field) return (
    <div style={{ width: 260, padding: 16, borderLeft: '1px solid #f0f0f0' }}>
      <Text type="secondary" style={{ fontSize: 12 }}>点击左侧字段查看属性</Text>
    </div>
  );
  return (
    <div style={{ width: 260, padding: 16, borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}>
      <Text strong style={{ fontSize: 13 }}>字段属性</Text>
      <div style={{ marginTop: 12 }}>
        <Text type="secondary" style={{ fontSize: 10 }}>字段标识 (key)</Text>
        <Input size="small" value={field.key} onChange={e => onUpdate({ ...field, key: e.target.value })}
          style={{ fontFamily: 'monospace', fontSize: 11 }} />
      </div>
      <div style={{ marginTop: 8 }}>
        <Text type="secondary" style={{ fontSize: 10 }}>标题 (title)</Text>
        <Input size="small" value={field.title} onChange={e => onUpdate({ ...field, title: e.target.value })}
          placeholder="字段的中文标题" style={{ fontSize: 11 }} />
      </div>
      <div style={{ marginTop: 8 }}>
        <Text type="secondary" style={{ fontSize: 10 }}>类型</Text>
        <Select size="small" value={field.type} style={{ width: '100%' }}
          onChange={v => onUpdate({ ...field, type: v })}
          options={FIELD_TYPES.map(t => ({ label: t, value: t }))} />
      </div>
      <div style={{ marginTop: 8 }}>
        <Text type="secondary" style={{ fontSize: 10 }}>x-component</Text>
        <Select size="small" value={field.xComponent || ''} style={{ width: '100%' }} allowClear
          onChange={v => onUpdate({ ...field, xComponent: v || undefined })}
          options={COMPONENTS.map(c => ({ label: c, value: c }))} />
      </div>
      <Space style={{ marginTop: 8 }}>
        <Switch size="small" checked={field.required} onChange={v => onUpdate({ ...field, required: v })} />
        <Text style={{ fontSize: 10 }}>必填</Text>
        <Switch size="small" checked={field.deprecated} onChange={v => onUpdate({ ...field, deprecated: v })} />
        <Text style={{ fontSize: 10 }}>废弃</Text>
      </Space>
      {field.deprecated && (
        <Text type="warning" style={{ fontSize: 10, display: 'block', marginTop: 4 }}>
          ⚠️ 废弃时会检查 DRL 规则引用
        </Text>
      )}
    </div>
  );
};

// ==================== 主组件 ====================

const SchemaEditor: React.FC = () => {
  const [entityType, setEntityType] = useState<'MEMBER' | 'TRANSACTION'>('MEMBER');
  const [fields, setFields] = useState<SchemaField[]>(() => {
    // 默认会员字段
    const memberSchema = {
      type: 'object',
      properties: {
        pet_name: { type: 'string', title: '宠物名称', 'x-component': 'Input' },
        member_level_index: { type: 'number', title: '会员等级指数', 'x-component': 'NumberPicker' },
      },
    };
    return parseSchemaToFields(memberSchema);
  });
  const [selectedField, setSelectedField] = useState<SchemaField | null>(null);
  const [exportVisible, setExportVisible] = useState(false);

  const addField = (container?: SchemaField) => {
    const newField: SchemaField = {
      key: `new_field_${Date.now()}`, type: 'string', title: '新字段', indent: container ? container.indent + 1 : 0,
    };
    if (container?.type === 'object') {
      container.children = [...(container.children || []), newField];
      setFields([...fields]);
    } else {
      setFields([...fields, newField]);
    }
    setSelectedField(newField);
  };

  const updateField = (f: SchemaField) => {
    setSelectedField(f);
    setFields([...fields]); // trigger re-render
  };

  const deleteField = (key: string) => {
    setFields(fields.filter(f => f.key !== key));
  };

  const exportSchema = () => {
    const schema = fieldsToSchema(fields);
    return JSON.stringify(schema, null, 2);
  };

  const handleSave = () => {
    message.success('Schema 已保存');
  };

  const handlePublish = () => {
    message.success('Schema 已发布');
  };

  return (
    <div style={{ display: 'flex', flex: 1, minHeight: 'calc(100vh - 120px)', background: '#fff' }}>
      {/* Schema 编辑区 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderRight: '1px solid #f0f0f0' }}>
        {/* 实体选择器 */}
        <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Space>
            <Button type={entityType === 'MEMBER' ? 'primary' : 'default'} size="small"
              onClick={() => setEntityType('MEMBER')} style={{ fontSize: 11 }}>
              会员 (ext_attributes)
            </Button>
            <Button type={entityType === 'TRANSACTION' ? 'primary' : 'default'} size="small"
              onClick={() => setEntityType('TRANSACTION')} style={{ fontSize: 11 }}>
              交易 (payload)
            </Button>
            <Button size="small" icon={<PlusOutlined />} style={{ fontSize: 11 }}>新建业务实体</Button>
          </Space>
          <Space>
            <Button size="small" icon={<SaveOutlined />} onClick={handleSave} style={{ fontSize: 11 }}>保存草稿</Button>
            <Button size="small" type="primary" icon={<SendOutlined />} onClick={handlePublish} style={{ fontSize: 11 }}>发布</Button>
          </Space>
        </div>

        {/* 字段列表 */}
        <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>
          {fields.map(f => (
            <FieldRow key={f.key} field={f} onUpdate={updateField} onDelete={deleteField}
              onSelect={setSelectedField} selected={selectedField?.key === f.key} />
          ))}
        </div>

        {/* 底部操作 */}
        <div style={{ padding: '8px 16px', borderTop: '1px solid #f0f0f0' }}>
          <Button size="small" icon={<PlusOutlined />} onClick={() => addField()} block style={{ fontSize: 11 }}>
            添加字段
          </Button>
        </div>
      </div>

      {/* 右侧属性面板 */}
      <SchemaPropPanel field={selectedField} onUpdate={updateField} onClose={() => setSelectedField(null)} />

      {/* Schema 导出预览（开发调试用） */}
      {exportVisible && (
        <div style={{ width: 400, padding: 16, borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}>
          <Text strong style={{ fontSize: 13 }}>JSON Schema 预览</Text>
          <pre style={{ fontSize: 11, background: '#f5f5f5', padding: 8, borderRadius: 4, marginTop: 8 }}>
            {JSON.stringify(fieldsToSchema(fields), null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
};

export default SchemaEditor;