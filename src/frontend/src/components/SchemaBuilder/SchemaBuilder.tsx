import React, { useState, useCallback, useMemo } from 'react';
import {
  Button, Card, Space, Modal, message, Input, Select, Switch,
  Form, Checkbox, Tag, Tooltip, Alert, Collapse,
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, SaveOutlined,
  DragOutlined, ExclamationCircleOutlined, CodeOutlined,
} from '@ant-design/icons';
import { DndProvider, useDrag, useDrop } from 'react-dnd';
import { HTML5Backend } from 'react-dnd-html5-backend';
import type { FieldSchema, JsonSchema, ComponentRegistryEntry } from '../../types';
import { checkFieldDeprecation, saveSchema } from '../../api';

// ==================== 组件注册表 ====================
const COMPONENT_REGISTRY: ComponentRegistryEntry[] = [
  { name: 'Input', label: '文本输入', category: 'basic', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'Input' } },
  { name: 'NumberPicker', label: '数字选择', category: 'basic', defaultSchema: { title: '新字段', type: 'number', 'x-component': 'NumberPicker' } },
  { name: 'Select', label: '下拉选择', category: 'basic', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'Select', enum: [{ label: '选项1', value: 'opt1' }] } },
  { name: 'Switch', label: '开关', category: 'basic', defaultSchema: { title: '新字段', type: 'boolean', 'x-component': 'Switch' } },
  { name: 'DatePicker', label: '日期选择', category: 'basic', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'DatePicker' } },
  { name: 'ImageUploader', label: '图片上传', category: 'advanced', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'ImageUploader' } },
  { name: 'CascadingAddress', label: '级联地址', category: 'advanced', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'CascadingAddress' } },
  { name: 'CustomStoreSelector', label: '门店选择器', category: 'custom', defaultSchema: { title: '新字段', type: 'string', 'x-component': 'CustomStoreSelector', 'x-component-props': { source: 'remote', endpoint: '/api/stores' } } },
];

const DRAG_TYPE = 'FIELD_PALETTE_ITEM';

// ==================== 字段面板 ====================
interface FieldPaletteProps {
  onDragStart?: (entry: ComponentRegistryEntry) => void;
}

const FieldPalette: React.FC<FieldPaletteProps> = ({ onDragStart }) => {
  const categories = useMemo(() => {
    const map = new Map<string, ComponentRegistryEntry[]>();
    for (const entry of COMPONENT_REGISTRY) {
      const list = map.get(entry.category) || [];
      list.push(entry);
      map.set(entry.category, list);
    }
    return map;
  }, []);

  const categoryLabels: Record<string, string> = {
    basic: '基础组件',
    advanced: '高级组件',
    custom: '自定义组件',
  };

  return (
    <Card title="组件面板" size="small" style={{ height: '100%' }}>
      {[...categories.entries()].map(([category, entries]) => (
        <div key={category} style={{ marginBottom: 16 }}>
          <h4 style={{ fontSize: 12, color: '#999', marginBottom: 8 }}>{categoryLabels[category]}</h4>
          {entries.map((entry) => (
            <DraggableFieldItem key={entry.name} entry={entry} onDragStart={onDragStart} />
          ))}
        </div>
      ))}
    </Card>
  );
};

const DraggableFieldItem: React.FC<{ entry: ComponentRegistryEntry; onDragStart?: (e: ComponentRegistryEntry) => void }> = ({ entry, onDragStart }) => {
  const [{ isDragging }, dragRef] = useDrag(() => ({
    type: DRAG_TYPE,
    item: () => {
      onDragStart?.(entry);
      return { ...entry };
    },
    collect: (monitor) => ({ isDragging: monitor.isDragging() }),
  }), [entry]);

  return (
    <div
      ref={dragRef}
      style={{
        padding: '6px 12px', marginBottom: 4, borderRadius: 4,
        background: isDragging ? '#e6f7ff' : '#f5f5f5',
        cursor: 'grab', opacity: isDragging ? 0.5 : 1,
        display: 'flex', alignItems: 'center', gap: 8,
        border: '1px solid #d9d9d9',
      }}
    >
      <DragOutlined /> {entry.label}
    </div>
  );
};

