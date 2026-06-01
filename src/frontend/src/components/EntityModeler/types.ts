// 实体建模器 - 类型系统

export type FieldType = 'Long' | 'String' | 'Integer' | 'Boolean' | 'Double' | 'BigDecimal' |
  'Date' | 'DateTime' | 'JSONB' | 'Text' | 'Enum';

export interface EntityField {
  key: string;                    // 字段标识
  name: string;                   // 显示名
  type: FieldType;                // 数据类型
  primaryKey?: boolean;           // 是否主键
  required?: boolean;             // 是否必填
  unique?: boolean;               // 是否唯一
  defaultValue?: string;          // 默认值
  description?: string;           // 描述
  enumValues?: string[];          // Enum 类型的可选值
}

export interface EntityRelationship {
  id: string;
  from: string;                   // 源实体 ID
  to: string;                     // 目标实体 ID
  fromField: string;              // 外键字段（在 from 实体中）
  toField: string;                // 引用字段（在 to 实体中，通常是主键）
  type: 'ONE_TO_ONE' | 'ONE_TO_MANY' | 'MANY_TO_ONE' | 'MANY_TO_MANY';
  label?: string;
}

export interface EntityModel {
  id: string;                     // 唯一标识
  name: string;                   // 实体名（对应表名）
  displayName: string;            // 中文显示名
  fields: EntityField[];          // 字段列表
  x: number;                      // 画布位置 X
  y: number;                      // 画布位置 Y
}

export interface EntityTemplate {
  type: string;
  displayName: string;
  defaultFields: EntityField[];
}