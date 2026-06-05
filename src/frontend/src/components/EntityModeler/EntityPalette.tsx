import React from 'react';
import { Button, Divider, Typography } from 'antd';
import { DragOutlined, LockOutlined, UploadOutlined } from '@ant-design/icons';
import { SYSTEM_ENTITIES, COLORS } from './constants';
import type { EntityKind } from './types';

const { Text } = Typography;

interface EntityPaletteProps {
  onAddEntity: (kind: EntityKind) => void;
  onImport: (json: string) => void;
}

/**
 * 左侧实体面板：系统实体列表 + 点击添加业务/API 实体 + 导入 JSON
 */
const EntityPalette: React.FC<EntityPaletteProps> = ({ onAddEntity, onImport }) => {
  return (
    <div style={{
      width: 180, padding: 10, background: '#fff',
      borderRight: '1px solid #f0f0f0', flexShrink: 0,
    }}>
      <Text strong style={{ fontSize: 12, display: 'block', marginBottom: 8 }}>实体面板</Text>
      <Text type="secondary" style={{ fontSize: 10, display: 'block', marginBottom: 4 }}>
        系统实体（🔒锁定）
      </Text>
      {SYSTEM_ENTITIES.map(e => (
        <div key={e.id} style={{ padding: '2px 6px', fontSize: 11, color: '#8c8c8c' }}>
          <LockOutlined style={{ fontSize: 9, marginRight: 4 }} />{e.displayName}
        </div>
      ))}
      <Divider style={{ margin: '6px 0' }} />
      <Text type="secondary" style={{ fontSize: 10, display: 'block', marginBottom: 6 }}>
        点击添加实体
      </Text>

      {/* 业务实体按钮 */}
      <div
        onClick={() => onAddEntity('business')}
        style={{
          padding: '4px 8px', marginBottom: 4, borderRadius: 4,
          background: '#fff', cursor: 'pointer',
          border: `1px solid ${COLORS.business}`, fontSize: 12,
          display: 'flex', alignItems: 'center', gap: 6,
          transition: 'background 0.2s',
        }}
        onMouseEnter={e => (e.currentTarget.style.background = '#e6f7ff')}
        onMouseLeave={e => (e.currentTarget.style.background = '#fff')}
      >
        <DragOutlined style={{ fontSize: 12 }} />📦 业务实体
        <Text type="secondary" style={{ fontSize: 10, marginLeft: 'auto' }}>B</Text>
      </div>

      {/* API 实体按钮 */}
      <div
        onClick={() => onAddEntity('api')}
        style={{
          padding: '4px 8px', marginBottom: 4, borderRadius: 4,
          background: '#fff', cursor: 'pointer',
          border: `1px solid ${COLORS.api}`, fontSize: 12,
          display: 'flex', alignItems: 'center', gap: 6,
          transition: 'background 0.2s',
        }}
        onMouseEnter={e => (e.currentTarget.style.background = '#f6ffed')}
        onMouseLeave={e => (e.currentTarget.style.background = '#fff')}
      >
        <DragOutlined style={{ fontSize: 12 }} />🔌 API实体
        <Text type="secondary" style={{ fontSize: 10, marginLeft: 'auto' }}>A</Text>
      </div>

      {/* 导入 */}
      <div style={{ marginTop: 8 }}>
        <Button size="small" block icon={<UploadOutlined />}
          onClick={() => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.json';
            input.onchange = e => {
              const file = (e.target as HTMLInputElement).files?.[0];
              if (file) {
                const reader = new FileReader();
                reader.onload = () => onImport(reader.result as string);
                reader.readAsText(file);
              }
            };
            input.click();
          }}>导入 JSON</Button>
      </div>
    </div>
  );
};

export default EntityPalette;