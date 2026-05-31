// ---- Schema Field Types ----

/** JSON Schema 中单个字段的定义 */
export interface FieldSchema {
  title: string;
  type: 'string' | 'number' | 'boolean' | 'object' | 'array';
  required?: boolean;
  default?: unknown;
  /** Formily 组件标识 */
  'x-component'?: string;
  /** 组件属性 */
  'x-component-props'?: Record<string, unknown>;
  /** 联动表达式 */
  'x-reactions'?: string;
  /** 联动依赖字段 */
  'x-dependencies'?: string[];
  /** 是否已废弃 */
  deprecated?: boolean;
  /** 废弃时间 */
  deprecated_at?: string;
  /** 枚举选项 */
  enum?: { label: string; value: unknown }[];
}

/** 完整的 JSON Schema 定义 */
export interface JsonSchema {
  type: 'object';
  properties: Record<string, FieldSchema>;
  required?: string[];
}

/** 组件注册表项 */
export interface ComponentRegistryEntry {
  name: string;
  label: string;
  category: 'basic' | 'advanced' | 'custom';
  defaultSchema: FieldSchema;
}

// ---- Dynamic Renderer Types ----

export interface MemberData {
  member_id: number;
  program_code: string;
  tier_code?: string;
  status: string;
  ext_attributes: Record<string, unknown>;
  schema_version?: string;
}

export interface SchemaVersionInfo {
  version: string;
  schema_type: string;
  schema_json: JsonSchema;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  published_at?: string;
}

export type RenderMode = 'view' | 'edit';

// ---- Scripting Workbench Types ----

export interface TransformTestRequest {
  channel: string;
  js_code: string;
  source_json: string;
}

export interface TransformTestResult {
  success: boolean;
  result?: Record<string, unknown>;
  error?: string;
  execution_time_ms?: number;
}

// ---- API Response ----

export interface ApiResponse<T> {
  code: string;
  message: string;
  trace_id: string;
  data: T;
}