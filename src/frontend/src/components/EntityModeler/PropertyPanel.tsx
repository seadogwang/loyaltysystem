import React from 'react';
import { Drawer, Tabs, Input, Select, Switch, Space, Typography } from 'antd';
import { FIELD_TYPES, FORMLY_COMPONENTS } from './constants';
import type { EntityFieldExt } from './types';

const { Text } = Typography;

interface PropertyPanelProps {
  field: EntityFieldExt | null;
  onChange: (f: EntityFieldExt) => void;
  onClose: () => void;
}

/**
 * 右侧属性面板：选中字段时滑出，配置结构和呈现属性
 */
const PropertyPanel: React.FC<PropertyPanelProps> = ({ field, onChange, onClose }) => {
  if (!field) return null;

  return (
    <Drawer title={`字段: ${field.name}`} open={!!field} onClose={onClose} width={400}>
      <Tabs items={[
        {
          key: 'struct', label: '结构配置', children: (
            <div>
              <div style={{ marginBottom: 10 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>字段名</Text>
                <Input
                  size="small"
                  value={field.name}
                  disabled={field.locked}
                  onChange={e => onChange({ ...field, name: e.target.value })}
                />
              </div>
              <div style={{ marginBottom: 10 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>类型</Text>
                <Select
                  size="small"
                  value={field.type}
                  style={{ width: '100%' }}
                  disabled={field.locked}
                  onChange={v => onChange({ ...field, type: v })}
                  options={FIELD_TYPES.map(t => ({ label: t, value: t }))}
                />
              </div>
              <Space style={{ marginBottom: 10 }}>
                <Switch
                  size="small"
                  checked={field.required}
                  disabled={field.locked}
                  onChange={v => onChange({ ...field, required: v })}
                />
                <Text style={{ fontSize: 11 }}>必填</Text>
                <Switch
                  size="small"
                  checked={field.unique}
                  disabled={field.locked}
                  onChange={v => onChange({ ...field, unique: v })}
                />
                <Text style={{ fontSize: 11 }}>唯一</Text>
              </Space>
              <div style={{ marginBottom: 10 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>默认值</Text>
                <Input
                  size="small"
                  value={field.defaultValue || ''}
                  onChange={e => onChange({ ...field, defaultValue: e.target.value })}
                />
              </div>
            </div>
          ),
        },
        {
          key: 'render', label: '呈现配置', children: (
            <div>
              <div style={{ marginBottom: 10 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>x-component</Text>
                <Select
                  size="small"
                  value={field.xComponent || ''}
                  style={{ width: '100%' }}
                  allowClear
                  onChange={v => onChange({ ...field, xComponent: v || undefined })}
                  options={FORMLY_COMPONENTS.map(c => ({ label: c, value: c }))}
                />
              </div>
              <div style={{ marginBottom: 10 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>x-reactions</Text>
                <Input.TextArea
                  size="small"
                  rows={3}
                  value={field.xReactions || ''}
                  onChange={e => onChange({ ...field, xReactions: e.target.value || undefined })}
                  placeholder='{{ $self.visible = ($deps[0] === "dog") }}'
                />
              </div>
              <div style={{ marginBottom: 10 }}>
                <Switch
                  size="small"
                  checked={field.deprecated}
                  onChange={v => onChange({ ...field, deprecated: v })}
                />
                <Text style={{ fontSize: 11, marginLeft: 8 }}>废弃</Text>
              </div>
            </div>
          ),
        },
      ]} />
    </Drawer>
  );
};

export default PropertyPanel;