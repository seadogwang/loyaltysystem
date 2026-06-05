import type { Node, Edge } from '@xyflow/react';

// ==================== 实体字段类型 ====================

export type FieldType = 'Long' | 'String' | 'Integer' | 'Boolean' | 'Double' | 'BigDecimal' |
  'Date' | 'DateTime' | 'JSONB' | 'Text' | 'Enum' | 'Object' | 'Array';

export interface EntityFieldExt {
  key: string;
  name: string;
  type: string;
  primaryKey?: boolean;
  required?: boolean;
  unique?: boolean;
  defaultValue?: string;
  locked?: boolean;
  xComponent?: string;
  xReactions?: string;
  xValidator?: string;
  deprecated?: boolean;
}

export interface EntityField {
  key: string;
  name: string;
  type: string;
  primaryKey?: boolean;
  required?: boolean;
  unique?: boolean;
  defaultValue?: string;
  description?: string;
  enumValues?: string[];
  locked?: boolean;
  xComponent?: string;
  xReactions?: string;
  deprecated?: boolean;
}

export type EntityKind = 'system' | 'business' | 'api';

export interface EntityNode {
  id: string;
  name: string;
  displayName: string;
  kind: EntityKind;
  fields: EntityFieldExt[];
  x: number;
  y: number;
  mapToEntity?: string;
  mapToField?: string;
  storageKey?: string;
  storageType?: 'Object' | 'Array';
}

export interface EntityRelation {
  id: string;
  from: string;
  to: string;
  fromField: string;
  toField: string;
  mappingField?: string;
  storageKey?: string;
  storageType?: string;
  type: string;
  label?: string;
  locked?: boolean;
}

export interface EntityRelationship {
  id: string;
  from: string;
  to: string;
  fromField: string;
  toField: string;
  type: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY';
  label?: string;
}

export interface EntityModel {
  id: string;
  name: string;
  displayName: string;
  fields: EntityField[];
  x: number;
  y: number;
}

export interface EntityTemplate {
  type: string;
  displayName: string;
  defaultFields: EntityField[];
}

// ==================== React Flow 类型 ====================

/** 存储在 React Flow 节点 data 中的实体数据 */
export interface EntityNodeData {
  [key: string]: unknown;
  id: string;
  name: string;
  displayName: string;
  kind: EntityKind;
  fields: EntityFieldExt[];
  mapToEntity?: string;
  mapToField?: string;
  storageKey?: string;
  storageType?: 'Object' | 'Array';
}

/** 存储在 React Flow 边 data 中的关系数据 */
export interface EntityEdgeData {
  [key: string]: unknown;
  fromField: string;
  toField: string;
  mappingField?: string;
  storageKey?: string;
  storageType?: string;
  type: string;
  label?: string;
  locked?: boolean;
}

/** React Flow 节点类型 */
export type EntityFlowNode = Node<EntityNodeData, 'entityNode'>;

/** React Flow 边类型 */
export type EntityFlowEdge = Edge<EntityEdgeData, 'entityEdge'>;