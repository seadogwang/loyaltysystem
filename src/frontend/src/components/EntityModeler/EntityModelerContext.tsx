import React, { createContext, useContext } from 'react';
import type { EntityNodeData, EntityFieldExt, EntityKind } from './types';

/**
 * EntityModeler 共享上下文
 * React Flow 自定义节点不能直接接收回调 props，通过 Context 共享回调函数
 */
export interface EntityModelerContextValue {
  selectedNodeId: string | null;
  selectNode: (id: string) => void;
  selectField: (field: EntityFieldExt) => void;
  deleteNode: (id: string) => void;
  openConfig: (entity: EntityNodeData) => void;
  addField: (nodeId: string, field: EntityFieldExt) => void;
}

const EntityModelerCtx = createContext<EntityModelerContextValue | null>(null);

export const EntityModelerProvider = EntityModelerCtx.Provider;

export function useEntityModeler(): EntityModelerContextValue {
  const ctx = useContext(EntityModelerCtx);
  if (!ctx) {
    throw new Error('useEntityModeler must be used within EntityModelerProvider');
  }
  return ctx;
}

export default EntityModelerCtx;