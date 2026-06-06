import React, { useState } from 'react';
import { Input, Select, Switch, Button, Typography, Space, Tag, message, Segmented } from 'antd';
import { PlusOutlined, DeleteOutlined, SaveOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface SchemaField {
  key: string; type: string; title: string; required?: boolean;
  xComponent?: string; xReactions?: string; xDependencies?: string[]; deprecated?: boolean;
  children?: SchemaField[]; arrayItem?: SchemaField; indent: number;
}

const FIELD_TYPES = ['string','number','integer','boolean','object','array'];
const COMPONENTS = ['Input','NumberPicker','Select','Switch','DatePicker','ImageUploader','CascadingAddress'];
const OPERATORS = [
  { label: '等于 (===)', value: '===' }, { label: '不等于 (!==)', value: '!==' },
  { label: '大于 (>)', value: '>' }, { label: '小于 (<)', value: '<' },
  { label: '包含 (includes)', value: 'includes' },
];
const EFFECTS = [
  { label: '显示 (visible)', value: 'show' }, { label: '隐藏 (hidden)', value: 'hide' },
  { label: '必填 (required)', value: 'require' }, { label: '非必填 (optional)', value: 'optional' },
];

interface LinkageRule { id: string; dependField: string; operator: string; compareValue: string; effect: string; }

function parseSchemaToFields(schema: any, indent=0): SchemaField[] {
  if (!schema?.properties) return [];
  const r: SchemaField[] = [];
  for (const [key, val] of Object.entries(schema.properties) as [string, any][]) {
    const f: SchemaField = {
      key, type: val.type||'string', title: val.title||val.description||'',
      required: schema.required?.includes(key), xComponent: val['x-component'],
      xReactions: val['x-reactions'], deprecated: val.deprecated, indent,
    };
    if (val.type==='object'&&val.properties) f.children = parseSchemaToFields(val, indent+1);
    if (val.type==='array'&&val.items?.properties) {
      f.arrayItem = { key:'item', type:'object', title:'', indent:indent+1, children:parseSchemaToFields(val.items, indent+2) };
    }
    r.push(f);
  }
  return r;
}

function fieldsToSchema(fields: SchemaField[]): any {
  const s: any = { type:'object', properties:{} }; const required: string[] = [];
  for (const f of fields) {
    const p: any = { type:f.type, title:f.title };
    if (f.xComponent) p['x-component']=f.xComponent;
    if (f.xReactions) p['x-reactions']=f.xReactions;
    if (f.xDependencies?.length) p['x-dependencies']=f.xDependencies;
    if (f.deprecated) p.deprecated=true;
    if (f.required) required.push(f.key);
    if (f.type==='object'&&f.children) p.properties=fieldsToSchema(f.children).properties;
    if (f.type==='array'&&f.arrayItem) p.items={type:'object',properties:fieldsToSchema(f.arrayItem.children||[]).properties};
    s.properties[f.key]=p;
  }
  if (required.length) s.required=required;
  return s;
}

function parseReactions(rx?: string): LinkageRule[] {
  if (!rx) return [];
  const rules: LinkageRule[] = [];
  const re = /\$self\.(\w+)\s*=\s*\(\$deps\[(\d+)\]\s*([=!<>]+|!==)\s*['"]?([^'")\]]+)['"]?\s*\)/g;
  let m; let i=0;
  while ((m=re.exec(rx))!==null) {
    const effect = m[1]==='visible' ? (rx.includes('!==')||rx.includes('!=')?'hide':'show') :
      m[1]==='required' ? 'require' : 'optional';
    rules.push({ id:`lr_${i++}`, dependField:'', operator:m[3], compareValue:m[4], effect });
  }
  return rules;
}

function rulesToReactions(rules: LinkageRule[], allFields: SchemaField[]): string {
  if (!rules.length) return '';
  const parts = rules.map((r, i) => {
    const ref = `$deps[${i}]`;
    const val = isNaN(Number(r.compareValue)) ? `'${r.compareValue}'` : r.compareValue;
    if (r.effect==='show') return `$self.visible = (${ref} ${r.operator} ${val})`;
    if (r.effect==='hide') return `$self.visible = (${ref} ${r.operator} ${val})`;
    if (r.effect==='require') return `$self.required = (${ref} ${r.operator} ${val})`;
    return `$self.required = !(${ref} ${r.operator} ${val})`;
  });
  return `{{ ${parts.join(' && ')} }}`;
}

/** 从 rules 生成 x-dependencies 数组 */
function rulesToDeps(rules: LinkageRule[]): string[] {
  return rules.filter(r => r.dependField).map(r => r.dependField);
}

// ==================== 字段行 ====================

const FieldRow: React.FC<{
  field: SchemaField; onUpdate: (f: SchemaField) => void;
  onDelete: (k: string) => void; onSelect: (f: SchemaField) => void;
  selected: boolean; level?: number;
}> = ({ field, onUpdate, onDelete, onSelect, selected, level=0 }) => (
  <>
    <div onClick={()=>onSelect(field)} style={{
      display:'flex',alignItems:'center',gap:6,padding:'4px 8px 4px '+(16+level*20)+'px',
      cursor:'pointer',background:selected?'#f0f5ff':'transparent',
      borderBottom:'1px solid #f5f5f5',fontSize:12,
    }}>
      <span style={{ fontFamily:'monospace',color:'#1a1a1a',fontWeight:500,minWidth:60 }}>{field.key}</span>
      <Tag color="blue" style={{ fontSize:10 }}>{field.type}</Tag>
      {field.title && <Text type="secondary" style={{ fontSize:11,flex:1,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap' }}>{field.title}</Text>}
      {field.required && <Text type="danger" style={{ fontSize:10 }}>*</Text>}
      {field.deprecated && <Tag color="default" style={{ fontSize:9 }}>废</Tag>}
      {field.xReactions && <Tag color="orange" style={{ fontSize:9 }}>联动</Tag>}
      <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={e=>{e.stopPropagation();onDelete(field.key)}} style={{ height:20 }} />
    </div>
    {field.children?.map(c=><FieldRow key={c.key} field={c} onUpdate={onUpdate} onDelete={onDelete} onSelect={onSelect} selected={selected} level={level+1} />)}
  </>
);

// ==================== 属性面板 (含联动构建器) ====================

const SchemaPropPanel: React.FC<{
  field: SchemaField|null; allFields: SchemaField[]; onUpdate: (f:SchemaField)=>void;
}> = ({ field, allFields, onUpdate }) => {
  const [mode, setMode] = useState<'visual'|'raw'>('visual');
  if (!field) return (
    <div style={{ width:320,padding:24,borderLeft:'1px solid #f0f0f0',display:'flex',alignItems:'center',justifyContent:'center' }}>
      <Text type="secondary" style={{ fontSize:12 }}>点击左侧字段配置属性</Text>
    </div>
  );

  const rules = parseReactions(field.xReactions);
  const otherFields = allFields.filter(f=>f.key!==field.key);
  const raw = field.xReactions||'';

  const addRule = () => {
    const r: LinkageRule = { id:`lr_${Date.now()}`,dependField:otherFields[0]?.key||'',operator:'===',compareValue:'',effect:'show' };
    const u = [...rules,r];
    onUpdate({...field,xReactions:rulesToReactions(u,allFields),xDependencies:rulesToDeps(u)});
  };
  const upRule = (id:string,k:keyof LinkageRule,v:string) => {
    const u = rules.map(r=>r.id===id?{...r,[k]:v}:r);
    onUpdate({...field,xReactions:rulesToReactions(u,allFields),xDependencies:rulesToDeps(u)});
  };
  const delRule = (id:string) => {
    const u = rules.filter(r=>r.id!==id);
    onUpdate({...field,xReactions:u.length?rulesToReactions(u,allFields):undefined,xDependencies:u.length?rulesToDeps(u):undefined});
  };

  return (
    <div style={{ width:320,borderLeft:'1px solid #f0f0f0',overflow:'auto',display:'flex',flexDirection:'column' }}>
      {/* 基础属性 */}
      <div style={{ padding:14,borderBottom:'1px solid #f0f0f0' }}>
        <Text strong style={{ fontSize:12 }}>字段: {field.key}</Text>
        <div style={{ marginTop:8 }}>
          <Text type="secondary" style={{ fontSize:10 }}>标识 (key)</Text>
          <Input size="small" value={field.key} onChange={e=>onUpdate({...field,key:e.target.value})} style={{ fontFamily:'monospace',fontSize:11 }} />
        </div>
        <div style={{ marginTop:6 }}>
          <Text type="secondary" style={{ fontSize:10 }}>标题</Text>
          <Input size="small" value={field.title} onChange={e=>onUpdate({...field,title:e.target.value})} style={{ fontSize:11 }} />
        </div>
        <div style={{ marginTop:6 }}>
          <Text type="secondary" style={{ fontSize:10 }}>类型</Text>
          <Select size="small" value={field.type} style={{ width:'100%' }} onChange={v=>onUpdate({...field,type:v})}
            options={FIELD_TYPES.map(t=>({label:t,value:t}))} />
        </div>
        <div style={{ marginTop:6 }}>
          <Text type="secondary" style={{ fontSize:10 }}>x-component</Text>
          <Select size="small" value={field.xComponent||''} style={{ width:'100%' }} allowClear
            onChange={v=>onUpdate({...field,xComponent:v||undefined})}
            options={COMPONENTS.map(c=>({label:c,value:c}))} />
        </div>
        <Space style={{ marginTop:8 }}>
          <Switch size="small" checked={field.required} onChange={v=>onUpdate({...field,required:v})} />
          <Text style={{ fontSize:10 }}>必填</Text>
          <Switch size="small" checked={field.deprecated} onChange={v=>onUpdate({...field,deprecated:v})} />
          <Text style={{ fontSize:10 }}>废弃</Text>
        </Space>
      </div>

      {/* 联动规则 */}
      <div style={{ padding:14,flex:1 }}>
        <div style={{ display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:10 }}>
          <Space>
            <ThunderboltOutlined style={{ color:'#faad14',fontSize:13 }} />
            <Text strong style={{ fontSize:12 }}>联动规则</Text>
          </Space>
          <Segmented size="small" value={mode} onChange={v=>setMode(v as any)}
            options={[{label:'可视化',value:'visual'},{label:'手写',value:'raw'}]} style={{ fontSize:10 }} />
        </div>

        {mode==='visual'?(
          <div>
            {rules.map(r=>(
              <div key={r.id} style={{ border:'1px solid #e8e8e8',borderRadius:6,padding:8,marginBottom:6,background:'#fafafa' }}>
                <div style={{ display:'flex',alignItems:'center',gap:3,marginBottom:3 }}>
                  <Text style={{ fontSize:10,color:'#999',whiteSpace:'nowrap',minWidth:16 }}>当</Text>
                  <Select size="small" value={r.dependField} style={{ flex:1,minWidth:80 }}
                    onChange={v=>upRule(r.id,'dependField',v)}
                    options={otherFields.map(f=>({label:f.key,value:f.key}))} placeholder="字段" />
                  <Select size="small" value={r.operator} style={{ width:110 }}
                    onChange={v=>upRule(r.id,'operator',v)} options={OPERATORS} />
                </div>
                <div style={{ display:'flex',alignItems:'center',gap:3 }}>
                  <Input size="small" value={r.compareValue} style={{ flex:1 }}
                    onChange={e=>upRule(r.id,'compareValue',e.target.value)} placeholder="值" />
                  <Text style={{ fontSize:10,color:'#999',whiteSpace:'nowrap' }}>时</Text>
                  <Select size="small" value={r.effect} style={{ width:130 }}
                    onChange={v=>upRule(r.id,'effect',v)} options={EFFECTS} />
                  <Button size="small" type="text" danger icon={<DeleteOutlined />} onClick={()=>delRule(r.id)} style={{ height:22 }} />
                </div>
              </div>
            ))}
            {rules.length===0 && (
              <div style={{ padding:12,textAlign:'center',color:'#ccc',fontSize:11 }}>暂无联动规则</div>
            )}
            <Button size="small" icon={<PlusOutlined />} onClick={addRule} block style={{ fontSize:11 }}>添加联动</Button>
          </div>
        ):(
          <div>
            <Input.TextArea value={raw} rows={4}
              onChange={e=>onUpdate({...field,xReactions:e.target.value||undefined})}
              placeholder='{{ $self.visible = ($deps[0] === "dog") }}'
              style={{ fontFamily:'monospace',fontSize:11 }} />
            <Text type="secondary" style={{ fontSize:10,display:'block',marginTop:4 }}>
              使用 $self.visible/$self.required + $deps[n]
            </Text>
          </div>
        )}

        {field.xReactions && (
          <div style={{ marginTop:10,padding:8,background:'#f5f5f5',borderRadius:4 }}>
            <Text type="secondary" style={{ fontSize:9,display:'block',marginBottom:2 }}>x-reactions</Text>
            <code style={{ fontSize:10,wordBreak:'break-all',color:'#1a1a1a' }}>{field.xReactions}</code>
            {field.xDependencies?.length && (
              <>
                <Text type="secondary" style={{ fontSize:9,display:'block',marginTop:4,marginBottom:2 }}>x-dependencies</Text>
                <code style={{ fontSize:10,color:'#1677ff' }}>[{field.xDependencies.join(', ')}]</code>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

// ==================== 主组件 ====================

const SchemaEditor: React.FC = () => {
  const [entityType, setEntityType] = useState<'MEMBER'|'TRANSACTION'>('MEMBER');
  const [fields, setFields] = useState<SchemaField[]>(()=>parseSchemaToFields({
    type:'object',properties:{
      pet_name:{type:'string',title:'宠物名称','x-component':'Input'},
      pet_type:{type:'string',title:'宠物类型','x-component':'Select'},
      dog_breed:{type:'string',title:'犬种明细','x-component':'Input',
        'x-reactions':"{{ $self.visible = ($deps[0] === 'dog') }}"},
      member_level_index:{type:'number',title:'会员等级指数','x-component':'NumberPicker'},
    },
  }));
  const [selectedField, setSelectedField] = useState<SchemaField|null>(null);

  const addField = () => {
    const f: SchemaField = { key:`new_field_${Date.now()}`,type:'string',title:'新字段',indent:0 };
    setFields([...fields,f]); setSelectedField(f);
  };

  const updateField = (f: SchemaField) => { setSelectedField(f); setFields([...fields]); };
  const deleteField = (k: string) => { setFields(fields.filter(f=>f.key!==k)); if(selectedField?.key===k) setSelectedField(null); };

  return (
    <div style={{ display:'flex',flex:1,minHeight:'calc(100vh - 120px)',background:'#fff' }}>
      {/* 编辑区 */}
      <div style={{ flex:1,display:'flex',flexDirection:'column',borderRight:'1px solid #f0f0f0' }}>
        <div style={{ padding:'10px 16px',borderBottom:'1px solid #f0f0f0',display:'flex',justifyContent:'space-between' }}>
          <Space>
            <Button type={entityType==='MEMBER'?'primary':'default'} size="small" onClick={()=>setEntityType('MEMBER')} style={{ fontSize:11 }}>会员 (ext_attributes)</Button>
            <Button type={entityType==='TRANSACTION'?'primary':'default'} size="small" onClick={()=>setEntityType('TRANSACTION')} style={{ fontSize:11 }}>交易 (payload)</Button>
            <Button size="small" icon={<PlusOutlined />} style={{ fontSize:11 }}>新建业务实体</Button>
          </Space>
          <Space>
            <Button size="small" icon={<SaveOutlined />} onClick={()=>message.success('已保存')} style={{ fontSize:11 }}>保存草稿</Button>
            <Button size="small" type="primary" icon={<SendOutlined />} onClick={()=>message.success('已发布')} style={{ fontSize:11 }}>发布</Button>
          </Space>
        </div>
        <div style={{ flex:1,overflow:'auto',padding:'8px 0' }}>
          {fields.map(f=><FieldRow key={f.key} field={f} onUpdate={updateField} onDelete={deleteField} onSelect={setSelectedField} selected={selectedField?.key===f.key} />)}
        </div>
        <div style={{ padding:'8px 16px',borderTop:'1px solid #f0f0f0' }}>
          <Button size="small" icon={<PlusOutlined />} onClick={addField} block style={{ fontSize:11 }}>添加字段</Button>
        </div>
      </div>
      {/* 属性面板 */}
      <SchemaPropPanel field={selectedField} allFields={fields} onUpdate={updateField} />
    </div>
  );
};

export default SchemaEditor;