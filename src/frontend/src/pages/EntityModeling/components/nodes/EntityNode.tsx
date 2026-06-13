import React, { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Tag } from 'antd';
import type { EntityNode as EntityNodeType } from '../types';

const COLORS: Record<string, { bg: string; border: string; tag: string }> = {
  BUSINESS:    { bg: '#eff6ff', border: '#3b82f6', tag: 'blue' },
  SYSTEM:      { bg: '#fff7ed', border: '#f97316', tag: 'orange' },
  API_REQUEST: { bg: '#f0fdf4', border: '#22c55e', tag: 'green' },
  API_RESPONSE:{ bg: '#faf5ff', border: '#a855f7', tag: 'purple' },
};

const CAT_LABELS: Record<string, string> = {
  BUSINESS: '业务', SYSTEM: '系统', API_REQUEST: 'API请求', API_RESPONSE: 'API响应',
};

const MAX_VISIBLE = 8;

const EntityNode: React.FC<NodeProps> = ({ data, selected }) => {
  const entity = data as unknown as EntityNodeType;
  const colors = COLORS[entity.entityCategory] || COLORS.BUSINESS;
  const fields = entity.fields || [];
  const expanded = fields.length <= MAX_VISIBLE;
  const visibleFields = expanded ? fields : fields.slice(0, MAX_VISIBLE);
  const hiddenCount = fields.length - MAX_VISIBLE;

  return (
    <div
      style={{
        background: '#fff',
        borderRadius: 8,
        minWidth: 200,
        maxWidth: 320,
        boxShadow: selected
          ? `0 0 0 2px #ec4899, 0 4px 16px rgba(0,0,0,0.1)`
          : '0 2px 8px rgba(0,0,0,0.08)',
        border: `1px solid ${selected ? '#ec4899' : '#e2e8f0'}`,
        transition: 'box-shadow 0.15s',
      }}
    >
      {/* Color stripe */}
      <div style={{ height: 3, background: entity.color, borderRadius: '8px 8px 0 0' }} />

      {/* Header */}
      <div style={{
        padding: '8px 12px 6px',
        background: colors.bg,
        borderRadius: '0 0 0 0',
        display: 'flex', alignItems: 'center', gap: 6,
      }}>
        <span style={{ fontWeight: 600, fontSize: 13, color: '#0f172a', fontFamily: 'monospace' }}>
          {entity.entityType}
        </span>
        <Tag color={colors.tag} style={{ fontSize: 10, margin: 0, lineHeight: '16px', padding: '0 4px' }}>
          {CAT_LABELS[entity.entityCategory] || entity.entityCategory}
        </Tag>
        <span style={{ fontSize: 10, color: '#94a3b8', marginLeft: 'auto' }}>{fields.length} fields</span>
      </div>

      {/* Field list */}
      <div style={{ padding: '2px 0' }}>
        {visibleFields.map((f, i) => {
          const yPos = 44 + i * 26;
          return (
            <div key={f.name} style={{
              position: 'relative',
              display: 'flex', alignItems: 'center',
              padding: '3px 12px', height: 26,
              borderTop: i > 0 ? '1px solid #f1f5f9' : 'none',
              fontSize: 12,
              background: 'transparent',
            }}>
              <Handle
                type="target"
                position={Position.Left}
                id={`${f.name}-target`}
                style={{
                  position: 'absolute',
                  left: -5, top: '50%',
                  transform: 'translateY(-50%)',
                  width: 8, height: 8,
                  background: f.primaryKey ? '#f59e0b' : '#94a3b8',
                  border: '2px solid #fff',
                  opacity: 0, transition: 'opacity 0.15s',
                }}
              />
              {/* PK icon */}
              {f.primaryKey && (
                <span style={{ fontSize: 10, color: '#f59e0b', marginRight: 4, fontWeight: 700 }}>PK</span>
              )}
              {/* Field name */}
              <span style={{
                flex: 1, fontFamily: 'monospace', fontSize: 11, color: '#1e293b',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {f.name}
              </span>
              {/* Type */}
              <span style={{
                fontSize: 10, color: '#94a3b8',
                background: '#f8fafc', borderRadius: 3, padding: '0 4px',
              }}>
                {f.type}
              </span>
              <Handle
                type="source"
                position={Position.Right}
                id={`${f.name}-source`}
                style={{
                  position: 'absolute',
                  right: -5, top: '50%',
                  transform: 'translateY(-50%)',
                  width: 8, height: 8,
                  background: '#3b82f6',
                  border: '2px solid #fff',
                  opacity: 0, transition: 'opacity 0.15s',
                }}
              />
            </div>
          );
        })}
      </div>

      {/* Show more */}
      {!expanded && (
        <div style={{
          padding: '4px 12px', textAlign: 'center', fontSize: 11, color: '#94a3b8',
          borderTop: '1px solid #f1f5f9', cursor: 'pointer',
        }}>
          + {hiddenCount} more fields
        </div>
      )}

      {/* Top/Bottom Handles for entity-level connections */}
      <Handle type="target" position={Position.Top} id="top-target"
        style={{ width: 0, height: 0, opacity: 0 }} />
      <Handle type="source" position={Position.Bottom} id="bottom-source"
        style={{ width: 0, height: 0, opacity: 0 }} />
    </div>
  );
};

export default memo(EntityNode);