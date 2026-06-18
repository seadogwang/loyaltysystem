import React, { useState, useEffect } from 'react';
import { Input, Select, Switch, Button, Typography, Space, Tag, message, Segmented, Spin, Tabs, Tooltip, Empty, Checkbox } from 'antd';
import { PlusOutlined, SaveOutlined, SendOutlined, ReloadOutlined, ImportOutlined, MenuOutlined, CaretRightOutlined, CaretDownOutlined } from '@ant-design/icons';
import api from '../api';

const { Text } = Typography;

// ==================== 类型 ====================

interface SchemaField {
  key: string; type: string; title: string; required?: boolean; primaryKey?: boolean;
  xComponent?: string; xReactions?: string; xDependencies?: string[]; deprecated?: boolean;
  children?: SchemaField[]; arrayItem?: SchemaField; indent: number;
  description?: string; format?: string; enumValues?: string[]; example?: string;
}

// Java 类型
const PG_TYPES = [
  { label: 'String',  jsonType: 'string',  color: '#16a34a', bg: '#f0fdf4' },
  { label: 'Integer', jsonType: 'integer', color: '#d97706', bg: '#fffbeb' },
  { label: 'Double',  jsonType: 'number',  color: '#2563eb', bg: '#eff6ff' },
  { label: 'Boolean', jsonType: 'boolean', color: '#d946ef', bg: '#fdf4ff' },
  { label: 'JSON',    jsonType: 'object',  color: '#2563eb', bg: '#eff6ff' },
  { label: "String[]", jsonType: "array",  color: "#059669", bg: "#f0fdf4" },
  { label: "Integer[]", jsonType: "array",  color: "#d97706", bg: "#fffbeb" },
  { label: "List", jsonType: "array",  color: "#059669", bg: "#f0fdf4",},
];
const PG_TO_JSON = Object.fromEntries(PG_TYPES.map(t => [t.label, { type: t.jsonType }]));
function jsonToPgType(jsonType, format) {
  const found = PG_TYPES.find(t => t.jsonType === jsonType);
  return found?.label || 'String';
}
function pgJsonType(pgType) {
  return PG_TO_JSON[pgType]?.type || 'string';
}

const COMPONENTS = ['Input', 'NumberPicker', 'Select', 'Switch', 'DatePicker', 'ImageUploader', 'CascadingAddress'];
const DEFAULT_ENTITY_OPTIONS = [
  { label: '会员 (MEMBER)', value: 'MEMBER' },
  { label: '订单 (ORDER)', value: 'ORDER' },
  { label: '订单明细 (OrderItem)', value: 'OrderItem' },
  { label: '交易事件 (TRANSACTION_EVENT)', value: 'TRANSACTION_EVENT' },
  { label: '行为 (BEHAVIOR)', value: 'BEHAVIOR' },
];

// ==================== 默认 Schema ====================

const DEFAULT_SCHEMAS = {
  MEMBER: { type: 'object', properties: {
    pet_name: { type: 'string', title: '宠物名称' },
    pet_type: { type: 'string', title: '宠物类型' },
    dog_breed: { type: 'string', title: '犬种明细', 'x-reactions': "{{ $self.visible = ($deps[0] === 'dog') }}" },
    member_level_index: { type: 'number', title: '会员等级指数' },
  }},
  ORDER: { type: 'object', properties: {
    total_amount: { type: 'number', title: '订单总金额' },
    order_amount: { type: 'number', title: '实付金额' },
    order_id: { type: 'string', title: '订单号' },
    item_count: { type: 'number', title: '商品数量' },
    buyer_nick: { type: 'string', title: '买家昵称' },
    channel: { type: 'string', title: '渠道' },
    trade_status: { type: 'string', title: '交易状态', enum: ['PENDING', 'PAID', 'SHIPPED', 'FINISHED', 'CANCELLED'] },
    trade_time: { type: 'string', title: '交易时间', format: 'date-time' },
    pay_time: { type: 'string', title: '付款时间', format: 'date-time' },
    remark: { type: 'string', title: '备注' },
    member_id: { type: 'string', title: '会员ID' },
    item_category: { type: 'string', title: '商品类目' },
    items: { type: 'array', title: '订单明细', items: { type: 'object', properties: {
      sku: { type: 'string', title: 'SKU' },
      category_id: { type: 'string', title: '类目' },
      price: { type: 'number', title: '单价' },
      quantity: { type: 'number', title: '数量' },
      product_name: { type: 'string', title: '商品名' },
    }}},
  }},
  OrderItem: { type: 'object', properties: {
    sku: { type: 'string', title: 'SKU' },
    category_id: { type: 'string', title: '类目' },
    price: { type: 'number', title: '单价' },
    quantity: { type: 'number', title: '数量' },
    product_name: { type: 'string', title: '商品名' },
  }},
  TRANSACTION_EVENT: { type: 'object', properties: {
    total_amount: { type: 'number', title: '订单总金额' },
    eventType: { type: 'string', title: '事件类型' },
    channel: { type: 'string', title: '渠道' },
  }},
  BEHAVIOR: { type: 'object', properties: {
    action: { type: 'string', title: '行为动作' },
    eventType: { type: 'string', title: '事件类型' },
    timestamp: { type: 'string', title: '时间戳', format: 'date-time' },
  }},
};

