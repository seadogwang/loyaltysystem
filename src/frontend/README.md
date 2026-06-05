#!/usr/bin/env node

/**
 * Loyalty SaaS Admin — Schema-Driven UI 管理后台
 * 
 * 技术栈: React 18 + TypeScript + Formily.js v2 + Ant Design v5 + Monaco Editor
 * 
 * 三个核心模块:
 * 
 * 1. SchemaBuilder (低代码画布)
 *    - 拖拽式 JSON Schema 构建器
 *    - 自动生成 x-component/x-reactions/x-dependencies
 *    - 废弃字段标记 (只读态展示/编辑态隐藏)
 *    - 规则依赖检查
 * 
 * 2. DynamicRenderer (动态渲染引擎)
 *    - Formily SchemaField 动态解析
 *    - 只读/编辑双模式
 *    - 版本过期提示 + 升级引导
 *    - 历史废弃字段折叠面板
 * 
 * 3. ScriptingWorkbench (渠道脚本工作台)
 *    - Monaco Editor JavaScript 编辑器
 *    - 内置天猫/京东/抖音转换模板
 *    - 三栏布局: 输入 JSON | 代码编辑 | 沙箱结果
 *    - 50ms GraalVM 沙箱指示
 * 
 * 启动: cd src/frontend && npm install && npm run dev
 * 构建: cd src/frontend && npm run build
 *
 * DrawDB 自托管:
 *   cd drawdb && npm install && npm run dev    # DrawDB 启动在 localhost:5174
 *   cd src/frontend && npm run dev              # 前端启动在 localhost:5173
 *   Vite 自动将 /drawdb 代理到 DrawDB dev server
 */