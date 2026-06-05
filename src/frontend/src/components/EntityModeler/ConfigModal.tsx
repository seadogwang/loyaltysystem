import React, { useState, useEffect } from 'react';
import { Modal, Input, Select, Typography } from 'antd';
import { SYSTEM_ENTITIES } from './constants';
import type { EntityNodeData, EntityKind } from './types';

const { Text } = Typography;

interface ConfigModalProps {
  open: boolean;
  entity: EntityNodeData | null;
  onClose: () => void;
  onSave: (e: EntityNodeData) => void;
}

/**
 * 实体配置弹窗：名称、映射配置（业务实体）
 */
const ConfigModal: React.FC<ConfigModalProps> = ({ open, entity, onClose, onSave }) => {
  const [draft, setDraft] = useState<EntityNodeData | null>(null);

  useEffect(() => {
    if (entity) setDraft({ ...entity, fields: [...entity.fields] });
  }, [entity]);

  if (!draft) return null;

  return (
    <Modal
      title={`配置: ${draft.displayName}`}
      open={open}
      onCancel={onClose}
      onOk={() => { onSave(draft); onClose(); }}
      okText="保存"
    >
      <div style={{ marginBottom: 10 }}>
        <Text type="secondary" style={{ fontSize: 11 }}>实体名称</Text>
        <Input
          size="small"
          value={draft.displayName}
          onChange={e => setDraft({ ...draft, displayName: e.target.value })}
        />
      </div>
      {draft.kind === 'business' && (
        <>
          <div style={{ marginBottom: 10 }}>
            <Text type="secondary" style={{ fontSize: 11 }}>映射到系统实体</Text>
            <Select
              size="small"
              value={draft.mapToEntity}
              style={{ width: '100%' }}
              onChange={v => setDraft({ ...draft, mapToEntity: v })}
              options={SYSTEM_ENTITIES.map(e => ({
                label: `${e.displayName} (${e.name})`,
                value: e.id,
              }))}
            />
          </div>
          <div style={{ marginBottom: 10 }}>
            <Text type="secondary" style={{ fontSize: 11 }}>映射到字段</Text>
            <Select
              size="small"
              value={draft.mapToField}
              style={{ width: '100%' }}
              onChange={v => setDraft({ ...draft, mapToField: v })}
              options={[
                { label: 'ext_attributes (Member)', value: 'ext_attributes' },
                { label: 'payload (TransactionEvent)', value: 'payload' },
                { label: 'config_json (Program)', value: 'config_json' },
              ]}
            />
          </div>
          <div style={{ marginBottom: 10 }}>
            <Text type="secondary" style={{ fontSize: 11 }}>存储Key</Text>
            <Input
              size="small"
              value={draft.storageKey}
              onChange={e => setDraft({ ...draft, storageKey: e.target.value })}
            />
          </div>
          <div style={{ marginBottom: 10 }}>
            <Text type="secondary" style={{ fontSize: 11 }}>存储方式</Text>
            <Select
              size="small"
              value={draft.storageType}
              style={{ width: '100%' }}
              onChange={v => setDraft({ ...draft, storageType: v as 'Object' | 'Array' })}
              options={[
                { label: 'Object', value: 'Object' },
                { label: 'Array', value: 'Array' },
              ]}
            />
          </div>
        </>
      )}
    </Modal>
  );
};

export default ConfigModal;