// ==================== Schema 解析/序列化 ====================

function parseSchemaToFields(schema, indent) {
  if (indent === undefined) indent = 0;
  if (!schema?.properties) return [];
  const r = [];
  for (const [key, val] of Object.entries(schema.properties)) {
    const jsonType = val.type || 'string';
    const pgType = jsonToPgType(jsonType, val.format);
    const f = {
      key, type: pgType, title: val.title || val.description || '',
      required: schema.required?.includes(key), xComponent: val['x-component'],
      xReactions: val['x-reactions'], deprecated: val.deprecated, indent,
      description: val.description, format: val.format,
      enumValues: val.enum, example: val.example,
    };
    if (jsonType === 'object' && val.properties) f.children = parseSchemaToFields(val, indent + 1);
    if (jsonType === 'array' && val.items?.properties) {
      f.arrayItem = { key: 'item', type: 'String', title: '', indent: indent + 1, children: parseSchemaToFields(val.items, indent + 2) };
    }
    r.push(f);
  }
  return r;
}

function fieldsToSchema(fields) {
  const s = { type: 'object', properties: {} };
  const required = [];
  for (const f of fields) {
    const jsonDef = PG_TO_JSON[f.type] || { type: 'string' };
    const p = { type: jsonDef.type, title: f.title };
    if (jsonDef.format) p.format = jsonDef.format;
    if (f.format && !jsonDef.format) p.format = f.format;
    if (f.xComponent) p['x-component'] = f.xComponent;
    if (f.xReactions) p['x-reactions'] = f.xReactions;
    if (f.xDependencies?.length) p['x-dependencies'] = f.xDependencies;
    if (f.deprecated) p.deprecated = true;
    if (f.description) p.description = f.description;
    if (f.enumValues?.length) p.enum = f.enumValues;
    if (f.example) p.example = f.example;
    if (f.required) required.push(f.key);
    if (jsonDef.type === 'object' && f.children) p.properties = fieldsToSchema(f.children).properties;
    if (jsonDef.type === 'array' && f.arrayItem) p.items = { type: 'object', properties: fieldsToSchema(f.arrayItem.children || []).properties };
    s.properties[f.key] = p;
  }
  if (required.length) s.required = required;
  return s;
}

function getEntityLabel(options, value) {
  return options.find(e => e.value === value)?.label || value;
}

// ==================== 行组件 ====================

