export type EntityCategory = 'BUSINESS' | 'API_REQUEST' | 'API_RESPONSE' | 'SYSTEM';
export type RelationType = 'FOREIGN_KEY' | 'INBOUND_MAPPING' | 'OUTBOUND_MAPPING';
export type MappingType = 'PATH' | 'EXPRESSION' | 'CONSTANT';

export interface FieldDefinition {
  name: string;
  type: string;
  primaryKey?: boolean;
  foreignKey?: { targetEntity: string; targetField: string };
  nullable?: boolean;
  description?: string;
}

export interface EntityNode {
  entityType: string;
  entityCategory: EntityCategory;
  description: string;
  color: string;
  fields: FieldDefinition[];
}

export interface MappingRule {
  key: string;
  source: string;
  target: string;
  type: MappingType;
  expression?: string;
}

export interface EdgeData {
  relationType: RelationType;
  mappingRules: MappingRule[];
}

export interface ModelingState {
  entities: EntityNode[];
  selectedNodeId: string | null;
  selectedEdgeId: string | null;
  mode: 'inbound' | 'outbound';
  channel: string;
  snapToGrid: boolean;
  showMiniMap: boolean;
  sidebarCollapsed: boolean;
  sidebarTab: 'entity' | 'relationship' | 'legend';
  filterCategory: EntityCategory | 'ALL';
  saving: boolean;

  setEntities: (e: EntityNode[]) => void;
  setSelectedNodeId: (id: string | null) => void;
  setSelectedEdgeId: (id: string | null) => void;
  setMode: (m: 'inbound' | 'outbound') => void;
  setChannel: (c: string) => void;
  toggleSnap: () => void;
  toggleMiniMap: () => void;
  toggleSidebar: () => void;
  setSidebarTab: (t: 'entity' | 'relationship' | 'legend') => void;
  setFilterCategory: (c: EntityCategory | 'ALL') => void;
  setSaving: (s: boolean) => void;
}