// ==================== 设计画布 ====================
interface DesignCanvasProps {
  schema: JsonSchema;
  onChange: (schema: JsonSchema) => void;
}

const DesignCanvas: React.FC<DesignCanvasProps> = ({ schema, onChange }) => {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [deprecationModal, setDeprecationModal] = useState<{ key: string } | null>(null);

  const [{ isOver }, dropRef] = useDrop(() => ({
    accept: DRAG_TYPE,
    drop: (item: ComponentRegistryEntry) => {
      const key = `field_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
      const newSchema: JsonSchema = {
        ...schema,
        properties: {
          ...schema.properties,
          [key]: { ...item.defaultSchema, title: `${item.label}_${Object.keys(schema.properties).length + 1}` },
        },
      };
      onChange(newSchema);
    },
    collect: (monitor) => ({ isOver: monitor.isOver() }),
  }), [schema]);

  const fields = Object.entries(schema.properties);

  const updateField = useCallback((key: string, updates: Partial<FieldSchema>) => {
    onChange({
      ...schema,
      properties: {
        ...schema.properties,
        [key]: { ...schema.properties[key], ...updates },
      },
    });
  }, [schema, onChange]);

  const removeField = useCallback(async (key: string) => {
    // 先检查是否被规则引用
    try {
      const check = await checkFieldDeprecation('MEMBER', key);
      if (!check.safe_to_deprecate) {
        Modal.warning({
          title: '无法删除',
          content: `字段 [${key}] 被以下规则引用:\n${check.referencing_rules.map(r => `${r.rule_code} (v${r.version})`).join('\n')}\n\n请先修改规则后再删除该字段。`,
        });
        return;
      }
    } catch {
      // 检查失败时仍允许标记废弃
    }

    // 标记为 deprecated 而非物理删除
    const updated = { ...schema.properties[key], deprecated: true, deprecated_at: new Date().toISOString() };
    onChange({
      ...schema,
      properties: { ...schema.properties, [key]: updated },
    });
    message.info(`字段 [${key}] 已标记为废弃（前端仅读态展示）`);
  }, [schema, onChange]);

  const isFieldDeprecated = (field: FieldSchema) => field.deprecated === true;

  return (
    <div
      ref={dropRef}
      style={{
        minHeight: 400, padding: 16, borderRadius: 8,
        background: isOver ? '#f0f5ff' : '#fafafa',
        border: `2px dashed ${isOver ? '#1677ff' : '#d9d9d9'}`,
      }}
    >
      {fields.length === 0 && (
        <div style={{ textAlign: 'center', color: '#999', padding: 60 }}>
          <DragOutlined style={{ fontSize: 32 }} />
          <p>从左侧拖拽组件到此处</p>
        </div>
      )}

      {fields.map(([key, field]) => (
        <div
          key={key}
          style={{
            padding: 12, marginBottom: 8, borderRadius: 6,
            background: isFieldDeprecated(field) ? '#fff7e6' : '#fff',
            border: `1px solid ${isFieldDeprecated(field) ? '#faad14' : '#e8e8e8'}`,
            opacity: isFieldDeprecated(field) ? 0.7 : 1,
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>
              <strong>{field.title}</strong>
              <Tag style={{ marginLeft: 8 }} color="blue">{field['x-component'] || field.type}</Tag>
              {field.required && <Tag color="red">必填</Tag>}
              {isFieldDeprecated(field) && <Tag color="orange">已废弃</Tag>}
              {field['x-reactions'] && <Tooltip title={field['x-reactions']}><CodeOutlined /></Tooltip>}
            </span>
            <Space>
              <Button size="small" onClick={() => setEditingKey(key === editingKey ? null : key)}>
                {key === editingKey ? '收起' : '配置'}
              </Button>
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeField(key)} />
            </Space>
          </div>

          {editingKey === key && (
            <div style={{ marginTop: 12, padding: 12, background: '#fafafa', borderRadius: 4 }}>
              <Form layout="vertical" size="small">
                <Form.Item label="字段标题">
                  <Input value={field.title} onChange={(e) => updateField(key, { title: e.target.value })} />
                </Form.Item>
                <Form.Item label="必填">
                  <Switch checked={field.required} onChange={(v) => updateField(key, { required: v })} />
                </Form.Item>
                <Form.Item label="联动表达式 (x-reactions)">
                  <Input.TextArea
                    rows={3}
                    value={field['x-reactions'] || ''}
                    onChange={(e) => updateField(key, { 'x-reactions': e.target.value || undefined })}
                    placeholder='{{ $self.visible = ($deps[0] === "dog") }}'
                  />
                </Form.Item>
                <Form.Item label="联动依赖字段 (x-dependencies)">
                  <Select
                    mode="tags"
                    value={field['x-dependencies'] || []}
                    onChange={(vals) => updateField(key, { 'x-dependencies': vals })}
                    placeholder="选择依赖字段"
                    options={fields.filter(([k]) => k !== key).map(([k, f]) => ({ label: f.title, value: k }))}
                  />
                </Form.Item>
                {field.type === 'string' && (
                  <Form.Item label="枚举选项">
                    <Select
                      mode="tags"
                      value={field.enum?.map(e => `${e.label}:${e.value}`) || []}
                      onChange={(vals) => updateField(key, {
                        enum: vals.map((v: string) => { const [label, value] = v.split(':'); return { label, value }; }),
                      })}
                      placeholder="格式: 标签:值"
                    />
                  </Form.Item>
                )}
              </Form>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

// ==================== SchemaBuilder 主组件 ====================
export interface SchemaBuilderProps {
  /** 实体类型（MEMBER / CUSTOM_ENTITY 等） */
  entityType: string;
  /** 初始 Schema（编辑已有时传入） */
  initialSchema?: JsonSchema;
  /** 保存成功回调 */
  onSave?: (schema: JsonSchema) => void;
}

const SchemaBuilder: React.FC<SchemaBuilderProps> = ({ entityType, initialSchema, onSave }) => {
  const [schema, setSchema] = useState<JsonSchema>(
    initialSchema || { type: 'object', properties: {} }
  );
  const [saving, setSaving] = useState(false);
  const [jsonPreview, setJsonPreview] = useState(false);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      await saveSchema(entityType, schema);
      message.success('Schema 保存成功！');
      onSave?.(schema);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '保存失败';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  }, [schema, entityType, onSave]);

  const exportJson = useCallback(() => {
    return JSON.stringify(schema, null, 2);
  }, [schema]);

  const fieldCount = Object.keys(schema.properties).length;
  const deprecatedCount = Object.values(schema.properties).filter((f) => f.deprecated).length;

  return (
    <DndProvider backend={HTML5Backend}>
      <div style={{ display: 'flex', gap: 16, padding: 16 }}>
        {/* 左栏：组件面板 */}
        <div style={{ width: 200, flexShrink: 0 }}>
          <FieldPalette />
        </div>

        {/* 中栏：设计画布 */}
        <div style={{ flex: 1 }}>
          <Card
            title={`Schema 设计器 —— ${entityType}`}
            extra={
              <Space>
                <Tag>{fieldCount} 个字段</Tag>
                {deprecatedCount > 0 && <Tag color="orange">{deprecatedCount} 个已废弃</Tag>}
                <Button icon={<CodeOutlined />} onClick={() => setJsonPreview(!jsonPreview)}>
                  {jsonPreview ? '隐藏' : '预览'} JSON
                </Button>
                <Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={saving}>
                  保存 Schema
                </Button>
              </Space>
            }
          >
            {jsonPreview ? (
              <pre style={{ background: '#1e1e1e', color: '#d4d4d4', padding: 16, borderRadius: 8, overflow: 'auto', maxHeight: 500 }}>
                {exportJson()}
              </pre>
            ) : (
              <DesignCanvas schema={schema} onChange={setSchema} />
            )}
          </Card>

          {deprecatedCount > 0 && (
            <Alert
              style={{ marginTop: 16 }}
              message="废弃字段提醒"
              description={`存在 ${deprecatedCount} 个已废弃字段。已废弃字段保存后不会从数据库删除，仅在「只读态」展示、「编辑态」隐藏。`}
              type="warning"
              showIcon
              icon={<ExclamationCircleOutlined />}
            />
          )}
        </div>
      </div>
    </DndProvider>
  );
};

export default SchemaBuilder;