import React from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Button, Space } from 'antd';
import { PlusOutlined, DeleteOutlined, SettingOutlined, LockOutlined, KeyOutlined } from '@ant-design/icons';
import { useEntityModeler } from './EntityModelerContext';
import { COLORS, BG_COLORS, BADGES } from './constants';
import type { EntityNodeData, EntityFieldExt } from './types';

/**
 * 自定义 React Flow 节点：数据库实体卡片
 * 外观与 ChartDB 的表节点一致，兼容系统/业务/API 三种实体类型
 */
const EntityNodeComponent: React.FC<NodeProps> = ({ id, data, selected }) => {
  const { selectNode, selectField, deleteNode, openConfig, addField } = useEntityModeler();
  const nodeData = data as unknown as EntityNodeData;
  const { displayName, fields } = nodeData;
  const isSystem = nodeData.kind === 'system';
  const c = COLORS[nodeData.kind];
  const bg = BG_COLORS[nodeData.kind];

  const handleHeaderClick = () => {
    selectNode(id);
  };

  const handleFieldClick = (f: EntityFieldExt) => {
    selectField(f);
  };

  const handleAddField = () => {
    const newField: EntityFieldExt = {
      key: `f_${Date.now()}`,
      name: '新字段',
      type: 'String',
    };
    addField(id, newField);
  };

  return (
    <div style={{
      minWidth: 200, maxWidth: 240,
      border: `2px solid ${selected ? c : '#d9d9d9'}`,
      borderRadius: 8, background: '#fff',
      boxShadow: selected ? '0 2px 8px rgba(0,0,0,0.12)' : '0 1px 3px rgba(0,0,0,0.06)',
    }}>
      {/* 左侧 Handle（出/入） */}
      <Handle type="source" position={Position.Left} id="left-source"
        style={{
          left: -5, width: 10, height: 10,
          borderRadius: '50%', background: selected ? c : '#d9d9d9',
          border: '2px solid #fff',
        }} />
      <Handle type="target" position={Position.Left} id="left-target"
        style={{
          left: -5, width: 10, height: 10,
          borderRadius: '50%', background: selected ? c : '#d9d9d9',
          border: '2px solid #fff',
        }} />

      {/* 右侧 Handle（出/入） */}
      <Handle type="source" position={Position.Right} id="right-source"
        style={{
          right: -5, width: 10, height: 10,
          borderRadius: '50%', background: selected ? c : '#d9d9d9',
          border: '2px solid #fff',
        }} />
      <Handle type="target" position={Position.Right} id="right-target"
        style={{
          right: -5, width: 10, height: 10,
          borderRadius: '50%', background: selected ? c : '#d9d9d9',
          border: '2px solid #fff',
        }} />

      {/* 头部 */}
      <div onClick={handleHeaderClick} style={{
        background: c, color: '#fff', padding: '4px 8px', borderRadius: '6px 6px 0 0',
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        fontSize: 12, fontWeight: 600, cursor: 'pointer',
      }}>
        <span>{BADGES[nodeData.kind]} {displayName}</span>
        {!isSystem && (
          <Space size={2}>
            <Button size="small" type="text" icon={<SettingOutlined />}
              style={{ color: '#fff', fontSize: 14, height: 20 }}
              onClick={(e) => { e.stopPropagation(); openConfig(nodeData); }} />
            <Button size="small" type="text" danger icon={<DeleteOutlined />}
              style={{ color: '#fff', fontSize: 14, height: 20 }}
              onClick={(e) => { e.stopPropagation(); deleteNode(id); }} />
          </Space>
        )}
      </div>

      {/* 字段列表 */}
      <div style={{ padding: '2px 0' }}>
        {fields.map(f => (
          <div key={f.key} onClick={() => handleFieldClick(f)} style={{
            display: 'flex', alignItems: 'center', padding: '2px 8px', cursor: 'pointer',
            borderTop: '1px solid #f5f5f5', fontSize: 11,
            background: f.locked ? BG_COLORS.system : '#fff',
          }}>
            <span style={{
              flex: 1, color: '#1a1a1a', overflow: 'hidden',
              textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>
              {f.locked && <LockOutlined style={{ fontSize: 9, color: '#999', marginRight: 3 }} />}
              {f.name}
            </span>
            <span style={{ color: '#888', fontSize: 10, marginLeft: 8 }}>{f.type}</span>
            {f.primaryKey && <KeyOutlined style={{ fontSize: 9, color: '#faad14', marginLeft: 4 }} />}
          </div>
        ))}
      </div>

      {/* 添加字段按钮（非系统实体） */}
      {!isSystem && (
        <Button type="dashed" size="small" block icon={<PlusOutlined />}
          style={{ margin: '2px 4px', width: 'calc(100% - 8px)', fontSize: 11, height: 24 }}
          onClick={handleAddField}>添加字段</Button>
      )}
    </div>
  );
};

export default EntityNodeComponent;