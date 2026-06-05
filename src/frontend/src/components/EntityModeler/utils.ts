import { MarkerType } from '@xyflow/react';
import type { EntityFlowNode, EntityFlowEdge, EntityNodeData, EntityEdgeData, EntityKind, EntityFieldExt } from './types';
import type { EntityNode, EntityRelation } from './types';

/**
 * 将旧版 EntityNode 转换为 React Flow EntityFlowNode
 */
export function entityToFlowNode(e: EntityNode): EntityFlowNode {
  return {
    id: e.id,
    type: 'entityNode',
    position: { x: e.x, y: e.y },
    data: {
      id: e.id,
      name: e.name,
      displayName: e.displayName,
      kind: e.kind,
      fields: e.fields.map(f => ({ ...f })),
      mapToEntity: e.mapToEntity,
      mapToField: e.mapToField,
      storageKey: e.storageKey,
      storageType: e.storageType,
    },
    draggable: true,
    deletable: true,
  };
}

/**
 * 将旧版 EntityRelation 转换为 React Flow EntityFlowEdge
 */
export function relationToFlowEdge(r: EntityRelation): EntityFlowEdge {
  return {
    id: r.id,
    source: r.from,
    target: r.to,
    type: 'entityEdge',
    data: {
      fromField: r.fromField,
      toField: r.toField,
      mappingField: r.mappingField,
      storageKey: r.storageKey,
      storageType: r.storageType,
      type: r.type,
      label: r.label,
      locked: r.locked,
    },
    deletable: true,
    style: {
      stroke: r.locked ? '#999' : '#1677ff',
      strokeWidth: 2,
      strokeDasharray: r.locked ? '4,2' : 'none',
    },
    markerEnd: r.locked
      ? undefined
      : { type: MarkerType.ArrowClosed, color: '#1677ff', width: 20, height: 20 },
  };
}

/**
 * 从 React Flow 节点/边导出 JSON Schema
 */
export function exportSchema(nodes: EntityFlowNode[], edges: EntityFlowEdge[]): string {
  return JSON.stringify({
    version: '1.0',
    entities: nodes
      .filter(n => n.data.kind !== 'system')
      .map(n => ({
        name: n.data.name,
        displayName: n.data.displayName,
        kind: n.data.kind,
        fields: n.data.fields.map(f => ({
          name: f.key,
          displayName: f.name,
          type: f.type,
          primaryKey: f.primaryKey,
          required: f.required,
          xComponent: f.xComponent,
          xReactions: f.xReactions,
          deprecated: f.deprecated,
        })),
        mapToEntity: n.data.mapToEntity,
        mapToField: n.data.mapToField,
        storageKey: n.data.storageKey,
        storageType: n.data.storageType,
      })),
    relationships: edges
      .filter(e => !e.data?.locked)
      .map(e => ({
        from: e.source,
        to: e.target,
        fromField: e.data?.fromField,
        toField: e.data?.toField,
        type: e.data?.type,
        label: e.data?.label,
      })),
  }, null, 2);
}

/**
 * 从 JSON 字符串导入实体和关系
 */
export function importFromJson(
  json: string,
  idCounter: { current: number },
): { nodes: EntityFlowNode[]; edges: EntityFlowEdge[] } | null {
  try {
    const data = JSON.parse(json);
    const nodes: EntityFlowNode[] = [];
    const edges: EntityFlowEdge[] = [];

    if (data.entities) {
      data.entities.forEach((e: any, i: number) => {
        const nodeId = `import_${i}_${Date.now()}`;
        nodes.push({
          id: nodeId,
          type: 'entityNode',
          position: { x: 800 + i * 250, y: 100 + (i % 3) * 280 },
          data: {
            id: nodeId,
            name: e.name || `entity_${i}`,
            displayName: e.displayName || '导入实体',
            kind: e.kind || 'business',
            fields: (e.fields || []).map((f: any, j: number) => ({
              key: f.name || `f_${j}`,
              name: f.displayName || f.name,
              type: f.type || 'String',
              primaryKey: f.primaryKey,
              required: f.required,
              xComponent: f.xComponent,
            })),
            mapToEntity: e.mapToEntity,
            mapToField: e.mapToField,
            storageKey: e.storageKey,
            storageType: e.storageType,
          },
        });
      });
    }

    if (data.relationships) {
      data.relationships.forEach((r: any, i: number) => {
        edges.push({
          id: `irel_${i}_${Date.now()}`,
          source: r.from,
          target: r.to,
          type: 'entityEdge',
          data: {
            fromField: r.fromField || '',
            toField: r.toField || '',
            type: r.type || '1:N',
            label: r.label || '',
            locked: false,
          },
          style: { stroke: '#1677ff', strokeWidth: 2 },
          markerEnd: { type: MarkerType.ArrowClosed, color: '#1677ff', width: 20, height: 20 },
        });
      });
    }

    return { nodes, edges };
  } catch {
    return null;
  }
}

/**
 * 获取实体节点
 */
export function getEntityById(id: string, nodes: EntityFlowNode[]): EntityFlowNode | undefined {
  return nodes.find(n => n.id === id);
}

/**
 * 创建新实体节点
 */
export function createNewEntityNode(
  kind: EntityKind,
  idCounter: { current: number },
): EntityFlowNode {
  const id = `e_${++idCounter.current}`;
  const names: Record<EntityKind, string> = { system: '新系统实体', business: '新业务实体', api: '新API实体' };
  const name = names[kind];
  return {
    id,
    type: 'entityNode',
    position: { x: 800 + Math.random() * 200, y: 100 + Math.random() * 300 },
    data: {
      id,
      name: id,
      displayName: name,
      kind,
      fields: [],
      mapToEntity: kind === 'business' ? 'sys_member' : undefined,
      mapToField: kind === 'business' ? 'ext_attributes' : undefined,
      storageKey: kind === 'business' ? 'new_entity' : undefined,
      storageType: kind === 'business' ? 'Object' : undefined,
    },
  };
}

/**
 * 创建新关系边
 */
export function createNewEdge(
  source: string,
  target: string,
  sourceNode: EntityFlowNode | undefined,
  targetNode: EntityFlowNode | undefined,
): EntityFlowEdge {
  return {
    id: `rel_${Date.now()}`,
    source,
    target,
    type: 'entityEdge',
    data: {
      fromField: sourceNode?.data.fields.find(f => f.type === 'JSONB')?.key
        || sourceNode?.data.fields[0]?.key || '',
      toField: targetNode?.data.fields.find(f => f.type === 'JSONB')?.key
        || targetNode?.data.fields[0]?.key || '',
      type: '1:N',
      label: '',
    },
    style: { stroke: '#1677ff', strokeWidth: 2 },
    markerEnd: { type: MarkerType.ArrowClosed, color: '#1677ff', width: 20, height: 20 },
  };
}