const SchemaNodeRow = React.memo(({ field, allFields, onUpdate, onDelete, onAddSibling, depth, isArrayItem, dragKey, onDragStart, onDrop }) => {
  const [expanded, setExpanded] = useState(true);
  const [hovered, setHovered] = useState(false);
  const jsonCat = pgJsonType(field.type);
  const hasChildren = jsonCat === "object" || jsonCat === "array" || !!field.children?.length || !!field.arrayItem;
  const canExpand = hasChildren && (field.children?.length || field.arrayItem);
  const iconBtn = { border: 'none', background: 'transparent', cursor: 'pointer', width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: 4, color: '#999', fontSize: 14, padding: 0, lineHeight: 1, flexShrink: 0 };

  const handleFieldChange = (k, v) => onUpdate(field.key, { ...field, [k]: v });

  const inputStyle = (baseStyle = {}) => ({
    ...baseStyle,
    background: hovered ? '#f9fafb' : 'transparent',
    border: hovered ? '1px solid #d1d5db' : '1px solid transparent',
    borderRadius: 4,
    transition: 'all 0.15s',
  });

  // 拖拽
  const handleDragStart = (e) => { e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', field.key); onDragStart?.(field.key); };
  const handleDragOver = (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; };
  const handleDrop = (e) => { e.preventDefault(); onDrop?.(field.key); };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', paddingLeft: depth * 16, borderBottom: '1px solid #d1d5db', minHeight: 40, background: dragKey === field.key ? '#e6f4ff' : '#fff', fontSize: 12 }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
        draggable={!isArrayItem && !!onDragStart}
        onDragStart={!isArrayItem && onDragStart ? handleDragStart : undefined}
        onDragOver={!isArrayItem && onDrop ? handleDragOver : undefined}
        onDrop={!isArrayItem && onDrop ? handleDrop : undefined}
        onDragEnd={() => onDragStart?.('')}
      >
        {/* 列1: 拖拽/展开/字段名 */}
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
          <span style={{ ...iconBtn, cursor: 'grab', color: dragKey === field.key ? '#1677ff' : '#b0b0b0', fontSize: 14 }}><MenuOutlined /></span>
          <span style={{ ...iconBtn, color: '#999' }} onClick={() => canExpand && setExpanded(!expanded)} title={expanded ? '折叠' : '展开'}>
            {canExpand ? (expanded ? <CaretDownOutlined style={{ fontSize: 10 }} /> : <CaretRightOutlined style={{ fontSize: 10 }} />) : <span style={{ width: 12 }} />}
          </span>
          {isArrayItem && <span style={{ fontSize: 9, fontWeight: 700, color: '#8b5cf6', background: '#f5f3ff', padding: '0 4px', borderRadius: 3, lineHeight: '16px' }}>ITEMS</span>}
          <Input size="small" value={field.key} onChange={e => handleFieldChange('key', e.target.value)} placeholder="" variant="borderless"
            style={inputStyle({ fontFamily: 'monospace', fontSize: 12, fontWeight: 500, padding: '1px 4px', height: 26 })} />
        </div>

        {/* 列2: 类型 */}
        <div style={{ width: 100, flexShrink: 0 }}>
          <Select size="small" value={field.type} onChange={v => handleFieldChange('type', v)} variant="borderless" style={{ width: '100%' }}
            dropdownStyle={{ minWidth: 140 }}
            options={PG_TYPES.map(t => ({ label: <span style={{ color: t.color, fontWeight: 500, fontSize: 12 }}>{t.label}</span>, value: t.label }))} />
        </div>

        {/* 列3: 主键 */}
        <div style={{ width: 50, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Checkbox checked={field.primaryKey} onChange={e => handleFieldChange('primaryKey', e.target.checked)} style={{ transform: 'scale(0.75)' }} />
        </div>

        {/* 列4: 非空 */}
        <div style={{ width: 50, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Checkbox checked={field.required} onChange={e => handleFieldChange('required', e.target.checked)} style={{ transform: 'scale(0.75)' }} />
        </div>

        {/* 列5: 中文名 */}
        <div style={{ width: 110, flexShrink: 0 }}>
          <Input size="small" value={field.title} onChange={e => handleFieldChange('title', e.target.value)} placeholder="中文名" variant="borderless"
            style={inputStyle({ fontSize: 12, padding: '1px 4px', height: 26 })} />
        </div>

        {/* 列6: 描述 */}
        <div style={{ width: 100, flexShrink: 0 }}>
          <Input size="small" value={field.description || ''} onChange={e => handleFieldChange('description', e.target.value || undefined)} placeholder="描述..." variant="borderless"
            style={inputStyle({ fontSize: 11, padding: '1px 4px', height: 26 })} />
        </div>

        {/* 列7: 操作 */}
        <div style={{ width: 56, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 4, justifyContent: 'center' }}>
          <Tooltip title="添加字段">
            <button onClick={() => onAddSibling(field.key)} style={{ width: 15, height: 15, borderRadius: '50%', border: '1px solid #000', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#000', padding: 0, lineHeight: 1 }}>
              <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
            </button>
          </Tooltip>
          <Tooltip title="删除字段">
            <button onClick={() => onDelete(field.key)} style={{ width: 15, height: 15, borderRadius: '50%', border: '1px solid #000', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#000', padding: 0, lineHeight: 1 }}>
              <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"><line x1="5" y1="12" x2="19" y2="12" /></svg>
            </button>
          </Tooltip>
        </div>
      </div>

      {/* 子节点 */}
      {expanded && (
        <div>
          {field.children?.map(child => (
            <SchemaNodeRow key={child.key} field={child} allFields={allFields} onUpdate={onUpdate} onDelete={onDelete}
              onAddSibling={(childKey) => {
                const siblings = field.children || [];
                const idx = siblings.findIndex(c => c.key === childKey);
                if (idx >= 0) {
                  const newChild = { key: 'new_field_' + Date.now(), type: 'String', title: '', indent: 0 };
                  const updated = [...siblings];
                  updated.splice(idx + 1, 0, newChild);
                  onUpdate(field.key, { ...field, children: updated });
                }
              }}
              depth={depth + 1} />
          ))}
          {field.arrayItem && (
            <SchemaNodeRow key={field.arrayItem.key} field={field.arrayItem} allFields={allFields} onUpdate={onUpdate} onDelete={onDelete}
              isArrayItem={true}
              onAddSibling={(childKey) => {
                const siblings = field.arrayItem.children || [];
                const idx = siblings.findIndex(c => c.key === childKey);
                if (idx >= 0) {
                  const newChild = { key: 'new_field_' + Date.now(), type: 'String', title: '', indent: 0 };
                  const updated = [...siblings];
                  updated.splice(idx + 1, 0, newChild);
                  onUpdate(field.key, { ...field, arrayItem: { ...field.arrayItem, children: updated } });
                }
              }}
              depth={depth + 1} />
          )}
          {hasChildren && !field.children?.length && !field.arrayItem && (() => {
            setTimeout(() => {
              const blank = { key: 'field_' + Date.now(), type: 'String', title: '', indent: 0 };
              onUpdate(field.key, { ...field, children: [blank] });
            }, 0);
            return null;
          })()}
        </div>
      )}
    </div>
  );
});

// ==================== 导入 JSON Modal ====================

const ImportJsonModal = ({ open, onClose, onImport }) => {
  const [jsonText, setJsonText] = useState('');
  const [error, setError] = useState('');
  const [importMode, setImportMode] = useState('auto');

  const guessType = (val) => {
    if (val === null || val === undefined) return 'string';
    if (Array.isArray(val)) return 'array';
    const t = typeof val;
    if (t === 'number') return Number.isInteger(val) ? 'integer' : 'number';
    if (t === 'boolean') return 'boolean';
    if (t === 'object') return 'object';
    return 'string';
  };

  const handleImport = () => {
    setError('');
    try {
      const parsed = JSON.parse(jsonText);
      let properties = {};
      if (parsed.properties) {
        properties = parsed.properties;
      } else if (Array.isArray(parsed)) {
        const sample = parsed[0] || {};
        for (const [k, v] of Object.entries(sample)) properties[k] = { type: guessType(v), title: k };
      } else if (typeof parsed === 'object') {
        for (const [k, v] of Object.entries(parsed)) properties[k] = { type: guessType(v), title: k };
      }
      if (!Object.keys(properties).length) { setError('未能解析出任何字段'); return; }
      onImport(parseSchemaToFields({ type: 'object', properties }));
      message.success('导入成功');
      setJsonText('');
      onClose();
    } catch (e) { setError('JSON 格式错误: ' + e.message); }
  };

  return (
    <div style={{ display: 'none' }}>{/* Placeholder - modal not needed for MVP */}</div>
  );
};

// ==================== 主组件 ====================

const SchemaEditor = () => {
  const [entityType, setEntityType] = useState('ORDER');
  const [entityOptions, setEntityOptions] = useState(DEFAULT_ENTITY_OPTIONS);
  const [fields, setFields] = useState(() => parseSchemaToFields(DEFAULT_SCHEMAS['ORDER']));
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState('fields');
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [dragKey, setDragKey] = useState(null);

  useEffect(() => {
    if (DEFAULT_SCHEMAS[entityType]) {
      loadSchema(entityType);
    } else {
      const blank = { key: 'field_1', type: 'String', title: '', indent: 0 };
      setFields([blank]);
    }
  }, [entityType]);

  const loadSchema = async (ent) => {
    // 优先用 DEFAULT_SCHEMAS，确保内容始终展示
    if (DEFAULT_SCHEMAS[ent]) {
      setFields(parseSchemaToFields(DEFAULT_SCHEMAS[ent]));
    }
    setLoading(true);
    try {
      const { data } = await api.get(`/schemas/${ent}`);
      const schema = data?.data?.schema || data?.data;
      if (schema?.properties && Object.keys(schema.properties).length > 0) {
        setFields(parseSchemaToFields(schema));
      }
    } catch (e) {
      console.log("loadSchema error for", ent, e);
    } finally { setLoading(false); }
  };

  const handleAddEntity = () => {
    const n = entityOptions.length + 1;
    const value = 'ENTITY_' + n;
    const blank = { key: 'field_1', type: 'String', title: '', indent: 0 };
    setEntityOptions(prev => [...prev, { label: '新实体' + n, value }]);
    setFields([blank]);
    setEntityType(value);
  };

  const handleDeleteEntity = (value) => {
    const remaining = entityOptions.filter(opt => opt.value !== value);
    setEntityOptions(remaining);
    if (entityType === value && remaining.length > 0) setEntityType(remaining[0].value);
    else if (remaining.length === 0) { setEntityType(''); setFields([]); }
  };

  const addField = () => {
    const f = { key: 'new_field_' + Date.now(), type: 'String', title: '', indent: 0 };
    setFields([...fields, f]);
  };

  const handleAddSibling = (afterKey) => {
    const newField = { key: 'new_field_' + Date.now(), type: 'String', title: '', indent: 0 };
    const insertAfter = (list) => {
      const idx = list.findIndex(f => f.key === afterKey);
      if (idx >= 0) { const r = [...list]; r.splice(idx + 1, 0, newField); return r; }
      return list.map(f => ({ ...f, children: f.children ? insertAfter(f.children) : f.children }));
    };
    setFields(prev => insertAfter(prev));
  };

  const handleUpdateField = (oldKey, newField) => {
    const updater = (list) => list.map(f => {
      if (f.key === oldKey) return newField;
      const result = { ...f };
      if (f.children) result.children = updater(f.children);
      if (f.arrayItem) {
        if (f.arrayItem.key === oldKey) result.arrayItem = newField;
        else if (f.arrayItem.children) result.arrayItem = { ...f.arrayItem, children: updater(f.arrayItem.children) };
      }
      return result;
    });
    setFields(prev => updater(prev));
  };

  const handleDeleteField = (k) => {
    const deleter = (list) => list.filter(f => {
      if (f.key === k) return false;
      if (f.children) f.children = deleter(f.children);
      if (f.arrayItem) {
        if (f.arrayItem.key === k) return false;
        if (f.arrayItem.children) f.arrayItem.children = deleter(f.arrayItem.children);
      }
      return true;
    });
    setFields(prev => deleter(prev));
  };

  const moveField = (fromKey, targetKey) => {
    if (fromKey === targetKey) return;
    const updated = [...fields];
    const fromIdx = updated.findIndex(f => f.key === fromKey);
    const toIdx = updated.findIndex(f => f.key === targetKey);
    if (fromIdx < 0 || toIdx < 0) return;
    const [moved] = updated.splice(fromIdx, 1);
    const adjustedTo = updated.findIndex(f => f.key === targetKey);
    updated.splice(adjustedTo, 0, moved);
    setFields(updated);
  };

  const handleSave = async () => {
    setSaving(true);
    try { await api.post('/admin/schemas', { entityType, schema: fieldsToSchema(fields), version: 'v1' }); message.success('已保存'); }
    catch (e) { message.error(e?.message || '保存失败'); }
    finally { setSaving(false); }
  };

  const handlePublish = async () => {
    setSaving(true);
    try { await api.post('/admin/schemas/publish', { entityType, schema: fieldsToSchema(fields) }); message.success('已发布'); }
    catch (e) { message.error(e?.message || '发布失败'); }
    finally { setSaving(false); }
  };

  const handleImportFields = (imported) => {
    const existing = new Set(fields.map(f => f.key));
    const newFields = imported.filter(f => !existing.has(f.key));
    if (!newFields.length) { message.warning('所有字段已存在'); return; }
    setFields([...fields, ...newFields]);
    message.success('新增 ' + newFields.length + ' 个字段');
  };

  const currentSchema = fieldsToSchema(fields);
  const entityLabel = getEntityLabel(entityOptions, entityType);
  const tabBarExtra = (
    <Space size="small" style={{ paddingRight: 12 }}>
      <Tooltip title="从 JSON 导入"><Button size="small" icon={<ImportOutlined />} onClick={() => setImportModalOpen(true)} style={{ fontSize: 11 }}>导入 JSON</Button></Tooltip>
      <Button size="small" icon={<SaveOutlined />} onClick={handleSave} loading={saving} style={{ fontSize: 11 }}>保存</Button>
      <Button size="small" type="primary" icon={<SendOutlined />} onClick={handlePublish} loading={saving} style={{ fontSize: 11 }}>发布</Button>
    </Space>
  );

  const tabItems = [
    { key: 'fields', label: <Space size={4}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /></svg><span>{entityLabel}</span></Space>,
      children: (
        <div className="schema-editor-fields">
          {/* 表头 */}
          <div style={{ display: 'flex', alignItems: 'center', height: 34, borderBottom: '2px solid #d1d5db', background: '#f0f0f0', fontSize: 11, fontWeight: 600, color: '#374151' }}>
            <div style={{ flex: 1, paddingLeft: 36 }}>字段名</div>
            <div style={{ width: 100, flexShrink: 0 }}>类型</div>
            <div style={{ width: 50, flexShrink: 0, textAlign: 'center' }}>主键</div>
            <div style={{ width: 50, flexShrink: 0, textAlign: 'center' }}>非空</div>
            <div style={{ width: 110, flexShrink: 0 }}>中文名</div>
            <div style={{ width: 100, flexShrink: 0 }}>描述</div>
            <div style={{ width: 56, flexShrink: 0 }}>操作</div>
          </div>

          {loading ? <Spin style={{ margin: '40px auto' }} /> : fields.length === 0 ? <Empty description="暂无字段" style={{ margin: 40 }} /> : (
            <div className="schema-editor-list">
              {fields.map(f => (
                <SchemaNodeRow key={f.key} field={f} allFields={fields} onUpdate={handleUpdateField} onDelete={handleDeleteField}
                  onAddSibling={handleAddSibling} depth={0} dragKey={dragKey} onDragStart={setDragKey}
                  onDrop={(targetKey) => { moveField(f.key, targetKey); setDragKey(null); }} />
              ))}
            </div>
          )}
        </div>
      ),
    },
    { key: "schema", label: <Space size={4}><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" /></svg><span>JSON Schema</span></Space>,
      children: (
        <div style={{ height: "100%", padding: 12, overflow: "auto" }}>
          <pre style={{ background: "#f6f8fa", border: "1px solid #e5e7eb", borderRadius: 8, padding: 16, fontSize: 12, fontFamily: "monospace", overflow: "auto", minHeight: "calc(100vh - 200px)" }}>{JSON.stringify(currentSchema, null, 2)}</pre>
        </div>
      ),
    },
  ];

  return (
    <div style={{ display: 'flex', minHeight: 'calc(100vh - 56px - 32px - 48px)', background: '#fff' }}>
      <style>{`
        .schema-editor-left { display: flex; flex-direction: column; }
        .schema-editor-tabs { flex: 1; min-width: 0; }
        .schema-editor-tabs .ant-tabs-content-holder { flex: 1; }
        .schema-editor-tabs .ant-tabs-tabpane-active { display: flex !important; flex-direction: column !important; }
        .schema-editor-fields { flex: 1; display: flex; flex-direction: column; }
        .schema-editor-list { flex: 1; }
      `}</style>
      {/* 左侧实体目录 */}
      <div className="schema-editor-left" style={{ width: 200, minWidth: 200, borderRight: '1px solid #e5e7eb', background: '#fff' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', borderBottom: '1px solid #d1d5db', fontSize: 14, fontWeight: 600, color: '#1f2937', minHeight: 40 }}>
          <span>业务实体</span>
          <button onClick={handleAddEntity} style={{ width: 15, height: 15, borderRadius: '50%', border: '1px solid #000', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#000', padding: 0, lineHeight: 1 }} title="新增实体">
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
          </button>
        </div>
        <div className="schema-editor-list">
          {entityOptions.map(e => (
            <div key={e.value} onClick={() => setEntityType(e.value)}
              style={{ display: 'flex', alignItems: 'center', padding: '8px 14px 8px 21px', cursor: 'pointer', fontSize: 14, color: '#374151', fontWeight: 400, borderLeft: entityType === e.value ? '3px solid #1677ff' : '3px solid transparent', background: entityType === e.value ? '#fafafa' : 'transparent' }}>
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.label}</span>
              {DEFAULT_ENTITY_OPTIONS.findIndex(d => d.value === e.value) < 0 && (
                <button onClick={(ev) => { ev.stopPropagation(); handleDeleteEntity(e.value); }} style={{ width: 15, height: 15, borderRadius: '50%', border: '1px solid #000', background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#000', padding: 0, lineHeight: 1, flexShrink: 0 }} title="删除实体">
                  <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                </button>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* 右侧内容区 */}
        <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems}
          className="schema-editor-tabs"
          tabBarStyle={{ paddingLeft: 16, marginBottom: 0, fontSize: 12 }} size="small"
          tabBarExtraContent={tabBarExtra} />
    </div>
  );
};

export default SchemaEditor;
