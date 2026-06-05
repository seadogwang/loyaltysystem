import React from 'react';
import { Tooltip } from 'antd';
import { TableOutlined, ApiOutlined, DeleteOutlined, ExpandOutlined, PlusOutlined } from '@ant-design/icons';
import type { EntityKind } from './types';

interface ToolbarProps {
  onAddEntity: (kind: EntityKind) => void;
  onDeleteSelected: () => void;
  onFitView: () => void;
  hasSelection: boolean;
}

const btnStyle: React.CSSProperties = {
  width: 32, height: 32, display: 'flex', alignItems: 'center', justifyContent: 'center',
  border: '1px solid #e0e0e0', borderRadius: 6, background: '#fff', cursor: 'pointer',
  fontFamily: 'inherit', fontSize: 14, color: '#666',
  transition: 'all 0.15s',
};

const Toolbar: React.FC<ToolbarProps> = ({ onAddEntity, onDeleteSelected, onFitView, hasSelection }) => (
  <div style={{
    position: 'absolute', top: 12, left: 12, zIndex: 10,
    display: 'flex', gap: 4, background: '#fff', padding: 4,
    borderRadius: 8, boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
    border: '1px solid #f0f0f0',
  }}>
    <Tooltip title="新建业务实体 (B)">
      <button style={btnStyle}
        onMouseEnter={e => { e.currentTarget.style.background = '#e6f7ff'; e.currentTarget.style.borderColor = '#1677ff'; }}
        onMouseLeave={e => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.borderColor = '#e0e0e0'; }}
        onClick={() => onAddEntity('business')}>📦</button>
    </Tooltip>
    <Tooltip title="新建 API 实体 (A)">
      <button style={btnStyle}
        onMouseEnter={e => { e.currentTarget.style.background = '#f6ffed'; e.currentTarget.style.borderColor = '#52c41a'; }}
        onMouseLeave={e => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.borderColor = '#e0e0e0'; }}
        onClick={() => onAddEntity('api')}>🔌</button>
    </Tooltip>
    <div style={{ width: 1, background: '#f0f0f0', margin: '0 4px' }} />
    <Tooltip title="删除选中 (Delete)">
      <button style={{ ...btnStyle, opacity: hasSelection ? 1 : 0.4, color: hasSelection ? '#ff4d4f' : '#ccc' }}
        onMouseEnter={e => { if (hasSelection) e.currentTarget.style.background = '#fff1f0'; }}
        onMouseLeave={e => { e.currentTarget.style.background = '#fff'; }}
        onClick={() => hasSelection && onDeleteSelected()}>🗑️</button>
    </Tooltip>
    <div style={{ width: 1, background: '#f0f0f0', margin: '0 4px' }} />
    <Tooltip title="适应画布 (F)">
      <button style={btnStyle}
        onMouseEnter={e => { e.currentTarget.style.background = '#fafafa'; e.currentTarget.style.borderColor = '#1a1a1a'; }}
        onMouseLeave={e => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.borderColor = '#e0e0e0'; }}
        onClick={onFitView}>🔍</button>
    </Tooltip>
  </div>
);

export default Toolbar;